package gateway

import (
	"context"

	"google.golang.org/grpc/codes"
	"google.golang.org/grpc/status"
	"google.golang.org/protobuf/proto"

	internalv1 "github.com/jevzo/ogcloud/data-plane/pkg/gen/proto/ogcloud/internalapi/v1"
	nodev1 "github.com/jevzo/ogcloud/data-plane/pkg/gen/proto/ogcloud/node/v1"
	"github.com/jevzo/ogcloud/data-plane/pkg/valkeykeys"
)

// Queries answer from Valkey records and read models; read models are the stored node messages (D-0077).

// GetServer returns a live server with its group's maintenance flag.
func (s *Service) GetServer(ctx context.Context, request *nodev1.GetServerRequest) (*nodev1.GetServerResponse, error) {
	record, err := s.State.Server(ctx, request.GetServerId())
	if err != nil {
		return nil, unavailable("server", err)
	}
	if record == nil {
		return nil, status.Errorf(codes.NotFound, "server %s is not live", request.GetServerId())
	}
	infos, err := s.serverInfos(ctx, []*internalv1.ServerRecord{record})
	if err != nil {
		return nil, unavailable("server", err)
	}
	return &nodev1.GetServerResponse{Server: infos[0]}, nil
}

// ListServers returns every live server, or those of one group.
func (s *Service) ListServers(ctx context.Context, request *nodev1.ListServersRequest) (*nodev1.ListServersResponse, error) {
	var records []*internalv1.ServerRecord
	var err error
	if request.GetGroupId() == "" {
		records, err = s.State.Servers(ctx)
	} else {
		records, err = s.State.ServersOfGroup(ctx, request.GetGroupId())
	}
	if err != nil {
		return nil, unavailable("servers", err)
	}
	infos, err := s.serverInfos(ctx, records)
	if err != nil {
		return nil, unavailable("servers", err)
	}
	return &nodev1.ListServersResponse{Servers: infos}, nil
}

// FindPlayer locates an online player by uuid or name; an offline player has no location.
func (s *Service) FindPlayer(ctx context.Context, request *nodev1.FindPlayerRequest) (*nodev1.FindPlayerResponse, error) {
	var player *internalv1.PlayerSession
	var err error
	switch {
	case request.GetPlayerUuid() != "":
		player, err = s.State.Session(ctx, request.GetPlayerUuid())
	case request.GetPlayerName() != "":
		player, err = s.State.SessionByName(ctx, request.GetPlayerName())
	default:
		return nil, status.Error(codes.InvalidArgument, "a player uuid or name is required")
	}
	if err != nil {
		return nil, unavailable("player", err)
	}
	if player == nil {
		return &nodev1.FindPlayerResponse{}, nil
	}
	return &nodev1.FindPlayerResponse{Location: locationOf(player)}, nil
}

func locationOf(session *internalv1.PlayerSession) *nodev1.PlayerLocation {
	return &nodev1.PlayerLocation{
		PlayerUuid: session.GetPlayerUuid(), PlayerName: session.GetPlayerName(), ProxyId: session.GetProxyId(), ServerId: session.GetServerId(),
	}
}

// GetPlayerProfile returns a player's rank as nodes apply it.
func (s *Service) GetPlayerProfile(ctx context.Context, request *nodev1.GetPlayerProfileRequest) (*nodev1.GetPlayerProfileResponse, error) {
	profile := &nodev1.PlayerProfile{}
	if err := s.readModel(ctx, valkeykeys.ReadModelPlayerProfile, request.GetPlayerUuid(), profile); err != nil {
		return nil, err
	}
	return &nodev1.GetPlayerProfileResponse{Profile: profile}, nil
}

// GetPermissionGroup returns a permission group's nodes and display.
func (s *Service) GetPermissionGroup(ctx context.Context, request *nodev1.GetPermissionGroupRequest) (*nodev1.GetPermissionGroupResponse, error) {
	group := &nodev1.PermissionGroup{}
	if err := s.readModel(ctx, valkeykeys.ReadModelPermissionGroup, request.GetPermissionGroupId(), group); err != nil {
		return nil, err
	}
	return &nodev1.GetPermissionGroupResponse{PermissionGroup: group}, nil
}

// ListNpcs returns a group's NPCs; a group without NPCs has none.
func (s *Service) ListNpcs(ctx context.Context, request *nodev1.ListNpcsRequest) (*nodev1.ListNpcsResponse, error) {
	npcs := &nodev1.ListNpcsResponse{}
	err := s.readModel(ctx, valkeykeys.ReadModelNpcsByGroup, request.GetGroupId(), npcs)
	if status.Code(err) == codes.NotFound {
		return npcs, nil
	}
	return npcs, err
}

// ListGroups returns every group from the group-list read model (D-0132).
func (s *Service) ListGroups(ctx context.Context, _ *nodev1.ListGroupsRequest) (*nodev1.ListGroupsResponse, error) {
	groups := &nodev1.ListGroupsResponse{}
	return groups, s.listModel(ctx, valkeykeys.ReadModelGroupList, groups)
}

// ListPermissionGroups returns every permission group from its list read model (D-0132).
func (s *Service) ListPermissionGroups(ctx context.Context, _ *nodev1.ListPermissionGroupsRequest) (*nodev1.ListPermissionGroupsResponse, error) {
	groups := &nodev1.ListPermissionGroupsResponse{}
	return groups, s.listModel(ctx, valkeykeys.ReadModelPermissionGroupList, groups)
}

// listModel reads a list read model. The controller writes it on every change and resync, so a missing one is
// not written yet, never an empty list.
func (s *Service) listModel(ctx context.Context, kind valkeykeys.ReadModelKind, list proto.Message) error {
	err := s.readModel(ctx, kind, valkeykeys.ListId, list)
	if status.Code(err) == codes.NotFound {
		return status.Errorf(codes.Unavailable, "the %s is not ready", kind)
	}
	return err
}

// ListPlayers returns the players on a backend or connected through a proxy (D-0132).
func (s *Service) ListPlayers(ctx context.Context, request *nodev1.ListPlayersRequest) (*nodev1.ListPlayersResponse, error) {
	server := request.GetServerId()
	if server == "" {
		return nil, status.Error(codes.InvalidArgument, "a server id is required")
	}
	sessions, err := s.State.OnlineSessions(ctx)
	if err != nil {
		return nil, unavailable("players", err)
	}
	players := &nodev1.ListPlayersResponse{}
	for _, session := range sessions {
		if session.GetServerId() == server || session.GetProxyId() == server {
			players.Players = append(players.Players, locationOf(session))
		}
	}
	return players, nil
}

// readModel decodes a stored read model into message; a missing one is NOT_FOUND.
func (s Dependencies) readModel(ctx context.Context, kind valkeykeys.ReadModelKind, id string, message proto.Message) error {
	if id == "" {
		return status.Errorf(codes.InvalidArgument, "%s: an id is required", kind)
	}
	encoded, err := s.State.ReadModel(ctx, kind, id)
	if err != nil {
		return unavailable(string(kind), err)
	}
	if encoded == nil {
		return status.Errorf(codes.NotFound, "no %s %s", kind, id)
	}
	if err := proto.Unmarshal(encoded, message); err != nil {
		return status.Errorf(codes.Internal, "%s %s: %v", kind, id, err)
	}
	return nil
}

func unavailable(what string, err error) error {
	return status.Errorf(codes.Unavailable, "%s: %v", what, err)
}
