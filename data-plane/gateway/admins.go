package gateway

import (
	"context"
	"errors"
	"fmt"
	"slices"

	"google.golang.org/protobuf/proto"

	nodev1 "github.com/jevzo/ogcloud/data-plane/pkg/gen/proto/ogcloud/node/v1"
	"github.com/jevzo/ogcloud/data-plane/pkg/permissionnodes"
	"github.com/jevzo/ogcloud/data-plane/pkg/valkeykeys"
)

var (
	errNotOnProxy   = errors.New("not_on_proxy")
	errNotPermitted = errors.New("not_permitted")
)

// actingAdmin checks an in-game admin command (D-0115): the player must be connected through the proxy
// that sent it and hold permission.
func (s *Service) actingAdmin(ctx context.Context, proxy *Session, playerUUID, permission string) error {
	player, err := s.State.Session(ctx, playerUUID)
	if err != nil {
		return fmt.Errorf("session of %s: %w", playerUUID, err)
	}
	if player == nil || player.GetProxyId() != proxy.Peer.ServerID {
		return errNotOnProxy
	}
	encoded, err := s.State.ReadModel(ctx, valkeykeys.ReadModelPermissionGroup, player.GetPermissionGroupId())
	if err != nil {
		return fmt.Errorf("permission group %s: %w", player.GetPermissionGroupId(), err)
	}
	group := &nodev1.PermissionGroup{}
	if err := proto.Unmarshal(encoded, group); err != nil {
		return fmt.Errorf("permission group %s: %w", player.GetPermissionGroupId(), err)
	}
	if !grants(group, permission) {
		return errNotPermitted
	}
	return nil
}

// grants follows the domain's rule: the exact node, or everything for the wildcard.
func grants(group *nodev1.PermissionGroup, permission string) bool {
	return slices.Contains(group.GetPermissions(), permissionnodes.Wildcard) || slices.Contains(group.GetPermissions(), permission)
}
