package gateway

import (
	"errors"
	"testing"

	"github.com/prometheus/client_golang/prometheus/testutil"

	eventsv1 "github.com/jevzo/ogcloud/data-plane/pkg/gen/proto/ogcloud/events/v1"
	internalv1 "github.com/jevzo/ogcloud/data-plane/pkg/gen/proto/ogcloud/internalapi/v1"
	nodev1 "github.com/jevzo/ogcloud/data-plane/pkg/gen/proto/ogcloud/node/v1"
	"github.com/jevzo/ogcloud/data-plane/pkg/kafkakeys"
	"github.com/jevzo/ogcloud/data-plane/pkg/kafkatopics"
	"github.com/jevzo/ogcloud/data-plane/pkg/permissionnodes"
	"github.com/jevzo/ogcloud/data-plane/pkg/runtimeprofiles"
	"github.com/jevzo/ogcloud/data-plane/pkg/valkeykeys"
)

func TestEachPodRoleRunsItsNodeTypes(t *testing.T) {
	allowed := map[runtimeprofiles.Role][]eventsv1.NodeType{
		runtimeprofiles.RoleProxy:   {eventsv1.NodeType_NODE_TYPE_PROXY, eventsv1.NodeType_NODE_TYPE_TEMPLATE_AGENT},
		runtimeprofiles.RoleBackend: {eventsv1.NodeType_NODE_TYPE_BACKEND, eventsv1.NodeType_NODE_TYPE_TEMPLATE_AGENT},
	}
	for role, types := range allowed {
		for value := range eventsv1.NodeType_name {
			nodeType := eventsv1.NodeType(value)
			want := false
			for _, ok := range types {
				want = want || ok == nodeType
			}
			err := checkHello(Peer{Role: role}, hello(nodeType))
			if (err == nil) != want {
				t.Errorf("%s pod running %v: %v", role, nodeType, err)
			}
		}
	}
}

// commandResult sends a server command and returns its result.
func commandResult(t *testing.T, stream nodev1.GatewayService_SessionClient, request *nodev1.ServerCommandRequest) *nodev1.ServerCommandResult {
	t.Helper()
	send(t, stream, &nodev1.SessionRequest{Message: &nodev1.SessionRequest_ServerCommand{ServerCommand: request}})
	return result(t, stream, request.GetRequestId())
}

func TestServerCommandRules(t *testing.T) {
	h := start(t, patience)
	h.state.sessions[adminUUID] = &internalv1.PlayerSession{PlayerUuid: adminUUID, ProxyId: "s-proxy", PermissionGroupId: "admin"}
	h.state.models[string(valkeykeys.ReadModelPermissionGroup)+":admin"] = marshal(t, &nodev1.PermissionGroup{Permissions: []string{permissionnodes.AdminServer}})
	sessions := map[string]nodev1.GatewayService_SessionClient{
		"proxy":   h.welcomed(t, proxyToken, eventsv1.NodeType_NODE_TYPE_PROXY),
		"backend": h.welcomed(t, lobbyToken, eventsv1.NodeType_NODE_TYPE_BACKEND),
		"agent":   h.welcomed(t, lobbyToken, eventsv1.NodeType_NODE_TYPE_TEMPLATE_AGENT),
	}
	kill := &nodev1.ServerCommandRequest_Kill{Kill: &eventsv1.KillServer{}}
	push := &nodev1.ServerCommandRequest_PushTemplate{PushTemplate: &eventsv1.PushTemplate{}}
	tests := []struct {
		name, from, want string
		request          *nodev1.ServerCommandRequest
	}{
		{"plugin kills its own server", "backend", "", &nodev1.ServerCommandRequest{Command: kill}},
		{"plugin pushes another server", "backend", errNotOwnServer.Error(), &nodev1.ServerCommandRequest{ServerId: "s-other", Command: push}},
		{"template agents send none", "agent", rejectNodeType, &nodev1.ServerCommandRequest{Command: kill}},
		{"backends act for no admin", "backend", rejectNodeType, &nodev1.ServerCommandRequest{ActingPlayerUuid: adminUUID, ServerId: "s-x", Command: kill}},
		{"admins name their target", "proxy", rejectInvalid, &nodev1.ServerCommandRequest{ActingPlayerUuid: adminUUID, Command: kill}},
		{"admins push any server", "proxy", "", &nodev1.ServerCommandRequest{ActingPlayerUuid: adminUUID, ServerId: "s-other", Command: push}},
		{"acting player must be a uuid", "proxy", rejectInvalid, &nodev1.ServerCommandRequest{ActingPlayerUuid: "Jevzo", ServerId: "s-x", Command: kill}},
		{"requests name a group", "backend", rejectInvalid, &nodev1.ServerCommandRequest{Command: &nodev1.ServerCommandRequest_Request{Request: &eventsv1.RequestServer{}}}},
		{"no command", "backend", rejectInvalid, &nodev1.ServerCommandRequest{}},
	}
	for i, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			tt.request.RequestId = string(rune('a' + i))

			if got := commandResult(t, sessions[tt.from], tt.request); got.GetError() != tt.want {
				t.Errorf("result %v, want error %q", got, tt.want)
			}
		})
	}
}

func TestAServerCommandThatCannotBePublishedIsNotReportedAsDone(t *testing.T) {
	h := start(t, patience)
	h.published.failing = kafkatopics.ServerCommand
	backend := h.welcomed(t, lobbyToken, eventsv1.NodeType_NODE_TYPE_BACKEND)

	got := commandResult(t, backend, &nodev1.ServerCommandRequest{RequestId: "r", Command: &nodev1.ServerCommandRequest_Stop{Stop: &eventsv1.StopServer{}}})

	if got.GetError() != rejectUnavailable {
		t.Errorf("result %v", got)
	}
}

func consoleCommand(actor string, command *eventsv1.NodeCommand) *nodev1.SessionRequest {
	return &nodev1.SessionRequest{Message: &nodev1.SessionRequest_NodeCommand{NodeCommand: &nodev1.NodeCommandRequest{ActingPlayerUuid: actor, Command: command}}}
}

func TestConsoleCommandsFailClosedAndKeyByTarget(t *testing.T) {
	h := start(t, patience)
	h.state.sessions[adminUUID] = &internalv1.PlayerSession{PlayerUuid: adminUUID, ProxyId: "s-proxy", PermissionGroupId: "owner"}
	h.state.sessions[visitorUUID] = &internalv1.PlayerSession{PlayerUuid: visitorUUID, ProxyId: "s-proxy", PermissionGroupId: "missing"}
	h.state.models[string(valkeykeys.ReadModelPermissionGroup)+":owner"] = marshal(t, &nodev1.PermissionGroup{Permissions: []string{permissionnodes.Wildcard}})
	proxy := h.welcomed(t, proxyToken, eventsv1.NodeType_NODE_TYPE_PROXY)

	send(t, proxy, consoleCommand(visitorUUID, &eventsv1.NodeCommand{Target: &eventsv1.NodeCommand_All{All: true}, Command: "op x"}))
	send(t, proxy, consoleCommand(adminUUID, &eventsv1.NodeCommand{Target: &eventsv1.NodeCommand_ServerId{ServerId: "s-lobby"}, Command: "say 1"}))
	send(t, proxy, consoleCommand(adminUUID, &eventsv1.NodeCommand{Target: &eventsv1.NodeCommand_GroupId{GroupId: "bedwars"}, Command: "say 2"}))
	send(t, proxy, consoleCommand(adminUUID, &eventsv1.NodeCommand{Target: &eventsv1.NodeCommand_All{All: true}, Command: "say 3"}))
	send(t, proxy, consoleCommand(adminUUID, &eventsv1.NodeCommand{Command: "say 4"}))

	published := h.settle(t, proxy)
	var keys []string
	for _, event := range published {
		keys = append(keys, event.key)
	}
	if len(keys) != 3 || keys[0] != "s-lobby" || keys[1] != "bedwars" || keys[2] != kafkakeys.AllNodes {
		t.Errorf("published keys %v; a wildcard grants console commands", keys)
	}
	if testutil.ToFloat64(h.metrics.rejected.WithLabelValues(messageNodeCommand, rejectUnavailable)) != 1 ||
		testutil.ToFloat64(h.metrics.rejected.WithLabelValues(messageNodeCommand, rejectInvalid)) != 1 {
		t.Error("a missing permission group must refuse as unavailable, an untargeted command as invalid")
	}
}

func TestAValkeyOutageRefusesAdminCommands(t *testing.T) {
	h := start(t, patience)
	h.state.sessionErr = errors.New("valkey is down")
	proxy := h.welcomed(t, proxyToken, eventsv1.NodeType_NODE_TYPE_PROXY)

	send(t, proxy, consoleCommand(adminUUID, &eventsv1.NodeCommand{Target: &eventsv1.NodeCommand_All{All: true}, Command: "op x"}))

	if published := h.settle(t, proxy); len(published) != 0 {
		t.Errorf("published %v", published)
	}
	if testutil.ToFloat64(h.metrics.rejected.WithLabelValues(messageNodeCommand, rejectUnavailable)) != 1 {
		t.Error("the outage was not counted")
	}
}

func TestNodeEventsArePublishedWithTheirKeys(t *testing.T) {
	h := start(t, patience)
	proxy := h.welcomed(t, proxyToken, eventsv1.NodeType_NODE_TYPE_PROXY)
	backend := h.welcomed(t, lobbyToken, eventsv1.NodeType_NODE_TYPE_BACKEND)
	toLobby := &eventsv1.TransferRequested_GroupId{GroupId: "lobby"}

	send(t, proxy, &nodev1.SessionRequest{Message: &nodev1.SessionRequest_Transfer{Transfer: &eventsv1.TransferRequested{
		Subject: &eventsv1.TransferRequested_PlayerUuid{PlayerUuid: visitorUUID}, Destination: toLobby,
	}}})
	send(t, proxy, &nodev1.SessionRequest{Message: &nodev1.SessionRequest_Transfer{Transfer: &eventsv1.TransferRequested{
		Subject: &eventsv1.TransferRequested_PlayerUuid{PlayerUuid: visitorUUID},
	}}})
	send(t, proxy, &nodev1.SessionRequest{Message: &nodev1.SessionRequest_PlayerActivity{PlayerActivity: &nodev1.PlayerActivityReport{
		PlayerUuid: visitorUUID, Activity: &nodev1.PlayerActivityReport_Disconnected{Disconnected: &eventsv1.PlayerDisconnected{ProxyId: "forged"}},
	}}})
	fromProxy := h.settle(t, proxy)
	send(t, backend, &nodev1.SessionRequest{Message: &nodev1.SessionRequest_Transfer{Transfer: &eventsv1.TransferRequested{
		Subject: &eventsv1.TransferRequested_FromServerId{FromServerId: "s-lobby"}, Destination: toLobby,
	}}})
	send(t, backend, &nodev1.SessionRequest{Message: &nodev1.SessionRequest_GameState{GameState: &nodev1.GameStateUpdate{GameState: eventsv1.GameState_GAME_STATE_INGAME}}})
	fromBackend := h.settle(t, backend)

	if len(fromProxy) != 2 || fromProxy[0].topic != kafkatopics.PlayerTransfer || fromProxy[0].key != visitorUUID ||
		fromProxy[1].event.(*eventsv1.PlayerActivity).GetDisconnected().GetProxyId() != "s-proxy" {
		t.Errorf("from the proxy %+v; a transfer without destination is invalid and the proxy id is the pod's", fromProxy)
	}
	if len(fromBackend) != 2 || fromBackend[0].key != "s-lobby" || fromBackend[1].topic != kafkatopics.ServerGamestate || fromBackend[1].key != "s-lobby" {
		t.Errorf("from the backend %+v", fromBackend)
	}
}

func TestTemplateAgentsOnlyAnswerAndPush(t *testing.T) {
	h := start(t, patience)
	for _, token := range []string{lobbyToken, proxyToken} {
		agent := h.welcomed(t, token, eventsv1.NodeType_NODE_TYPE_TEMPLATE_AGENT)

		send(t, agent, &nodev1.SessionRequest{Message: &nodev1.SessionRequest_Heartbeat{Heartbeat: &nodev1.Heartbeat{Players: 1}}})
		send(t, agent, &nodev1.SessionRequest{Message: &nodev1.SessionRequest_LivePublish{LivePublish: &nodev1.LiveChannelPublish{Channel: "c", TypeId: "T"}}})
		send(t, agent, &nodev1.SessionRequest{Message: &nodev1.SessionRequest_Transfer{Transfer: &eventsv1.TransferRequested{
			Subject: &eventsv1.TransferRequested_PlayerUuid{PlayerUuid: visitorUUID}, Destination: &eventsv1.TransferRequested_GroupId{GroupId: "lobby"},
		}}})
		// The command's result is the barrier: everything sent before it has been handled.
		commandResult(t, agent, &nodev1.ServerCommandRequest{RequestId: token, Command: &nodev1.ServerCommandRequest_Kill{Kill: &eventsv1.KillServer{}}})
	}

	select {
	case event := <-h.published.events:
		t.Errorf("a template agent published %+v", event)
	default:
	}
	for _, message := range []string{messageLivePublish, messageTransfer} {
		if testutil.ToFloat64(h.metrics.rejected.WithLabelValues(message, rejectNodeType)) != 2 {
			t.Errorf("%s from agents was not refused", message)
		}
	}
}

func TestProxiesSnapshotTheirPlayers(t *testing.T) {
	h := start(t, patience)
	proxy := h.welcomed(t, proxyToken, eventsv1.NodeType_NODE_TYPE_PROXY)
	backend := h.welcomed(t, lobbyToken, eventsv1.NodeType_NODE_TYPE_BACKEND)
	players := []*eventsv1.SnapshotPlayer{{PlayerUuid: visitorUUID, PlayerName: "Jevzo", ServerId: "s-lobby"}}

	send(t, proxy, &nodev1.SessionRequest{Message: &nodev1.SessionRequest_PlayerSnapshot{PlayerSnapshot: &nodev1.PlayerSnapshot{Players: players}}})
	send(t, proxy, &nodev1.SessionRequest{Message: &nodev1.SessionRequest_PlayerSnapshot{PlayerSnapshot: &nodev1.PlayerSnapshot{
		Players: []*eventsv1.SnapshotPlayer{{PlayerUuid: "Jevzo"}},
	}}})
	send(t, backend, &nodev1.SessionRequest{Message: &nodev1.SessionRequest_PlayerSnapshot{PlayerSnapshot: &nodev1.PlayerSnapshot{Players: players}}})

	published := append(h.settle(t, proxy), h.settle(t, backend)...)
	if len(published) != 1 || published[0].topic != kafkatopics.PlayerSnapshot || published[0].key != "s-proxy" ||
		published[0].event.(*eventsv1.ProxyPlayersSnapshot).GetProxyId() != "s-proxy" {
		t.Errorf("published %+v; only the proxy's valid snapshot counts", published)
	}
}

func TestInGameNpcEditsNeedAnAdminOnTheBackend(t *testing.T) {
	h := start(t, patience)
	h.state.sessions[adminUUID] = &internalv1.PlayerSession{PlayerUuid: adminUUID, ProxyId: "s-proxy", ServerId: "s-lobby", PermissionGroupId: "builder"}
	h.state.sessions[visitorUUID] = &internalv1.PlayerSession{PlayerUuid: visitorUUID, ProxyId: "s-proxy", ServerId: "s-other", PermissionGroupId: "builder"}
	h.state.models[string(valkeykeys.ReadModelPermissionGroup)+":builder"] = marshal(t, &nodev1.PermissionGroup{Permissions: []string{permissionnodes.AdminNpc}})
	backend := h.welcomed(t, lobbyToken, eventsv1.NodeType_NODE_TYPE_BACKEND)
	const shop, old = "0192d2c1-0000-7000-8000-00000000000a", "0192d2c1-0000-7000-8000-00000000000b"
	change := func(actor string, request *nodev1.NpcChangeRequest) *nodev1.SessionRequest {
		request.ActingPlayerUuid = actor
		return &nodev1.SessionRequest{Message: &nodev1.SessionRequest_NpcChange{NpcChange: request}}
	}
	create := func(group string) *nodev1.NpcChangeRequest {
		return &nodev1.NpcChangeRequest{Change: &nodev1.NpcChangeRequest_Create{Create: &nodev1.Npc{NpcId: shop, GroupId: group, Title: "Shop"}}}
	}
	update := func(id string) *nodev1.NpcChangeRequest {
		return &nodev1.NpcChangeRequest{Change: &nodev1.NpcChangeRequest_Update{Update: &nodev1.Npc{NpcId: id, GroupId: "lobby"}}}
	}

	send(t, backend, change(adminUUID, create("lobby")))
	send(t, backend, change(adminUUID, update(shop)))
	send(t, backend, change(adminUUID, &nodev1.NpcChangeRequest{Change: &nodev1.NpcChangeRequest_DeleteNpcId{DeleteNpcId: old}}))
	send(t, backend, change(adminUUID, create("bedwars")))
	send(t, backend, change(adminUUID, update("shop")))
	send(t, backend, change(visitorUUID, create("lobby")))

	published := h.settle(t, backend)
	if len(published) != 3 || published[0].topic != kafkatopics.NpcChange {
		t.Fatalf("published %+v", published)
	}
	created := published[0].event.(*internalv1.NpcChangeRequested)
	if id := created.GetCreate().GetNpcId(); id == shop || !uuidText.MatchString(id) || published[0].key != id ||
		created.GetCreate().GetTitle() != "Shop" || created.GetGroupId() != "lobby" || created.GetMetadata().GetActor().GetPlayerUuid() != adminUUID {
		t.Errorf("a new NPC gets a gateway id and the admin as actor: %v", created)
	}
	if published[1].key != shop || published[1].event.(*internalv1.NpcChangeRequested).GetUpdate() == nil || published[2].key != old {
		t.Errorf("published %+v", published[1:])
	}
	if testutil.ToFloat64(h.metrics.rejected.WithLabelValues(messageNpcChange, rejectInvalid)) != 2 ||
		testutil.ToFloat64(h.metrics.rejected.WithLabelValues(messageNpcChange, errPlayerElsewhere.Error())) != 1 {
		t.Error("another group's NPC, a malformed id and a player on another server must be refused")
	}
}
