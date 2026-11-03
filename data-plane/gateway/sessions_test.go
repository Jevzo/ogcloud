package gateway

import (
	"bytes"
	"context"
	"testing"
	"time"

	"github.com/prometheus/client_golang/prometheus/testutil"
	"google.golang.org/grpc/codes"
	"google.golang.org/grpc/status"
	"google.golang.org/protobuf/proto"

	eventsv1 "github.com/jevzo/ogcloud/data-plane/pkg/gen/proto/ogcloud/events/v1"
	nodev1 "github.com/jevzo/ogcloud/data-plane/pkg/gen/proto/ogcloud/node/v1"
	"github.com/jevzo/ogcloud/data-plane/pkg/kafkatopics"
)

func TestEveryCallNeedsAPodToken(t *testing.T) {
	h := start(t, patience)
	calls := map[string]func(ctx context.Context) error{
		"GetServer": func(ctx context.Context) error {
			_, err := h.client.GetServer(ctx, &nodev1.GetServerRequest{ServerId: "s-lobby"})
			return err
		},
		"ListServers": func(ctx context.Context) error {
			_, err := h.client.ListServers(ctx, &nodev1.ListServersRequest{})
			return err
		},
		"FindPlayer": func(ctx context.Context) error {
			_, err := h.client.FindPlayer(ctx, &nodev1.FindPlayerRequest{Player: &nodev1.FindPlayerRequest_PlayerName{PlayerName: "x"}})
			return err
		},
		"GetPlayerProfile": func(ctx context.Context) error {
			_, err := h.client.GetPlayerProfile(ctx, &nodev1.GetPlayerProfileRequest{PlayerUuid: visitorUUID})
			return err
		},
		"GetPermissionGroup": func(ctx context.Context) error {
			_, err := h.client.GetPermissionGroup(ctx, &nodev1.GetPermissionGroupRequest{PermissionGroupId: "x"})
			return err
		},
		"ListNpcs": func(ctx context.Context) error {
			_, err := h.client.ListNpcs(ctx, &nodev1.ListNpcsRequest{GroupId: "x"})
			return err
		},
		"ResolveLogin": func(ctx context.Context) error {
			_, err := h.client.ResolveLogin(ctx, &nodev1.ResolveLoginRequest{})
			return err
		},
		"GetPodAssets": func(ctx context.Context) error {
			_, err := h.agents.GetPodAssets(ctx, &nodev1.GetPodAssetsRequest{})
			return err
		},
		"CreateTemplateUpload": func(ctx context.Context) error {
			_, err := h.agents.CreateTemplateUpload(ctx, &nodev1.CreateTemplateUploadRequest{})
			return err
		},
	}
	for name, call := range calls {
		for token, label := range map[string]string{"": "no token", "forged": "a forged token"} {
			ctx, cancel := context.WithTimeout(context.Background(), patience)
			if token != "" {
				ctx = as(t, token)
			}
			if err := call(ctx); status.Code(err) != codes.Unauthenticated {
				t.Errorf("%s with %s: %v", name, label, err)
			}
			cancel()
		}
	}
	if testutil.ToFloat64(h.metrics.authFailed.WithLabelValues(codes.Unauthenticated.String())) != float64(2*len(calls)) {
		t.Error("refused calls were not counted")
	}
}

func TestAReplacedSessionLeavesItsSuccessorsGameMetrics(t *testing.T) {
	h := start(t, patience)
	first := h.welcomed(t, lobbyToken, eventsv1.NodeType_NODE_TYPE_BACKEND)
	h.settle(t, first)

	second := h.welcomed(t, lobbyToken, eventsv1.NodeType_NODE_TYPE_BACKEND)
	if got := code(t, first); got != codes.Aborted {
		t.Fatalf("the replaced session ended with %v", got)
	}
	send(t, second, &nodev1.SessionRequest{Message: &nodev1.SessionRequest_Heartbeat{Heartbeat: &nodev1.Heartbeat{Players: 9}}})
	for event := range h.published.events {
		if event.topic == kafkatopics.NodeHeartbeat && event.event.(*eventsv1.NodeHeartbeat).GetPlayers() == 9 {
			break
		}
	}

	if players := testutil.ToFloat64(h.metrics.players.WithLabelValues("s-lobby", "lobby", "backend")); players != 9 {
		t.Errorf("players = %v; the replaced session took its successor's series", players)
	}
}

func TestANodeThatStopsReadingIsDisconnected(t *testing.T) {
	h := start(t, patience)
	reader := h.open(t, proxyToken, hello(eventsv1.NodeType_NODE_TYPE_PROXY))
	if _, err := reader.Recv(); err != nil {
		t.Fatal(err)
	}
	session, ok := h.sessions.Get("s-proxy", eventsv1.NodeType_NODE_TYPE_PROXY)
	if !ok {
		t.Fatal("no session")
	}
	big := &nodev1.SessionResponse{Message: &nodev1.SessionResponse_LiveMessage{LiveMessage: &nodev1.LiveChannelDelivery{
		Channel: "c", TypeId: "T", Payload: bytes.Repeat([]byte("x"), maxLivePayloadBytes),
	}}}
	if proto.Size(big) < maxLivePayloadBytes {
		t.Fatal("the message is too small to fill the window")
	}

	// The client never reads again: the HTTP/2 window fills, sends block, and the queue overflows. The
	// session must still end, which a send blocked inside the handler used to prevent.
	deadline := time.Now().Add(patience)
	for testutil.ToFloat64(h.metrics.ended.WithLabelValues(endSlow)) != 1 {
		if time.Now().After(deadline) {
			t.Fatal("a node that stopped reading kept its session")
		}
		session.Send(big)
		time.Sleep(10 * time.Millisecond)
	}

	if h.sessions.Holds(session) {
		t.Error("the ended session is still routed to")
	}
}
