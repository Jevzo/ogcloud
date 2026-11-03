package gateway

import (
	"context"
	"errors"
	"fmt"
	"slices"

	eventsv1 "github.com/jevzo/ogcloud/data-plane/pkg/gen/proto/ogcloud/events/v1"
	nodev1 "github.com/jevzo/ogcloud/data-plane/pkg/gen/proto/ogcloud/node/v1"
	"github.com/jevzo/ogcloud/data-plane/pkg/permissionnodes"
	"github.com/jevzo/ogcloud/data-plane/pkg/valkeykeys"
)

var (
	errPlayerElsewhere = errors.New("player_elsewhere")
	errNotPermitted    = errors.New("not_permitted")
)

// actingAdmin checks an in-game admin command (D-0115, D-0129): the player must be connected through the
// proxy, or play on the backend, that sent it and hold permission.
func (s *Service) actingAdmin(ctx context.Context, sender *Session, playerUUID, permission string) error {
	player, err := s.State.Session(ctx, playerUUID)
	if err != nil {
		return fmt.Errorf("session of %s: %w", playerUUID, err)
	}
	where := player.GetServerId()
	if sender.NodeType == eventsv1.NodeType_NODE_TYPE_PROXY {
		where = player.GetProxyId()
	}
	if player == nil || where != sender.Peer.ServerID {
		return errPlayerElsewhere
	}
	// A missing read model is an outage, not a group without permissions; either way the command is refused.
	group := &nodev1.PermissionGroup{}
	if err := s.readModel(ctx, valkeykeys.ReadModelPermissionGroup, player.GetPermissionGroupId(), group); err != nil {
		return fmt.Errorf("permission group of %s: %w", playerUUID, err)
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
