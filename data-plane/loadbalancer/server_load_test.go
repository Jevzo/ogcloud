package loadbalancer

import (
	"bufio"
	"context"
	"fmt"
	"io"
	"net"
	"net/netip"
	"os"
	"strconv"
	"sync"
	"sync/atomic"
	"testing"
	"time"

	"github.com/pires/go-proxyproto"

	eventsv1 "github.com/jevzo/ogcloud/data-plane/pkg/gen/proto/ogcloud/events/v1"
	internalv1 "github.com/jevzo/ogcloud/data-plane/pkg/gen/proto/ogcloud/internalapi/v1"
)

const (
	loadRate    = 500
	loadSeconds = 10
	// How far behind the offered pace the last login may finish.
	loadSlack = time.Second
	// What the fake proxy answers once a login reached it.
	proxyReply = 'k'
)

// The load balancer takes at least 500 new logins a second, each from its own client behind the cloud load balancer,
// so the per-IP limits never apply (D-0053, D-0175). It measures real time on this machine, so `mise run test:load`
// runs it and `mise run check` skips it.
func TestTakesFiveHundredNewLoginsASecond(t *testing.T) {
	if os.Getenv("OGCLOUD_LOAD") != "true" {
		t.Skip("a load test: mise run test:load runs it")
	}
	l := startLB(t, testOptions, netip.MustParsePrefix("127.0.0.0/8"))
	login := append(handshakePacket(777, "play.ogwars.io", 25565, nextLogin), loginStart()...)
	reached := countingProxy(t, l, len(login))

	var failures atomic.Int64
	var firstFailure atomic.Value
	var clients sync.WaitGroup
	ticker := time.NewTicker(time.Second / loadRate)
	defer ticker.Stop()
	started := time.Now()
	for index := range loadRate * loadSeconds {
		<-ticker.C
		clients.Go(func() {
			if err := loginFrom(l.address, clientAddress(index), login); err != nil {
				failures.Add(1)
				firstFailure.CompareAndSwap(nil, err)
			}
		})
	}
	clients.Wait()
	elapsed := time.Since(started)

	t.Logf("%d of %d logins reached the proxy in %s", reached.Load(), loadRate*loadSeconds, elapsed)
	if failures.Load() > 0 || reached.Load() != loadRate*loadSeconds {
		t.Errorf("%d logins failed, the first with %v", failures.Load(), firstFailure.Load())
	}
	if elapsed > loadSeconds*time.Second+loadSlack {
		t.Errorf("%d logins took %s, so the load balancer did not keep up with %d a second", loadRate*loadSeconds, elapsed, loadRate)
	}
}

// countingProxy reads every login of loginBytes that arrives with its PROXY header, answers it and counts it; closing
// with bytes unread would reset the connection and lose the answer. It reports no capacity, so the logins the pool
// sends it never make it full.
func countingProxy(t *testing.T, l *lb, loginBytes int) *atomic.Int64 {
	t.Helper()
	listener, err := (&net.ListenConfig{}).Listen(context.Background(), "tcp", "127.0.0.1:0")
	if err != nil {
		t.Fatal(err)
	}
	t.Cleanup(func() { _ = listener.Close() })
	host, port, _ := net.SplitHostPort(listener.Addr().String())
	number, _ := strconv.Atoi(port)
	l.pool.Update(0, []*internalv1.ServerRecord{{
		ServerId: "p-load", GroupType: eventsv1.GroupType_GROUP_TYPE_PROXY, State: eventsv1.ServerState_SERVER_STATE_RUNNING,
		Host: host, Port: uint32(number),
	}}, network)
	var reached atomic.Int64
	go func() {
		for {
			conn, err := listener.Accept()
			if err != nil {
				return
			}
			go func() {
				defer func() { _ = conn.Close() }()
				reader := bufio.NewReader(conn)
				if _, err := proxyproto.Read(reader); err != nil {
					return
				}
				if _, err := io.ReadFull(reader, make([]byte, loginBytes)); err != nil {
					return
				}
				reached.Add(1)
				_, _ = conn.Write([]byte{proxyReply})
			}()
		}
	}()
	return &reached
}

// clientAddress is a distinct address in 10.0.0.0/8 for each index.
func clientAddress(index int) net.IP {
	return net.IPv4(10, byte(index>>16), byte(index>>8), byte(index))
}

// loginFrom sends a login as the cloud load balancer forwards it for client, and waits for the proxy's answer.
func loginFrom(address string, client net.IP, login []byte) error {
	conn, err := (&net.Dialer{Timeout: patience}).Dial("tcp", address)
	if err != nil {
		return fmt.Errorf("dial: %w", err)
	}
	defer func() { _ = conn.Close() }()
	if err := conn.SetDeadline(time.Now().Add(patience)); err != nil {
		return err
	}
	header := &proxyproto.Header{Version: proxyHeaderV2, Command: proxyproto.PROXY, TransportProtocol: proxyproto.TCPv4,
		SourceAddr: &net.TCPAddr{IP: client, Port: 40000}, DestinationAddr: &net.TCPAddr{IP: net.IPv4(10, 0, 0, 1), Port: 25565}}
	if _, err := header.WriteTo(conn); err != nil {
		return fmt.Errorf("PROXY header: %w", err)
	}
	if _, err := conn.Write(login); err != nil {
		return fmt.Errorf("login: %w", err)
	}
	reply := make([]byte, 1)
	if _, err := io.ReadFull(conn, reply); err != nil || reply[0] != proxyReply {
		return fmt.Errorf("the proxy's answer %q: %w", reply, err)
	}
	return nil
}
