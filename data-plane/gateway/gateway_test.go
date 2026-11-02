package gateway

import (
	"context"
	"crypto/rand"
	"errors"
	"io"
	"log/slog"
	"net"
	"strings"
	"sync"
	"testing"
	"time"

	"github.com/prometheus/client_golang/prometheus"
	"google.golang.org/grpc"
	"google.golang.org/grpc/codes"
	"google.golang.org/grpc/credentials/insecure"
	"google.golang.org/grpc/metadata"
	"google.golang.org/grpc/status"
	"google.golang.org/grpc/test/bufconn"
	"google.golang.org/protobuf/proto"
	corev1 "k8s.io/api/core/v1"
	metav1 "k8s.io/apimachinery/pkg/apis/meta/v1"
	"k8s.io/apimachinery/pkg/types"

	"github.com/jevzo/ogcloud/data-plane/pkg/events"
	eventsv1 "github.com/jevzo/ogcloud/data-plane/pkg/gen/proto/ogcloud/events/v1"
	internalv1 "github.com/jevzo/ogcloud/data-plane/pkg/gen/proto/ogcloud/internalapi/v1"
	nodev1 "github.com/jevzo/ogcloud/data-plane/pkg/gen/proto/ogcloud/node/v1"
	"github.com/jevzo/ogcloud/data-plane/pkg/ids"
	"github.com/jevzo/ogcloud/data-plane/pkg/kafkatopics"
	"github.com/jevzo/ogcloud/data-plane/pkg/kube"
	"github.com/jevzo/ogcloud/data-plane/pkg/podlabels"
	"github.com/jevzo/ogcloud/data-plane/pkg/valkeykeys"
)

const (
	namespace  = "ogcloud-servers"
	proxyToken = "proxy-token"
	lobbyToken = "lobby-token"

	patience        = 10 * time.Second
	deliveryTimeout = 30 * time.Second
)

type fakeReviewer map[string]kube.Identity

func (f fakeReviewer) Review(_ context.Context, token string) (kube.Identity, error) {
	identity, ok := f[token]
	if !ok {
		return kube.Identity{}, kube.ErrUnauthenticated
	}
	return identity, nil
}

type fakePods map[string]*corev1.Pod

func (f fakePods) Pod(name string) (*corev1.Pod, bool) {
	pod, ok := f[name]
	return pod, ok
}

type fakeState struct {
	servers  []*internalv1.ServerRecord
	sessions map[string]*internalv1.PlayerSession
	models   map[string][]byte
}

func (f *fakeState) Session(_ context.Context, playerUUID string) (*internalv1.PlayerSession, error) {
	return f.sessions[playerUUID], nil
}

func (f *fakeState) SessionByName(_ context.Context, name string) (*internalv1.PlayerSession, error) {
	for _, session := range f.sessions {
		if strings.EqualFold(session.GetPlayerName(), name) {
			return session, nil
		}
	}
	return nil, nil
}

func (f *fakeState) Server(_ context.Context, id string) (*internalv1.ServerRecord, error) {
	for _, server := range f.servers {
		if server.GetServerId() == id {
			return server, nil
		}
	}
	return nil, nil
}

func (f *fakeState) ServersOfGroup(_ context.Context, groupID string) ([]*internalv1.ServerRecord, error) {
	var servers []*internalv1.ServerRecord
	for _, server := range f.servers {
		if server.GetGroupId() == groupID {
			servers = append(servers, server)
		}
	}
	return servers, nil
}

func (f *fakeState) Servers(context.Context) ([]*internalv1.ServerRecord, error) {
	return f.servers, nil
}

func (f *fakeState) ReadModel(_ context.Context, kind valkeykeys.ReadModelKind, id string) ([]byte, error) {
	return f.models[string(kind)+":"+id], nil
}

type published struct {
	topic, key string
	event      proto.Message
}

type recorder struct {
	events chan published
}

func (r *recorder) Publish(_ context.Context, topic, key string, event proto.Message) error {
	r.events <- published{topic, key, event}
	return nil
}

func gamePod(name, serverID, role, groupType string) *corev1.Pod {
	profile := "paper-26.3"
	if role == "proxy" {
		profile = "velocity-4.3.0"
	}
	return &corev1.Pod{ObjectMeta: metav1.ObjectMeta{Name: name, Namespace: namespace, UID: types.UID(name + "-uid"), Labels: map[string]string{
		podlabels.ManagedBy: podlabels.ControllerName, podlabels.ServerId: serverID, podlabels.Group: "lobby",
		podlabels.GroupType: groupType, podlabels.Role: role, podlabels.Profile: profile,
	}}}
}

func marshal(t *testing.T, message proto.Message) []byte {
	t.Helper()
	encoded, err := proto.Marshal(message)
	if err != nil {
		t.Fatal(err)
	}
	return encoded
}

// fakeController records what the gateway asks the controller.
type fakeController struct {
	mu            sync.Mutex
	logins        []*internalv1.ResolveLoginRequest
	registrations []*internalv1.RegisterTemplateVersionRequest
	err           error
}

func (f *fakeController) ResolveLogin(_ context.Context, in *internalv1.ResolveLoginRequest, _ ...grpc.CallOption) (*internalv1.ResolveLoginResponse, error) {
	f.mu.Lock()
	defer f.mu.Unlock()
	f.logins = append(f.logins, in)
	return &internalv1.ResolveLoginResponse{Result: &nodev1.ResolveLoginResponse{Allowed: true}}, f.err
}

func (f *fakeController) RegisterTemplateVersion(_ context.Context, in *internalv1.RegisterTemplateVersionRequest,
	_ ...grpc.CallOption,
) (*internalv1.RegisterTemplateVersionResponse, error) {
	f.mu.Lock()
	defer f.mu.Unlock()
	f.registrations = append(f.registrations, in)
	return &internalv1.RegisterTemplateVersionResponse{TemplateVersionId: "v-1"}, f.err
}

// fakePresigner signs nothing; its URLs name the method and key.
type fakePresigner struct{}

func (fakePresigner) Get(_ context.Context, key string) (string, error) { return "get:" + key, nil }
func (fakePresigner) Put(_ context.Context, key string) (string, error) { return "put:" + key, nil }

type harness struct {
	client     nodev1.GatewayServiceClient
	agents     nodev1.TemplateAgentServiceClient
	controller *fakeController
	published  *recorder
	sessions   *Registry
	state      *fakeState
	metrics    *Metrics
	router     *Router
	now        *time.Time
}

func start(t *testing.T, helloTimeout time.Duration) *harness {
	t.Helper()
	pods := fakePods{
		"proxy-1": gamePod("proxy-1", "s-proxy", "proxy", "proxy"),
		"lobby-1": gamePod("lobby-1", "s-lobby", "backend", "dynamic"),
	}
	reviewer := fakeReviewer{
		proxyToken: {Namespace: namespace, ServiceAccount: "game", PodName: "proxy-1", PodUID: "proxy-1-uid"},
		lobbyToken: {Namespace: namespace, ServiceAccount: "game", PodName: "lobby-1", PodUID: "lobby-1-uid"},
	}
	state := &fakeState{
		servers:  []*internalv1.ServerRecord{{ServerId: "s-lobby", GroupId: "lobby", State: eventsv1.ServerState_SERVER_STATE_RUNNING, Port: 25565}},
		sessions: map[string]*internalv1.PlayerSession{},
		models: map[string][]byte{
			string(valkeykeys.ReadModelNetworkSettings) + ":" + valkeykeys.NetworkSettingsId: marshal(t, &eventsv1.NetworkSettingsUpdated{Motd: "hi"}),
			string(valkeykeys.ReadModelGroup) + ":lobby":                                     marshal(t, &eventsv1.GroupUpdated{GroupId: "lobby", Maintenance: true}),
		},
	}
	publisher := &recorder{events: make(chan published, 10)}
	now := time.Date(2026, 11, 2, 12, 0, 0, 0, time.UTC)
	clock := func() time.Time { return now }
	dependencies := testDependencies(state, publisher, clock)
	controller := &fakeController{}
	dependencies.Logins, dependencies.Templates, dependencies.Objects = controller, controller, fakePresigner{}
	service := NewService(dependencies, Options{HelloTimeout: helloTimeout, QueueSize: 8, DeliveryTimeout: deliveryTimeout})
	auth := NewAuthenticator(reviewer, pods, namespace)
	server := grpc.NewServer(grpc.UnaryInterceptor(auth.UnaryInterceptor()), grpc.StreamInterceptor(auth.StreamInterceptor()))
	nodev1.RegisterGatewayServiceServer(server, service)
	nodev1.RegisterTemplateAgentServiceServer(server, NewAgentService(dependencies))
	listener := bufconn.Listen(1 << 20)
	go func() { _ = server.Serve(listener) }()
	t.Cleanup(server.Stop)
	conn, err := grpc.NewClient("passthrough:///bufnet",
		grpc.WithContextDialer(func(ctx context.Context, _ string) (net.Conn, error) { return listener.DialContext(ctx) }),
		grpc.WithTransportCredentials(insecure.NewCredentials()))
	if err != nil {
		t.Fatalf("client: %v", err)
	}
	t.Cleanup(func() { _ = conn.Close() })
	return &harness{
		client: nodev1.NewGatewayServiceClient(conn), agents: nodev1.NewTemplateAgentServiceClient(conn), controller: controller,
		published: publisher, sessions: dependencies.Sessions, state: state,
		metrics: dependencies.Metrics, router: NewRouter(dependencies, deliveryTimeout), now: &now,
	}
}

// testDependencies wires fakes with a fresh metrics registry and a fixed clock.
func testDependencies(state State, publisher Publisher, clock func() time.Time) Dependencies {
	return Dependencies{
		Sessions: NewRegistry(), State: state, Events: publisher,
		Stamper: events.NewStamper(ids.New(clock, rand.Reader), clock, "gateway"), IDs: ids.New(clock, rand.Reader),
		Metrics: NewMetrics(prometheus.NewRegistry()), Logger: slog.New(slog.NewTextHandler(io.Discard, nil)), Now: clock,
	}
}

// as is a call context carrying token.
func as(t *testing.T, token string) context.Context {
	t.Helper()
	ctx, cancel := context.WithTimeout(metadata.AppendToOutgoingContext(context.Background(), "authorization", "Bearer "+token), patience)
	t.Cleanup(cancel)
	return ctx
}

func (h *harness) open(t *testing.T, token string, hello *nodev1.Hello) nodev1.GatewayService_SessionClient {
	t.Helper()
	ctx, cancel := context.WithTimeout(metadata.AppendToOutgoingContext(context.Background(), "authorization", "Bearer "+token), patience)
	t.Cleanup(cancel)
	stream, err := h.client.Session(ctx)
	if err != nil {
		t.Fatalf("Session: %v", err)
	}
	if hello != nil {
		if err := stream.Send(&nodev1.SessionRequest{Message: &nodev1.SessionRequest_Hello{Hello: hello}}); err != nil {
			t.Fatalf("send Hello: %v", err)
		}
	}
	return stream
}

func hello(nodeType eventsv1.NodeType) *nodev1.Hello {
	return &nodev1.Hello{ProtocolVersion: nodev1.ProtocolVersion_PROTOCOL_VERSION_1, NodeType: nodeType, IntegrationVersion: "2.0.0"}
}

func code(t *testing.T, stream nodev1.GatewayService_SessionClient) codes.Code {
	t.Helper()
	for {
		if _, err := stream.Recv(); err != nil {
			return status.Code(err)
		}
	}
}

func TestProxiesAreWelcomedWithTheNetworkAndEveryServer(t *testing.T) {
	stream := start(t, patience).open(t, proxyToken, hello(eventsv1.NodeType_NODE_TYPE_PROXY))

	response, err := stream.Recv()
	if err != nil {
		t.Fatalf("Recv: %v", err)
	}
	welcome := response.GetWelcome()
	if welcome.GetServerId() != "s-proxy" || welcome.GetGroupType() != eventsv1.GroupType_GROUP_TYPE_PROXY ||
		welcome.GetNetworkSettings().GetMotd() != "hi" {
		t.Errorf("welcome = %v", welcome)
	}
	if len(welcome.GetServers()) != 1 || !welcome.GetServers()[0].GetGroupMaintenance() || welcome.GetServers()[0].GetPort() != 25565 {
		t.Errorf("servers = %v", welcome.GetServers())
	}
}

func TestBackendsGetNoServerList(t *testing.T) {
	stream := start(t, patience).open(t, lobbyToken, hello(eventsv1.NodeType_NODE_TYPE_BACKEND))

	response, err := stream.Recv()
	if err != nil {
		t.Fatalf("Recv: %v", err)
	}
	if welcome := response.GetWelcome(); welcome.GetGroupType() != eventsv1.GroupType_GROUP_TYPE_DYNAMIC || len(welcome.GetServers()) != 0 {
		t.Errorf("welcome = %v", welcome)
	}
}

func TestBadOpeningsAreRefused(t *testing.T) {
	h := start(t, 100*time.Millisecond)
	tests := []struct {
		name  string
		token string
		hello *nodev1.Hello
		want  codes.Code
	}{
		{"no token", "", hello(eventsv1.NodeType_NODE_TYPE_BACKEND), codes.Unauthenticated},
		{"unknown token", "forged", hello(eventsv1.NodeType_NODE_TYPE_BACKEND), codes.Unauthenticated},
		{"backend posing as proxy", lobbyToken, hello(eventsv1.NodeType_NODE_TYPE_PROXY), codes.PermissionDenied},
		{"old protocol", lobbyToken, &nodev1.Hello{NodeType: eventsv1.NodeType_NODE_TYPE_BACKEND}, codes.FailedPrecondition},
		{"no Hello", lobbyToken, nil, codes.DeadlineExceeded},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			if got := code(t, h.open(t, tt.token, tt.hello)); got != tt.want {
				t.Errorf("got %v, want %v", got, tt.want)
			}
		})
	}
}

func TestHeartbeatsBecomeNodeHeartbeatEvents(t *testing.T) {
	h := start(t, patience)
	stream := h.open(t, lobbyToken, hello(eventsv1.NodeType_NODE_TYPE_BACKEND))
	if _, err := stream.Recv(); err != nil {
		t.Fatalf("Welcome: %v", err)
	}

	err := stream.Send(&nodev1.SessionRequest{Message: &nodev1.SessionRequest_Heartbeat{Heartbeat: &nodev1.Heartbeat{Players: 3, Tps: 19.9}}})
	if err != nil {
		t.Fatalf("send: %v", err)
	}

	select {
	case event := <-h.published.events:
		heartbeat, ok := event.event.(*eventsv1.NodeHeartbeat)
		if event.topic != kafkatopics.NodeHeartbeat || event.key != "s-lobby" || !ok ||
			heartbeat.GetPlayers() != 3 || heartbeat.GetNodeType() != eventsv1.NodeType_NODE_TYPE_BACKEND ||
			heartbeat.GetMetadata().GetProducer() != "gateway" {
			t.Errorf("published %+v", event)
		}
	case <-time.After(patience):
		t.Fatal("no heartbeat event")
	}
}

func TestAReconnectReplacesTheOldSession(t *testing.T) {
	h := start(t, patience)
	first := h.open(t, lobbyToken, hello(eventsv1.NodeType_NODE_TYPE_BACKEND))
	if _, err := first.Recv(); err != nil {
		t.Fatalf("Welcome: %v", err)
	}
	second := h.open(t, lobbyToken, hello(eventsv1.NodeType_NODE_TYPE_BACKEND))
	if _, err := second.Recv(); err != nil {
		t.Fatalf("Welcome: %v", err)
	}

	if got := code(t, first); got != codes.Aborted {
		t.Errorf("old session ended with %v", got)
	}
	if session, ok := h.sessions.Get("s-lobby", eventsv1.NodeType_NODE_TYPE_BACKEND); !ok || session == nil {
		t.Error("the new session is not registered")
	}
}

func TestASlowNodeIsDisconnectedRatherThanQueuedForever(t *testing.T) {
	var cause error
	session := newSession(Peer{ServerID: "s"}, hello(eventsv1.NodeType_NODE_TYPE_BACKEND), 1, func(err error) { cause = err })

	first, second := session.Send(&nodev1.SessionResponse{}), session.Send(&nodev1.SessionResponse{})

	if !first || second || !errors.Is(cause, ErrSlowNode) {
		t.Errorf("sent %v %v, cause %v", first, second, cause)
	}
}

func TestTokensOfDeletedOrForeignPodsAreRefused(t *testing.T) {
	pods := fakePods{"lobby-1": gamePod("lobby-1", "s-lobby", "backend", "dynamic")}
	unmanaged := gamePod("rogue", "s-x", "backend", "dynamic")
	unmanaged.Labels[podlabels.ManagedBy] = "someone"
	pods["rogue"] = unmanaged
	reviewer := fakeReviewer{
		"other-namespace": {Namespace: "ogcloud", PodName: "lobby-1", PodUID: "lobby-1-uid"},
		"replaced-pod":    {Namespace: namespace, PodName: "lobby-1", PodUID: "older-uid"},
		"unmanaged":       {Namespace: namespace, PodName: "rogue", PodUID: "rogue-uid"},
		"no-pod":          {Namespace: namespace},
	}
	auth := NewAuthenticator(reviewer, pods, namespace)
	for token := range reviewer {
		ctx := metadata.NewIncomingContext(context.Background(), metadata.Pairs("authorization", "Bearer "+token))
		if _, err := auth.Authenticate(ctx); status.Code(err) != codes.PermissionDenied {
			t.Errorf("%s: got %v", token, err)
		}
	}
}
