package gateway

import (
	"context"
	"slices"
	"testing"
	"time"

	"github.com/prometheus/client_golang/prometheus/testutil"
	"google.golang.org/protobuf/proto"

	eventsv1 "github.com/jevzo/ogcloud/data-plane/pkg/gen/proto/ogcloud/events/v1"
	internalv1 "github.com/jevzo/ogcloud/data-plane/pkg/gen/proto/ogcloud/internalapi/v1"
	nodev1 "github.com/jevzo/ogcloud/data-plane/pkg/gen/proto/ogcloud/node/v1"
	"github.com/jevzo/ogcloud/data-plane/pkg/kafkatopics"
)

const playerUUID = "0190f3c4-6b2a-7c3e-9f00-000000000001"

// network is one replica's sessions: two proxies, a lobby and a bedwars backend, and the lobby's agent.
type network struct {
	router   *Router
	sessions map[string]*Session
	now      *time.Time
}

func newNetwork(t *testing.T) *network {
	t.Helper()
	now := time.Date(2026, 11, 2, 12, 0, 0, 0, time.UTC)
	state := &fakeState{sessions: map[string]*internalv1.PlayerSession{
		playerUUID: {PlayerUuid: playerUUID, ProxyId: "proxy-1", ServerId: "lobby-1", PermissionGroupId: "admin"},
	}}
	dependencies := testDependencies(state, &recorder{events: make(chan published, 10)}, func() time.Time { return now })
	router, err := NewRouter(dependencies, testOptions())
	if err != nil {
		t.Fatal(err)
	}
	n := &network{router: router, sessions: map[string]*Session{}, now: &now}
	for name, node := range map[string]struct {
		group    string
		nodeType eventsv1.NodeType
	}{
		"proxy-1":   {"proxy", eventsv1.NodeType_NODE_TYPE_PROXY},
		"proxy-2":   {"proxy", eventsv1.NodeType_NODE_TYPE_PROXY},
		"lobby-1":   {"lobby", eventsv1.NodeType_NODE_TYPE_BACKEND},
		"bedwars-1": {"bedwars", eventsv1.NodeType_NODE_TYPE_BACKEND},
		"agent":     {"lobby", eventsv1.NodeType_NODE_TYPE_TEMPLATE_AGENT},
	} {
		serverID := name
		if name == "agent" {
			serverID = "lobby-1"
		}
		session := newSession(Peer{ServerID: serverID, GroupID: node.group}, hello(node.nodeType), 16, func(error) {})
		dependencies.Sessions.Add(session)
		n.sessions[name] = session
	}
	return n
}

func (n *network) handle(t *testing.T, topic string, event proto.Message) {
	t.Helper()
	encoded, err := proto.Marshal(event)
	if err != nil {
		t.Fatal(err)
	}
	n.router.Handle(context.Background(), topic, encoded)
}

// received drains what each session got.
func (n *network) received() map[string][]*nodev1.SessionResponse {
	got := map[string][]*nodev1.SessionResponse{}
	for name, session := range n.sessions {
		for len(session.out) > 0 {
			got[name] = append(got[name], <-session.out)
		}
	}
	return got
}

func recipients(got map[string][]*nodev1.SessionResponse) []string {
	var names []string
	for name := range got {
		names = append(names, name)
	}
	slices.Sort(names)
	return names
}

func TestEachRecordReachesOnlyTheSessionsThatNeedIt(t *testing.T) {
	tests := []struct {
		name  string
		topic string
		event proto.Message
		want  []string
	}{
		{"lifecycle", kafkatopics.ServerLifecycle, &eventsv1.ServerLifecycleChanged{ServerId: "x"},
			[]string{"bedwars-1", "lobby-1", "proxy-1", "proxy-2"}},
		{"network settings", kafkatopics.NodeBroadcast, &eventsv1.NodeBroadcast{Update: &eventsv1.NodeBroadcast_NetworkSettings{NetworkSettings: &eventsv1.NetworkSettingsUpdated{}}},
			[]string{"bedwars-1", "lobby-1", "proxy-1", "proxy-2"}},
		{"permission group", kafkatopics.NodeBroadcast, &eventsv1.NodeBroadcast{Update: &eventsv1.NodeBroadcast_PermissionGroup{PermissionGroup: &eventsv1.PermissionGroupUpdated{}}},
			[]string{"bedwars-1", "lobby-1", "proxy-1", "proxy-2"}},
		{"group", kafkatopics.NodeBroadcast, &eventsv1.NodeBroadcast{Update: &eventsv1.NodeBroadcast_Group{Group: &eventsv1.GroupUpdated{GroupId: "lobby"}}},
			[]string{"lobby-1", "proxy-1", "proxy-2"}},
		{"npc", kafkatopics.NodeBroadcast, &eventsv1.NodeBroadcast{Update: &eventsv1.NodeBroadcast_Npc{Npc: &eventsv1.NpcUpdated{GroupId: "bedwars"}}},
			[]string{"bedwars-1"}},
		{"player permissions", kafkatopics.NodeBroadcast, &eventsv1.NodeBroadcast{Update: &eventsv1.NodeBroadcast_PlayerPermissions{PlayerPermissions: &eventsv1.PlayerPermissionsUpdated{PlayerUuid: playerUUID}}},
			[]string{"lobby-1", "proxy-1"}},
		{"offline player's permissions", kafkatopics.NodeBroadcast, &eventsv1.NodeBroadcast{Update: &eventsv1.NodeBroadcast_PlayerPermissions{PlayerPermissions: &eventsv1.PlayerPermissionsUpdated{PlayerUuid: "nobody"}}},
			nil},
		{"command to all", kafkatopics.NodeCommand, &eventsv1.NodeCommand{Target: &eventsv1.NodeCommand_All{All: true}, Command: "say hi"},
			[]string{"bedwars-1", "lobby-1", "proxy-1", "proxy-2"}},
		{"command to a server", kafkatopics.NodeCommand, &eventsv1.NodeCommand{Target: &eventsv1.NodeCommand_ServerId{ServerId: "lobby-1"}, Command: "say hi"},
			[]string{"lobby-1"}},
		{"command to a group", kafkatopics.NodeCommand, &eventsv1.NodeCommand{Target: &eventsv1.NodeCommand_GroupId{GroupId: "proxy"}, Command: "say hi"},
			[]string{"proxy-1", "proxy-2"}},
		{"player transfer", kafkatopics.PlayerTransfer, &eventsv1.TransferRequested{Subject: &eventsv1.TransferRequested_PlayerUuid{PlayerUuid: playerUUID}},
			[]string{"proxy-1"}},
		{"server transfer", kafkatopics.PlayerTransfer, &eventsv1.TransferRequested{Subject: &eventsv1.TransferRequested_FromServerId{FromServerId: "lobby-1"}},
			[]string{"proxy-1", "proxy-2"}},
		{"link code", kafkatopics.AccountLink, &eventsv1.LinkCodeIssued{PlayerUuid: playerUUID, Code: "123456"},
			[]string{"proxy-1"}},
		{"template push", kafkatopics.ServerCommand, &eventsv1.ServerCommand{ServerId: "lobby-1", Command: &eventsv1.ServerCommand_PushTemplate{PushTemplate: &eventsv1.PushTemplate{}}},
			[]string{"agent"}},
		{"other server commands", kafkatopics.ServerCommand, &eventsv1.ServerCommand{ServerId: "lobby-1", Command: &eventsv1.ServerCommand_Stop{Stop: &eventsv1.StopServer{}}},
			nil},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			n := newNetwork(t)

			n.handle(t, tt.topic, tt.event)

			if got := recipients(n.received()); !slices.Equal(got, tt.want) {
				t.Errorf("reached %v, want %v", got, tt.want)
			}
		})
	}
}

func TestLiveMessagesReachSubscribersOnly(t *testing.T) {
	n := newNetwork(t)
	for _, name := range []string{"proxy-2", "bedwars-1"} {
		if err := n.sessions[name].live.subscribe("party:invites", true); err != nil {
			t.Fatal(err)
		}
	}

	n.handle(t, kafkatopics.LiveChannel, &eventsv1.LiveChannelMessage{Channel: "party:invites", SenderServerId: "lobby-1", TypeId: "Invite", Payload: []byte("x")})

	got := n.received()
	if names := recipients(got); !slices.Equal(names, []string{"bedwars-1", "proxy-2"}) {
		t.Fatalf("reached %v", names)
	}
	if message := got["proxy-2"][0].GetLiveMessage(); message.GetSenderServerId() != "lobby-1" || string(message.GetPayload()) != "x" {
		t.Errorf("message = %v", message)
	}
}

func TestDeliveriesAreAnsweredOrTimeOut(t *testing.T) {
	n := newNetwork(t)
	metrics := n.router.Metrics
	n.handle(t, kafkatopics.NodeCommand, &eventsv1.NodeCommand{Target: &eventsv1.NodeCommand_GroupId{GroupId: "proxy"}, Command: "say hi"})
	got := n.received()
	answered := got["proxy-1"][0].GetDelivery()
	if answered.GetDeliveryId() == "" || answered.GetCommand().GetCommand() != "say hi" ||
		answered.GetDeliveryId() == got["proxy-2"][0].GetDelivery().GetDeliveryId() {
		t.Fatalf("deliveries = %v", got)
	}
	service := &Service{Dependencies: n.router.Dependencies}

	service.deliveryResult(n.sessions["proxy-1"], &nodev1.DeliveryResult{DeliveryId: answered.GetDeliveryId(), Success: true})
	n.router.ExpireDeliveries(n.now.Add(deliveryTimeout - time.Second))
	n.router.ExpireDeliveries(n.now.Add(deliveryTimeout + time.Second))

	if testutil.ToFloat64(metrics.deliveries.WithLabelValues("command", outcomeSuccess)) != 1 ||
		testutil.ToFloat64(metrics.deliveries.WithLabelValues("command", outcomeTimeout)) != 1 {
		t.Error("expected one answered and one timed-out delivery")
	}
	service.deliveryResult(n.sessions["proxy-2"], &nodev1.DeliveryResult{DeliveryId: "late"})
	if testutil.ToFloat64(metrics.rejected.WithLabelValues(messageDeliveryResult, rejectUnknown)) != 1 {
		t.Error("an unknown delivery id was not counted")
	}
}

func TestUndecodableRecordsAreCounted(t *testing.T) {
	n := newNetwork(t)

	n.router.Handle(context.Background(), kafkatopics.NodeBroadcast, []byte{0xff})
	n.handle(t, kafkatopics.NodeBroadcast, &eventsv1.NodeBroadcast{})

	if got := testutil.ToFloat64(n.router.Metrics.unroutable.WithLabelValues(kafkatopics.NodeBroadcast)); got != 2 {
		t.Errorf("unroutable = %v", got)
	}
}
