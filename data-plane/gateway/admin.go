package gateway

import (
	"context"
	"errors"

	"google.golang.org/grpc/codes"
	"google.golang.org/grpc/status"
	"google.golang.org/protobuf/proto"

	"github.com/jevzo/ogcloud/data-plane/pkg/events"
	eventsv1 "github.com/jevzo/ogcloud/data-plane/pkg/gen/proto/ogcloud/events/v1"
	internalv1 "github.com/jevzo/ogcloud/data-plane/pkg/gen/proto/ogcloud/internalapi/v1"
	nodev1 "github.com/jevzo/ogcloud/data-plane/pkg/gen/proto/ogcloud/node/v1"
	"github.com/jevzo/ogcloud/data-plane/pkg/kafkakeys"
	"github.com/jevzo/ogcloud/data-plane/pkg/kafkatopics"
	"github.com/jevzo/ogcloud/data-plane/pkg/permissionnodes"
	"github.com/jevzo/ogcloud/data-plane/pkg/valkeykeys"
)

var (
	errUnknownGroup           = errors.New("unknown_group")
	errUnknownPermissionGroup = errors.New("unknown_permission_group")
)

// adminChange is a checked admin request: the event, its key and the node the acting player needs.
type adminChange struct {
	event      *internalv1.AdminChangeRequested
	key        string
	permission string
	// The read model that must exist for the change to make sense.
	target     valkeykeys.ReadModelKind
	targetID   string
	targetGone error
}

// adminRequest publishes an in-game admin's change for the API and answers it with a result (D-0132).
func (s *Service) adminRequest(ctx context.Context, session *Session, request *nodev1.AdminRequest) {
	result := &nodev1.AdminRequestResult{RequestId: request.GetRequestId()}
	if err := s.issueAdminChange(ctx, session, request); err != nil {
		result.Error = err.Error()
		s.reject(session, messageAdminRequest, err.Error())
	}
	session.Send(&nodev1.SessionResponse{Message: &nodev1.SessionResponse_AdminRequestResult{AdminRequestResult: result}})
}

func (s *Service) issueAdminChange(ctx context.Context, session *Session, request *nodev1.AdminRequest) error {
	player := request.GetActingPlayerUuid()
	change, ok := adminChangeOf(request)
	if !ok || request.GetRequestId() == "" || !uuidText.MatchString(player) {
		return errInvalid
	}
	if err := s.adminCheck(ctx, session, player, change.permission); err != nil {
		return err
	}
	if change.target != "" {
		if err := s.targetExists(ctx, change); err != nil {
			return err
		}
	}
	metadata, err := s.Stamper.Metadata(events.Player(player))
	if err == nil {
		change.event.Metadata = metadata
		err = s.Events.Publish(ctx, kafkatopics.AdminChange, change.key, change.event)
	}
	if err != nil {
		s.Logger.Warn("admin change not published", "serverId", session.Peer.ServerID, "error", err)
		return errUnavailable
	}
	return nil
}

// adminChangeOf checks a request's shape; false when it is malformed.
func adminChangeOf(request *nodev1.AdminRequest) (adminChange, bool) {
	switch requested := request.GetChange().(type) {
	case *nodev1.AdminRequest_GroupMaintenance:
		id := requested.GroupMaintenance.GetGroupId()
		return adminChange{
			event:      &internalv1.AdminChangeRequested{Change: &internalv1.AdminChangeRequested_GroupMaintenance{GroupMaintenance: requested.GroupMaintenance}},
			key:        id,
			permission: permissionnodes.AdminGroup,
			target:     valkeykeys.ReadModelGroup, targetID: id, targetGone: errUnknownGroup,
		}, id != ""
	case *nodev1.AdminRequest_NetworkMaintenance:
		return adminChange{
			event:      &internalv1.AdminChangeRequested{Change: &internalv1.AdminChangeRequested_NetworkMaintenance{NetworkMaintenance: requested.NetworkMaintenance}},
			key:        kafkakeys.NetworkSubject,
			permission: permissionnodes.AdminNetwork,
		}, true
	case *nodev1.AdminRequest_Rank:
		rank := requested.Rank
		// Unset is permanent; a set duration must be positive.
		timed := rank.GetDuration() == nil || rank.GetDuration().AsDuration() > 0
		return adminChange{
			event:      &internalv1.AdminChangeRequested{Change: &internalv1.AdminChangeRequested_Rank{Rank: rank}},
			key:        rank.GetPlayerUuid(),
			permission: permissionnodes.AdminPermission,
			target:     valkeykeys.ReadModelPermissionGroup, targetID: rank.GetPermissionGroupId(), targetGone: errUnknownPermissionGroup,
		}, uuidText.MatchString(rank.GetPlayerUuid()) && rank.GetPermissionGroupId() != "" && timed
	default:
		return adminChange{}, false
	}
}

// targetExists checks the change's group or permission group against its read model.
func (s *Service) targetExists(ctx context.Context, change adminChange) error {
	var model proto.Message = &eventsv1.GroupUpdated{}
	if change.target == valkeykeys.ReadModelPermissionGroup {
		model = &nodev1.PermissionGroup{}
	}
	err := s.readModel(ctx, change.target, change.targetID, model)
	switch status.Code(err) {
	case codes.OK:
		return nil
	case codes.NotFound:
		return change.targetGone
	default:
		s.Logger.Warn("admin change target unreadable", "target", change.target, "id", change.targetID, "error", err)
		return errUnavailable
	}
}
