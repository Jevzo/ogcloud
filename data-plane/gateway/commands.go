package gateway

import (
	"context"
	"errors"

	"github.com/jevzo/ogcloud/data-plane/pkg/nodeerrors"

	"github.com/jevzo/ogcloud/data-plane/pkg/events"
	eventsv1 "github.com/jevzo/ogcloud/data-plane/pkg/gen/proto/ogcloud/events/v1"
	nodev1 "github.com/jevzo/ogcloud/data-plane/pkg/gen/proto/ogcloud/node/v1"
	"github.com/jevzo/ogcloud/data-plane/pkg/kafkatopics"
	"github.com/jevzo/ogcloud/data-plane/pkg/permissionnodes"
)

var (
	errNotOwnServer = errors.New(nodeerrors.NotOwnServer)
	errNodeType     = errors.New(rejectNodeType)
	errInvalid      = errors.New(rejectInvalid)
	errUnavailable  = errors.New(rejectUnavailable)
)

// serverCommand runs a node's server command and answers it with a result (D-0068, D-0117).
func (s *Service) serverCommand(ctx context.Context, session *Session, request *nodev1.ServerCommandRequest) {
	serverID, err := s.issueServerCommand(ctx, session, request)
	result := &nodev1.ServerCommandResult{RequestId: request.GetRequestId(), ServerId: serverID}
	if err != nil {
		result.Error = err.Error()
		s.reject(session, messageServerCommand, err.Error())
	}
	session.Send(&nodev1.SessionResponse{Message: &nodev1.SessionResponse_ServerCommandResult{ServerCommandResult: result}})
}

// issueServerCommand checks the command and publishes it; it returns the target, or the id assigned to a request.
func (s *Service) issueServerCommand(ctx context.Context, session *Session, request *nodev1.ServerCommandRequest) (string, error) {
	if request.GetRequestId() == "" {
		return "", errInvalid
	}
	command := &eventsv1.ServerCommand{ServerId: request.GetServerId()}
	switch requested := request.GetCommand().(type) {
	case *nodev1.ServerCommandRequest_Request:
		if requested.Request.GetGroupId() == "" {
			return "", errInvalid
		}
		id, err := s.IDs.Next()
		if err != nil {
			s.Logger.Error("no server id", "error", err)
			return "", errUnavailable
		}
		command.ServerId = id
		command.Command = &eventsv1.ServerCommand_Request{Request: requested.Request}
	case *nodev1.ServerCommandRequest_Stop:
		command.Command = &eventsv1.ServerCommand_Stop{Stop: requested.Stop}
	case *nodev1.ServerCommandRequest_Kill:
		command.Command = &eventsv1.ServerCommand_Kill{Kill: requested.Kill}
	case *nodev1.ServerCommandRequest_PushTemplate:
		command.Command = &eventsv1.ServerCommand_PushTemplate{PushTemplate: requested.PushTemplate}
	default:
		return "", errInvalid
	}
	actor, err := s.authorizeServerCommand(ctx, session, request, command)
	if err != nil {
		return command.GetServerId(), err
	}
	metadata, err := s.Stamper.Metadata(actor)
	if err == nil {
		command.Metadata = metadata
		err = s.Events.Publish(ctx, kafkatopics.ServerCommand, command.GetServerId(), command)
	}
	if err != nil {
		s.Logger.Warn("server command not published", "serverId", session.Peer.ServerID, "error", err)
		return command.GetServerId(), errUnavailable
	}
	return command.GetServerId(), nil
}

// authorizeServerCommand applies D-0117 and returns who acts: an admin on the sending proxy may target any
// server; the plugin API may request servers but stop, kill and push only its own.
func (s *Service) authorizeServerCommand(ctx context.Context, session *Session, request *nodev1.ServerCommandRequest,
	command *eventsv1.ServerCommand,
) (*eventsv1.Actor, error) {
	if !session.isNode() {
		return nil, errNodeType
	}
	if player := request.GetActingPlayerUuid(); player != "" {
		if session.NodeType != eventsv1.NodeType_NODE_TYPE_PROXY {
			return nil, errNodeType
		}
		if !uuidText.MatchString(player) {
			return nil, errInvalid
		}
		if err := s.adminCheck(ctx, session, player, permissionnodes.AdminServer); err != nil {
			return nil, err
		}
		// An admin names the target; only the plugin API defaults to its own server.
		if command.GetRequest() == nil && command.GetServerId() == "" {
			return nil, errInvalid
		}
		return events.Player(player), nil
	}
	if command.GetRequest() == nil {
		if command.GetServerId() == "" {
			command.ServerId = session.Peer.ServerID
		}
		if command.GetServerId() != session.Peer.ServerID {
			return nil, errNotOwnServer
		}
	}
	return events.System(component), nil
}
