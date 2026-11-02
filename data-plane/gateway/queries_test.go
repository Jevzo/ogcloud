package gateway

import (
	"testing"

	"google.golang.org/grpc/codes"
	"google.golang.org/grpc/status"

	eventsv1 "github.com/jevzo/ogcloud/data-plane/pkg/gen/proto/ogcloud/events/v1"
	internalv1 "github.com/jevzo/ogcloud/data-plane/pkg/gen/proto/ogcloud/internalapi/v1"
	nodev1 "github.com/jevzo/ogcloud/data-plane/pkg/gen/proto/ogcloud/node/v1"
	"github.com/jevzo/ogcloud/data-plane/pkg/valkeykeys"
)

func TestQueriesAnswerFromLiveStateAndReadModels(t *testing.T) {
	h := start(t, patience)
	h.state.sessions[visitorUUID] = &internalv1.PlayerSession{PlayerUuid: visitorUUID, PlayerName: "Jevzo", ProxyId: "s-proxy", ServerId: "s-lobby"}
	h.state.models[string(valkeykeys.ReadModelPlayerProfile)+":"+visitorUUID] = marshal(t, &nodev1.PlayerProfile{PlayerUuid: visitorUUID, PermissionGroupId: "vip"})
	h.state.models[string(valkeykeys.ReadModelPermissionGroup)+":vip"] = marshal(t, &nodev1.PermissionGroup{PermissionGroupId: "vip", Weight: 5})
	h.state.models[string(valkeykeys.ReadModelNpcsByGroup)+":lobby"] = marshal(t, &nodev1.ListNpcsResponse{Npcs: []*nodev1.Npc{{NpcId: "n-1"}}})
	ctx := as(t, lobbyToken)

	server, err := h.client.GetServer(ctx, &nodev1.GetServerRequest{ServerId: "s-lobby"})
	if err != nil || !server.GetServer().GetGroupMaintenance() || server.GetServer().GetState() != eventsv1.ServerState_SERVER_STATE_RUNNING {
		t.Errorf("GetServer = %v, %v", server, err)
	}
	listed, err := h.client.ListServers(ctx, &nodev1.ListServersRequest{GroupId: "bedwars"})
	if err != nil || len(listed.GetServers()) != 0 {
		t.Errorf("ListServers(bedwars) = %v, %v", listed, err)
	}
	found, err := h.client.FindPlayer(ctx, &nodev1.FindPlayerRequest{Player: &nodev1.FindPlayerRequest_PlayerName{PlayerName: "jevzo"}})
	if err != nil || found.GetLocation().GetServerId() != "s-lobby" || found.GetLocation().GetProxyId() != "s-proxy" {
		t.Errorf("FindPlayer = %v, %v", found, err)
	}
	profile, err := h.client.GetPlayerProfile(ctx, &nodev1.GetPlayerProfileRequest{PlayerUuid: visitorUUID})
	if err != nil || profile.GetProfile().GetPermissionGroupId() != "vip" {
		t.Errorf("GetPlayerProfile = %v, %v", profile, err)
	}
	group, err := h.client.GetPermissionGroup(ctx, &nodev1.GetPermissionGroupRequest{PermissionGroupId: "vip"})
	if err != nil || group.GetPermissionGroup().GetWeight() != 5 {
		t.Errorf("GetPermissionGroup = %v, %v", group, err)
	}
	npcs, err := h.client.ListNpcs(ctx, &nodev1.ListNpcsRequest{GroupId: "lobby"})
	if err != nil || len(npcs.GetNpcs()) != 1 {
		t.Errorf("ListNpcs = %v, %v", npcs, err)
	}
}

func TestQueriesForMissingThings(t *testing.T) {
	h := start(t, patience)
	ctx := as(t, lobbyToken)

	_, serverErr := h.client.GetServer(ctx, &nodev1.GetServerRequest{ServerId: "gone"})
	_, profileErr := h.client.GetPlayerProfile(ctx, &nodev1.GetPlayerProfileRequest{PlayerUuid: visitorUUID})
	_, findErr := h.client.FindPlayer(ctx, &nodev1.FindPlayerRequest{})
	offline, err := h.client.FindPlayer(ctx, &nodev1.FindPlayerRequest{Player: &nodev1.FindPlayerRequest_PlayerUuid{PlayerUuid: visitorUUID}})
	npcs, npcErr := h.client.ListNpcs(ctx, &nodev1.ListNpcsRequest{GroupId: "bedwars"})

	if status.Code(serverErr) != codes.NotFound || status.Code(profileErr) != codes.NotFound || status.Code(findErr) != codes.InvalidArgument {
		t.Errorf("errors = %v, %v, %v", serverErr, profileErr, findErr)
	}
	if err != nil || offline.GetLocation() != nil {
		t.Errorf("an offline player = %v, %v", offline, err)
	}
	if npcErr != nil || len(npcs.GetNpcs()) != 0 {
		t.Errorf("a group without NPCs = %v, %v", npcs, npcErr)
	}
}

func TestOnlyProxiesResolveLogins(t *testing.T) {
	h := start(t, patience)
	request := &nodev1.ResolveLoginRequest{PlayerUuid: visitorUUID, PlayerName: "Jevzo", Protocol: 47}

	allowed, err := h.client.ResolveLogin(as(t, proxyToken), request)
	_, refused := h.client.ResolveLogin(as(t, lobbyToken), request)

	if err != nil || !allowed.GetAllowed() {
		t.Fatalf("ResolveLogin = %v, %v", allowed, err)
	}
	if len(h.controller.logins) != 1 || h.controller.logins[0].GetProxyServerId() != "s-proxy" || h.controller.logins[0].GetLogin().GetProtocol() != 47 {
		t.Errorf("the controller was asked %v", h.controller.logins)
	}
	if status.Code(refused) != codes.PermissionDenied {
		t.Errorf("a backend resolved a login: %v", refused)
	}
}

func TestControllerOutagesStayRetryable(t *testing.T) {
	h := start(t, patience)
	h.controller.err = status.Error(codes.Unavailable, "no leader")

	_, err := h.client.ResolveLogin(as(t, proxyToken), &nodev1.ResolveLoginRequest{PlayerUuid: visitorUUID})

	if status.Code(err) != codes.Unavailable {
		t.Errorf("got %v", err)
	}
}
