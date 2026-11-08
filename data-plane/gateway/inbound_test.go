package gateway

import (
	"errors"
	"fmt"
	"io"
	"strings"
	"testing"
	"time"

	"github.com/prometheus/client_golang/prometheus/testutil"

	eventsv1 "github.com/jevzo/ogcloud/data-plane/pkg/gen/proto/ogcloud/events/v1"
	internalv1 "github.com/jevzo/ogcloud/data-plane/pkg/gen/proto/ogcloud/internalapi/v1"
	nodev1 "github.com/jevzo/ogcloud/data-plane/pkg/gen/proto/ogcloud/node/v1"
	"github.com/jevzo/ogcloud/data-plane/pkg/kafkakeys"
	"github.com/jevzo/ogcloud/data-plane/pkg/kafkatopics"
	"github.com/jevzo/ogcloud/data-plane/pkg/permissionnodes"
	"github.com/jevzo/ogcloud/data-plane/pkg/valkeykeys"
)

const (
	adminUUID     = "0190f3c4-6b2a-7c3e-9f00-00000000000a"
	visitorUUID   = "0190f3c4-6b2a-7c3e-9f00-00000000000b"
	elsewhereUUID = "0190f3c4-6b2a-7c3e-9f00-00000000000c"
)

// welcomed opens a session and reads its Welcome.
func (h *harness) welcomed(t *testing.T, token string, nodeType eventsv1.NodeType) nodev1.GatewayService_SessionClient {
	t.Helper()
	stream := h.open(t, token, hello(nodeType))
	if _, err := stream.Recv(); err != nil {
		t.Fatalf("Welcome: %v", err)
	}
	return stream
}

func send(t *testing.T, stream nodev1.GatewayService_SessionClient, request *nodev1.SessionRequest) {
	t.Helper()
	if err := stream.Send(request); err != nil {
		t.Fatalf("send: %v", err)
	}
}

// settle sends a heartbeat and waits for it: the gateway handles a session's messages in order, so
// everything sent before it has been handled. It returns what was published before the heartbeat.
func (h *harness) settle(t *testing.T, stream nodev1.GatewayService_SessionClient) []published {
	t.Helper()
	send(t, stream, &nodev1.SessionRequest{Message: &nodev1.SessionRequest_Heartbeat{Heartbeat: &nodev1.Heartbeat{}}})
	var before []published
	for {
		select {
		case event := <-h.published.events:
			if event.topic == kafkatopics.NodeHeartbeat {
				return before
			}
			before = append(before, event)
		case <-time.After(patience):
			t.Fatal("the heartbeat never arrived")
		}
	}
}

func TestNodesMaySendOnlyWhatTheirNodeTypeAllows(t *testing.T) {
	h := start(t, patience)
	backend := h.welcomed(t, lobbyToken, eventsv1.NodeType_NODE_TYPE_BACKEND)
	proxy := h.welcomed(t, proxyToken, eventsv1.NodeType_NODE_TYPE_PROXY)

	send(t, backend, &nodev1.SessionRequest{Message: &nodev1.SessionRequest_PlayerActivity{PlayerActivity: &nodev1.PlayerActivityReport{
		PlayerUuid: visitorUUID, Activity: &nodev1.PlayerActivityReport_Disconnected{Disconnected: &eventsv1.PlayerDisconnected{}},
	}}})
	send(t, backend, &nodev1.SessionRequest{Message: &nodev1.SessionRequest_NodeCommand{NodeCommand: &nodev1.NodeCommandRequest{}}})
	send(t, backend, &nodev1.SessionRequest{Message: &nodev1.SessionRequest_AdminRequest{AdminRequest: &nodev1.AdminRequest{
		ActingPlayerUuid: adminUUID, Change: &nodev1.AdminRequest_NetworkMaintenance{NetworkMaintenance: &nodev1.NetworkMaintenanceChange{Enabled: true}},
	}}})
	send(t, proxy, &nodev1.SessionRequest{Message: &nodev1.SessionRequest_GameState{GameState: &nodev1.GameStateUpdate{}}})
	send(t, proxy, &nodev1.SessionRequest{Message: &nodev1.SessionRequest_NpcChange{NpcChange: &nodev1.NpcChangeRequest{
		ActingPlayerUuid: adminUUID, Change: &nodev1.NpcChangeRequest_Create{Create: &nodev1.Npc{GroupId: "lobby"}},
	}}})

	if published := append(h.settle(t, backend), h.settle(t, proxy)...); len(published) != 0 {
		t.Errorf("published %v", published)
	}
	for _, message := range []string{messagePlayerActivity, messageNodeCommand, messageGameState, messageAdminRequest, messageNpcChange} {
		if testutil.ToFloat64(h.metrics.rejected.WithLabelValues(message, rejectNodeType)) != 1 {
			t.Errorf("%s was not rejected", message)
		}
	}
}

func TestPlayerActivityNamesTheReportingProxy(t *testing.T) {
	h := start(t, patience)
	proxy := h.welcomed(t, proxyToken, eventsv1.NodeType_NODE_TYPE_PROXY)

	send(t, proxy, &nodev1.SessionRequest{Message: &nodev1.SessionRequest_PlayerActivity{PlayerActivity: &nodev1.PlayerActivityReport{
		PlayerUuid: visitorUUID, Activity: &nodev1.PlayerActivityReport_Connected{Connected: &eventsv1.PlayerConnected{PlayerName: "Jevzo", ProxyId: "forged"}},
	}}})

	published := h.settle(t, proxy)
	if len(published) != 1 {
		t.Fatalf("published %v", published)
	}
	activity, ok := published[0].event.(*eventsv1.PlayerActivity)
	if !ok || published[0].key != visitorUUID || activity.GetConnected().GetProxyId() != "s-proxy" || activity.GetMetadata().GetEventId() == "" {
		t.Errorf("published %+v", published[0])
	}
}

func TestConsoleCommandsNeedAnAdminOnTheSendingProxy(t *testing.T) {
	h := start(t, patience)
	h.state.sessions[adminUUID] = &internalv1.PlayerSession{PlayerUuid: adminUUID, ProxyId: "s-proxy", PermissionGroupId: "admin"}
	h.state.sessions[visitorUUID] = &internalv1.PlayerSession{PlayerUuid: visitorUUID, ProxyId: "s-proxy", PermissionGroupId: "default"}
	h.state.sessions[elsewhereUUID] = &internalv1.PlayerSession{PlayerUuid: elsewhereUUID, ProxyId: "s-other", PermissionGroupId: "admin"}
	h.state.models[string(valkeykeys.ReadModelPermissionGroup)+":admin"] = marshal(t, &nodev1.PermissionGroup{Permissions: []string{permissionnodes.AdminCommand}})
	h.state.models[string(valkeykeys.ReadModelPermissionGroup)+":default"] = marshal(t, &nodev1.PermissionGroup{Permissions: []string{"chat"}})
	proxy := h.welcomed(t, proxyToken, eventsv1.NodeType_NODE_TYPE_PROXY)
	command := func(actor string) *nodev1.SessionRequest {
		return &nodev1.SessionRequest{Message: &nodev1.SessionRequest_NodeCommand{NodeCommand: &nodev1.NodeCommandRequest{
			ActingPlayerUuid: actor, Command: &eventsv1.NodeCommand{Target: &eventsv1.NodeCommand_All{All: true}, Command: "op " + actor},
		}}}
	}

	for _, actor := range []string{visitorUUID, elsewhereUUID, adminUUID} {
		send(t, proxy, command(actor))
	}

	published := h.settle(t, proxy)
	if len(published) != 1 {
		t.Fatalf("published %v", published)
	}
	ran, ok := published[0].event.(*eventsv1.NodeCommand)
	if !ok || published[0].topic != kafkatopics.NodeCommand || published[0].key != kafkakeys.AllNodes ||
		!strings.HasSuffix(ran.GetCommand(), adminUUID) || ran.GetMetadata().GetActor().GetPlayerUuid() != adminUUID {
		t.Errorf("published %+v", published[0])
	}
	if testutil.ToFloat64(h.metrics.rejected.WithLabelValues(messageNodeCommand, errNotPermitted.Error())) != 1 ||
		testutil.ToFloat64(h.metrics.rejected.WithLabelValues(messageNodeCommand, errPlayerElsewhere.Error())) != 1 {
		t.Error("the refused commands were not counted")
	}
}

func TestLivePublishesFromNodesReachKafka(t *testing.T) {
	h := start(t, patience)
	backend := h.welcomed(t, lobbyToken, eventsv1.NodeType_NODE_TYPE_BACKEND)

	send(t, backend, &nodev1.SessionRequest{Message: &nodev1.SessionRequest_LivePublish{LivePublish: &nodev1.LiveChannelPublish{Channel: "party", TypeId: "Invite", Payload: []byte("x")}}})
	send(t, backend, &nodev1.SessionRequest{Message: &nodev1.SessionRequest_LivePublish{LivePublish: &nodev1.LiveChannelPublish{Channel: "Bad Name", TypeId: "Invite"}}})

	published := h.settle(t, backend)
	if len(published) != 1 {
		t.Fatalf("published %v", published)
	}
	message, ok := published[0].event.(*eventsv1.LiveChannelMessage)
	if !ok || published[0].key != "party" || message.GetSenderServerId() != "s-lobby" || message.GetSenderNodeType() != eventsv1.NodeType_NODE_TYPE_BACKEND {
		t.Errorf("published %+v", published[0])
	}
}

func TestGameMetricsFollowHeartbeatsAndEndWithTheSession(t *testing.T) {
	h := start(t, patience)
	backend := h.welcomed(t, lobbyToken, eventsv1.NodeType_NODE_TYPE_BACKEND)
	send(t, backend, &nodev1.SessionRequest{Message: &nodev1.SessionRequest_Heartbeat{Heartbeat: &nodev1.Heartbeat{Players: 7, Tps: 19.5}}})
	select {
	case <-h.published.events:
	case <-time.After(patience):
		t.Fatal("the heartbeat never arrived")
	}

	labels := []string{"s-lobby", "lobby", "backend"}
	if testutil.ToFloat64(h.metrics.players.WithLabelValues(labels...)) != 7 || testutil.ToFloat64(h.metrics.tps.WithLabelValues(labels...)) != 19.5 {
		t.Error("the heartbeat did not set the game metrics")
	}
	if testutil.ToFloat64(h.metrics.sessions.WithLabelValues("backend")) != 1 {
		t.Error("the open session is not counted")
	}
	if err := backend.CloseSend(); err != nil {
		t.Fatal(err)
	}
	if _, err := backend.Recv(); !errors.Is(err, io.EOF) {
		t.Fatalf("Recv after close: %v", err)
	}

	if series := testutil.CollectAndCount(h.metrics.players); series != 0 {
		t.Errorf("%d player series outlived the session", series)
	}
	if testutil.ToFloat64(h.metrics.ended.WithLabelValues(endClosed)) != 1 {
		t.Error("the closed session was not counted")
	}
}

func TestLiveChannelLimits(t *testing.T) {
	var live liveChannels
	now := time.Date(2026, 11, 2, 12, 0, 0, 0, time.UTC)
	for i := range maxLiveSubscriptions {
		if err := live.subscribe(fmt.Sprintf("channel-%d", i), true); err != nil {
			t.Fatalf("subscription %d: %v", i, err)
		}
	}
	checks := map[string]struct {
		got, want error
	}{
		"too many subscriptions": {live.subscribe("one-more", true), errLiveSubscribers},
		"bad channel":            {live.subscribe("UPPER", true), errLiveChannelName},
		"no type id":             {live.admit("c", "", nil, now), errLiveTypeID},
		"payload over 64 KiB":    {live.admit("c", "T", make([]byte, maxLivePayloadBytes+1), now), errLivePayload},
	}
	for name, check := range checks {
		if !errors.Is(check.got, check.want) {
			t.Errorf("%s: got %v, want %v", name, check.got, check.want)
		}
	}
	for i := range livePublishesPerSecond {
		if err := live.admit("c", "T", nil, now); err != nil {
			t.Fatalf("publish %d: %v", i, err)
		}
	}
	if err := live.admit("c", "T", nil, now); !errors.Is(err, errLivePublishLimit) {
		t.Errorf("publish over the budget: %v", err)
	}
	if err := live.admit("c", "T", nil, now.Add(10*time.Millisecond)); err != nil {
		t.Errorf("the budget did not refill: %v", err)
	}
}
