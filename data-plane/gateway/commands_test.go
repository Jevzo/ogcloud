package gateway

import (
	"testing"

	eventsv1 "github.com/jevzo/ogcloud/data-plane/pkg/gen/proto/ogcloud/events/v1"
	internalv1 "github.com/jevzo/ogcloud/data-plane/pkg/gen/proto/ogcloud/internalapi/v1"
	nodev1 "github.com/jevzo/ogcloud/data-plane/pkg/gen/proto/ogcloud/node/v1"
	"github.com/jevzo/ogcloud/data-plane/pkg/kafkatopics"
	"github.com/jevzo/ogcloud/data-plane/pkg/permissionnodes"
	"github.com/jevzo/ogcloud/data-plane/pkg/valkeykeys"
)

func stop(target, actor, requestID string) *nodev1.SessionRequest {
	return &nodev1.SessionRequest{Message: &nodev1.SessionRequest_ServerCommand{ServerCommand: &nodev1.ServerCommandRequest{
		RequestId: requestID, ServerId: target, ActingPlayerUuid: actor, Command: &nodev1.ServerCommandRequest_Stop{Stop: &eventsv1.StopServer{}},
	}}}
}

// result reads session messages until the result of requestID arrives.
func result(t *testing.T, stream nodev1.GatewayService_SessionClient, requestID string) *nodev1.ServerCommandResult {
	t.Helper()
	for {
		response, err := stream.Recv()
		if err != nil {
			t.Fatalf("Recv: %v", err)
		}
		if got := response.GetServerCommandResult(); got.GetRequestId() == requestID {
			return got
		}
	}
}

func TestPluginsMayStopOnlyTheirOwnServer(t *testing.T) {
	h := start(t, patience)
	backend := h.welcomed(t, lobbyToken, eventsv1.NodeType_NODE_TYPE_BACKEND)

	send(t, backend, stop("", "", "own"))
	send(t, backend, stop("s-other", "", "other"))

	if own := result(t, backend, "own"); own.GetError() != "" || own.GetServerId() != "s-lobby" {
		t.Errorf("own = %v", own)
	}
	if other := result(t, backend, "other"); other.GetError() != errNotOwnServer.Error() || other.GetServerId() != "s-other" {
		t.Errorf("other = %v", other)
	}
	published := h.settle(t, backend)
	if len(published) != 1 || published[0].topic != kafkatopics.ServerCommand || published[0].key != "s-lobby" {
		t.Errorf("published %v", published)
	}
}

func TestPluginsMayRequestServersAndLearnTheirIds(t *testing.T) {
	h := start(t, patience)
	backend := h.welcomed(t, lobbyToken, eventsv1.NodeType_NODE_TYPE_BACKEND)

	send(t, backend, &nodev1.SessionRequest{Message: &nodev1.SessionRequest_ServerCommand{ServerCommand: &nodev1.ServerCommandRequest{
		RequestId: "r-1", Command: &nodev1.ServerCommandRequest_Request{Request: &eventsv1.RequestServer{GroupId: "bedwars"}},
	}}})

	got := result(t, backend, "r-1")
	if got.GetError() != "" || !uuidText.MatchString(got.GetServerId()) {
		t.Fatalf("result = %v", got)
	}
	published := h.settle(t, backend)
	command, ok := published[0].event.(*eventsv1.ServerCommand)
	if !ok || command.GetServerId() != got.GetServerId() || command.GetRequest().GetGroupId() != "bedwars" ||
		command.GetMetadata().GetActor().GetSystem() != component {
		t.Errorf("published %v", published)
	}
}

func TestAdminsOnTheSendingProxyMayTargetAnyServer(t *testing.T) {
	h := start(t, patience)
	h.state.sessions[adminUUID] = &internalv1.PlayerSession{PlayerUuid: adminUUID, ProxyId: "s-proxy", PermissionGroupId: "admin"}
	h.state.sessions[visitorUUID] = &internalv1.PlayerSession{PlayerUuid: visitorUUID, ProxyId: "s-proxy", PermissionGroupId: "default"}
	h.state.models[string(valkeykeys.ReadModelPermissionGroup)+":admin"] = marshal(t, &nodev1.PermissionGroup{Permissions: []string{permissionnodes.Wildcard}})
	proxy := h.welcomed(t, proxyToken, eventsv1.NodeType_NODE_TYPE_PROXY)
	backend := h.welcomed(t, lobbyToken, eventsv1.NodeType_NODE_TYPE_BACKEND)

	send(t, proxy, stop("s-lobby", adminUUID, "admin"))
	send(t, proxy, stop("s-lobby", visitorUUID, "visitor"))
	send(t, backend, stop("s-other", adminUUID, "from-backend"))

	if got := result(t, proxy, "admin"); got.GetError() != "" {
		t.Errorf("admin = %v", got)
	}
	if got := result(t, proxy, "visitor"); got.GetError() != errNotPermitted.Error() {
		t.Errorf("visitor = %v", got)
	}
	if got := result(t, backend, "from-backend"); got.GetError() != rejectNodeType {
		t.Errorf("from a backend = %v", got)
	}
	published := h.settle(t, proxy)
	if len(published) != 1 || published[0].event.(*eventsv1.ServerCommand).GetMetadata().GetActor().GetPlayerUuid() != adminUUID {
		t.Errorf("published %v", published)
	}
}

func TestCommandsWithoutARequestIdAreRefused(t *testing.T) {
	h := start(t, patience)
	backend := h.welcomed(t, lobbyToken, eventsv1.NodeType_NODE_TYPE_BACKEND)

	send(t, backend, stop("", "", ""))

	if got := result(t, backend, ""); got.GetError() != rejectInvalid {
		t.Errorf("result = %v", got)
	}
	if published := h.settle(t, backend); len(published) != 0 {
		t.Errorf("published %v", published)
	}
}
