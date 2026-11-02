package loadbalancer

import (
	"bufio"
	"context"
	"errors"
	"fmt"
	"io"
	"log/slog"
	"net"
	"net/netip"
	"time"

	"github.com/pires/go-proxyproto"
)

const (
	// Accept errors such as running out of file descriptors are retried after a pause.
	acceptBackoff = 50 * time.Millisecond
	sweepInterval = 10 * time.Second
	proxyHeaderV2 = 2
)

// Options tune connections (D-0118).
type Options struct {
	HandshakeTimeout time.Duration
	MaxPacketBytes   int
	DialTimeout      time.Duration
	Limits           Limits
}

// Server accepts Minecraft clients.
type Server struct {
	pool    *Pool
	limiter *limiter
	options Options
	metrics *Metrics
	logger  *slog.Logger
	now     func() time.Time
	dialer  net.Dialer
}

// NewServer routes logins to proxies in pool.
func NewServer(pool *Pool, options Options, metrics *Metrics, logger *slog.Logger, now func() time.Time) *Server {
	return &Server{pool: pool, limiter: newLimiter(options.Limits), options: options, metrics: metrics, logger: logger, now: now}
}

// ProxyPolicy requires a PROXY header from trusted networks, the cloud load balancer (D-0118). Other
// connections are taken as they are: no header is looked for, so their address is known at once for the
// per-IP limits, and a header they send fails the handshake like any other garbage.
func ProxyPolicy(trusted []netip.Prefix) proxyproto.ConnPolicyFunc {
	return func(options proxyproto.ConnPolicyOptions) (proxyproto.Policy, error) {
		addr := addrOf(options.Upstream)
		for _, prefix := range trusted {
			if prefix.Contains(addr) {
				return proxyproto.REQUIRE, nil
			}
		}
		return proxyproto.SKIP, nil
	}
}

// Serve accepts connections until ctx ends.
func (s *Server) Serve(ctx context.Context, listener net.Listener) error {
	go func() {
		<-ctx.Done()
		_ = listener.Close()
	}()
	go s.sweep(ctx)
	for {
		conn, err := listener.Accept()
		if ctx.Err() != nil {
			return nil
		}
		if err != nil {
			if errors.Is(err, net.ErrClosed) {
				return fmt.Errorf("accept: %w", err)
			}
			s.logger.Warn("accept failed", "error", err)
			time.Sleep(acceptBackoff)
			continue
		}
		go s.handle(ctx, conn)
	}
}

func (s *Server) sweep(ctx context.Context) {
	ticker := time.NewTicker(sweepInterval)
	defer ticker.Stop()
	for {
		select {
		case <-ctx.Done():
			return
		case <-ticker.C:
			s.limiter.sweep(s.now())
		}
	}
}

// handle answers a status ping or hands a login to a proxy.
func (s *Server) handle(ctx context.Context, conn net.Conn) {
	defer func() { _ = conn.Close() }()
	if err := conn.SetDeadline(s.now().Add(s.options.HandshakeTimeout)); err != nil {
		return
	}
	// With a PROXY header this is the client's address, read from the header.
	release, ok := s.limiter.acquire(addrOf(conn.RemoteAddr()), s.now())
	if !ok {
		s.metrics.outcome(outcomeLimited)
		return
	}
	defer release()
	reader := bufio.NewReader(conn)
	if first, err := reader.Peek(1); err != nil || first[0] == legacyPing {
		s.metrics.outcome(outcomeMalformed)
		return
	}
	id, payload, raw, err := readPacket(reader, s.options.MaxPacketBytes)
	var handshake Handshake
	if err == nil && id == packetHandshake {
		handshake, err = parseHandshake(payload)
	}
	if err != nil || id != packetHandshake {
		s.metrics.outcome(outcomeMalformed)
		return
	}
	switch handshake.NextState {
	case nextStatus:
		network, online := s.pool.Status()
		if err := answerStatus(reader, conn, status(network, online, handshake.Protocol)); err != nil {
			s.metrics.outcome(outcomeMalformed)
			return
		}
		s.metrics.outcome(outcomeStatus)
	case nextLogin, nextTransfer:
		s.login(ctx, conn, reader, handshake, raw)
	default:
		s.metrics.outcome(outcomeMalformed)
	}
}

// login connects the client to a proxy: a PROXY v2 header with the client's address, the handshake as
// sent, then both directions as they come.
func (s *Server) login(ctx context.Context, conn net.Conn, reader *bufio.Reader, handshake Handshake, raw []byte) {
	if !supported(handshake.Protocol) {
		s.refuse(conn, outcomeUnsupported, unsupportedVersionMessage)
		return
	}
	proxyID, address, err := s.pool.Select()
	if err != nil {
		s.refuse(conn, outcomeNoProxy, noProxyMessage)
		return
	}
	dialCtx, cancel := context.WithTimeout(ctx, s.options.DialTimeout)
	upstream, err := s.dialer.DialContext(dialCtx, "tcp", address)
	cancel()
	if err != nil {
		s.logger.Warn("proxy unreachable", "proxyId", proxyID, "error", err)
		s.refuse(conn, outcomeDialFailed, noProxyMessage)
		return
	}
	defer func() { _ = upstream.Close() }()
	header := proxyproto.HeaderProxyFromAddrs(proxyHeaderV2, conn.RemoteAddr(), conn.LocalAddr())
	if _, err := header.WriteTo(upstream); err != nil {
		s.refuse(conn, outcomeDialFailed, noProxyMessage)
		return
	}
	if _, err := upstream.Write(raw); err != nil {
		s.refuse(conn, outcomeDialFailed, noProxyMessage)
		return
	}
	if err := conn.SetDeadline(time.Time{}); err != nil {
		return
	}
	s.metrics.outcome(outcomeLogin)
	s.metrics.active.Inc()
	defer s.metrics.active.Dec()
	pipe(conn, reader, upstream)
}

func (s *Server) refuse(conn net.Conn, outcome, message string) {
	s.metrics.outcome(outcome)
	if err := refuseLogin(conn, message); err != nil {
		s.logger.Debug("refusal not sent", "outcome", outcome, "error", err)
	}
}

// pipe copies both ways until either side closes, then closes both.
func pipe(client net.Conn, fromClient io.Reader, upstream net.Conn) {
	done := make(chan struct{}, 2)
	go func() {
		_, _ = io.Copy(upstream, fromClient)
		done <- struct{}{}
	}()
	go func() {
		_, _ = io.Copy(client, upstream)
		done <- struct{}{}
	}()
	<-done
	_ = client.Close()
	_ = upstream.Close()
	<-done
}

// addrOf returns the IP of a TCP address; anything else shares the zero address.
func addrOf(addr net.Addr) netip.Addr {
	if tcp, ok := addr.(*net.TCPAddr); ok {
		return tcp.AddrPort().Addr().Unmap()
	}
	return netip.Addr{}
}
