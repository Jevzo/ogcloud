package loadbalancer

import (
	"bufio"
	"context"
	"io"
	"log/slog"
	"net"
	"net/netip"
	"strconv"
	"strings"
	"testing"
	"time"

	"github.com/pires/go-proxyproto"
	"github.com/prometheus/client_golang/prometheus"
	"github.com/prometheus/client_golang/prometheus/testutil"

	eventsv1 "github.com/jevzo/ogcloud/data-plane/pkg/gen/proto/ogcloud/events/v1"
	internalv1 "github.com/jevzo/ogcloud/data-plane/pkg/gen/proto/ogcloud/internalapi/v1"
)

const patience = 10 * time.Second

var testOptions = Options{HandshakeTimeout: patience, MaxPacketBytes: 4096, DialTimeout: patience, Limits: Limits{Open: 8, Rate: 10, Burst: 20}}

type lb struct {
	address string
	pool    *Pool
	metrics *Metrics
}

// startLB serves on a local port; trusted networks must send a PROXY header.
func startLB(t *testing.T, options Options, trusted ...netip.Prefix) *lb {
	t.Helper()
	ctx, cancel := context.WithCancel(context.Background())
	t.Cleanup(cancel)
	listener, err := (&net.ListenConfig{}).Listen(ctx, "tcp", "127.0.0.1:0")
	if err != nil {
		t.Fatal(err)
	}
	pool := NewPool()
	metrics := NewMetrics(prometheus.NewRegistry())
	server, err := NewServer(pool, options, metrics, slog.New(slog.NewTextHandler(io.Discard, nil)), time.Now)
	if err != nil {
		t.Fatal(err)
	}
	go func() {
		_ = server.Serve(ctx, &proxyproto.Listener{Listener: listener, ConnPolicy: ProxyPolicy(trusted), ReadHeaderTimeout: patience})
	}()
	return &lb{address: listener.Addr().String(), pool: pool, metrics: metrics}
}

type upstreamConn struct {
	header *proxyproto.Header
	reader *bufio.Reader
	conn   net.Conn
}

// fakeProxy accepts logins as Velocity would: a PROXY header first.
func fakeProxy(t *testing.T, l *lb) <-chan upstreamConn {
	t.Helper()
	listener, err := (&net.ListenConfig{}).Listen(context.Background(), "tcp", "127.0.0.1:0")
	if err != nil {
		t.Fatal(err)
	}
	t.Cleanup(func() { _ = listener.Close() })
	host, port, _ := net.SplitHostPort(listener.Addr().String())
	number, _ := strconv.Atoi(port)
	l.pool.Update(0, []*internalv1.ServerRecord{{
		ServerId: "p-1", GroupType: eventsv1.GroupType_GROUP_TYPE_PROXY, State: eventsv1.ServerState_SERVER_STATE_RUNNING,
		Host: host, Port: uint32(number), MaxPlayers: 100,
	}}, network)
	accepted := make(chan upstreamConn, 8)
	go func() {
		for {
			conn, err := listener.Accept()
			if err != nil {
				return
			}
			reader := bufio.NewReader(conn)
			header, err := proxyproto.Read(reader)
			if err != nil {
				_ = conn.Close()
				continue
			}
			accepted <- upstreamConn{header: header, reader: reader, conn: conn}
		}
	}()
	return accepted
}

func dial(t *testing.T, address string) net.Conn {
	t.Helper()
	conn, err := (&net.Dialer{}).DialContext(context.Background(), "tcp", address)
	if err != nil {
		t.Fatal(err)
	}
	t.Cleanup(func() { _ = conn.Close() })
	if err := conn.SetDeadline(time.Now().Add(patience)); err != nil {
		t.Fatal(err)
	}
	return conn
}

func loginStart() []byte { return frame(appendString(appendVarInt(nil, 0x00), "Jevzo")) }

func TestLoginsReachAProxyWithTheClientAddressAndTheirOwnBytes(t *testing.T) {
	l := startLB(t, testOptions)
	upstream := fakeProxy(t, l)
	client := dial(t, l.address)
	sent := append(handshakePacket(777, "play.ogwars.io", 25565, nextLogin), loginStart()...)

	if _, err := client.Write(sent); err != nil {
		t.Fatal(err)
	}
	proxy := <-upstream
	defer func() { _ = proxy.conn.Close() }()
	received := make([]byte, len(sent))
	if _, err := io.ReadFull(proxy.reader, received); err != nil || string(received) != string(sent) {
		t.Fatalf("the proxy got %x, %v", received, err)
	}
	if source := proxy.header.SourceAddr.(*net.TCPAddr); source.String() != client.LocalAddr().String() || proxy.header.Version != proxyHeaderV2 {
		t.Errorf("PROXY header %v, client %v", proxy.header, client.LocalAddr())
	}
	if _, err := proxy.conn.Write([]byte("welcome")); err != nil {
		t.Fatal(err)
	}
	reply := make([]byte, len("welcome"))
	if _, err := io.ReadFull(client, reply); err != nil || string(reply) != "welcome" {
		t.Errorf("the client got %q, %v", reply, err)
	}
	if testutil.ToFloat64(l.metrics.connections.WithLabelValues(outcomeLogin)) != 1 {
		t.Error("the login was not counted")
	}
}

func TestStatusPingsAreAnsweredWithoutAProxy(t *testing.T) {
	l := startLB(t, testOptions)
	l.pool.Update(0, nil, network)
	client := dial(t, l.address)
	ping := append(appendVarInt(nil, packetPing), 1, 2, 3, 4, 5, 6, 7, 8)

	if _, err := client.Write(append(append(handshakePacket(47, "x", 1, nextStatus), frame(appendVarInt(nil, packetStatusRequest))...), frame(ping)...)); err != nil {
		t.Fatal(err)
	}

	reader := bufio.NewReader(client)
	_, body, _, err := readPacket(reader, 4096)
	if err != nil {
		t.Fatal(err)
	}
	if response := decodeStatus(t, body); response.Description.Text != "§aWelcome" || response.Version.Protocol != 47 {
		t.Errorf("status %+v", response)
	}
	if _, pong, _, err := readPacket(reader, 64); err != nil || len(pong) != 8 {
		t.Errorf("pong %x, %v", pong, err)
	}
}

func TestLoginsThatCannotBeServedAreRefusedWithAReason(t *testing.T) {
	tests := map[string]struct {
		protocol int32
		want     string
	}{
		"old client": {5, "Unsupported Minecraft version"},
		"no proxy":   {777, "No proxy is available"},
	}
	for name, tt := range tests {
		t.Run(name, func(t *testing.T) {
			l := startLB(t, testOptions)
			client := dial(t, l.address)

			if _, err := client.Write(append(handshakePacket(tt.protocol, "x", 1, nextLogin), loginStart()...)); err != nil {
				t.Fatal(err)
			}

			id, body, _, err := readPacket(bufio.NewReader(client), 4096)
			if err != nil || id != packetLoginDisconnect || !strings.Contains(string(body), tt.want) {
				t.Errorf("got %d %q %v", id, body, err)
			}
		})
	}
}

func TestProxyHeadersAreTakenOnlyFromTrustedNetworks(t *testing.T) {
	spoofed := &proxyproto.Header{Version: 2, Command: proxyproto.PROXY, TransportProtocol: proxyproto.TCPv4,
		SourceAddr: &net.TCPAddr{IP: net.ParseIP("198.51.100.9"), Port: 4000}, DestinationAddr: &net.TCPAddr{IP: net.ParseIP("10.0.0.1"), Port: 25565}}
	login := append(handshakePacket(777, "x", 1, nextLogin), loginStart()...)

	trusted := startLB(t, testOptions, netip.MustParsePrefix("127.0.0.0/8"))
	upstream := fakeProxy(t, trusted)
	client := dial(t, trusted.address)
	if _, err := spoofed.WriteTo(client); err != nil {
		t.Fatal(err)
	}
	if _, err := client.Write(login); err != nil {
		t.Fatal(err)
	}
	proxy := <-upstream
	_ = proxy.conn.Close()
	if source := proxy.header.SourceAddr.(*net.TCPAddr); !source.IP.Equal(net.ParseIP("198.51.100.9")) {
		t.Errorf("a trusted header's client was not used: %v", source)
	}

	untrusted := startLB(t, testOptions)
	direct := dial(t, untrusted.address)
	if _, err := spoofed.WriteTo(direct); err != nil {
		t.Fatal(err)
	}
	if _, err := direct.Write(login); err != nil {
		t.Fatal(err)
	}
	if _, err := direct.Read(make([]byte, 1)); err == nil {
		t.Error("an untrusted PROXY header was accepted")
	}
}

func TestEachAddressMayHoldOnlySoManyConnections(t *testing.T) {
	l := startLB(t, Options{HandshakeTimeout: patience, MaxPacketBytes: 4096, DialTimeout: patience, Limits: Limits{Open: 1, Rate: 100, Burst: 100}})
	upstream := fakeProxy(t, l)
	held := dial(t, l.address)
	if _, err := held.Write(append(handshakePacket(777, "x", 1, nextLogin), loginStart()...)); err != nil {
		t.Fatal(err)
	}
	// Once the proxy has the login, the first connection holds the address's only slot.
	proxy := <-upstream
	defer func() { _ = proxy.conn.Close() }()

	refused := dial(t, l.address)

	if _, err := refused.Read(make([]byte, 1)); err == nil {
		t.Error("a second connection from the same address was served")
	}
	if testutil.ToFloat64(l.metrics.connections.WithLabelValues(outcomeLimited)) != 1 {
		t.Error("the refusal was not counted")
	}
}
