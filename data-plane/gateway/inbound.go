package gateway

import (
	"context"
	"fmt"
	"regexp"
	"slices"

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
)

// Message names in metrics and logs.
const (
	messageGameState      = "game_state"
	messagePlayerActivity = "player_activity"
	messageTransfer       = "transfer"
	messageNodeCommand    = "node_command"
	messageServerCommand  = "server_command"
	messageLiveSubscribe  = "live_subscription"
	messageLivePublish    = "live_publish"
	messageDeliveryResult = "delivery_result"
	messageHeartbeat      = "heartbeat"
	messagePlayerSnapshot = "player_snapshot"
	messageNpcChange      = "npc_change"
	messageAdminRequest   = "admin_request"
)

// Why a node message was dropped.
const (
	rejectNodeType    = "node_type"
	rejectInvalid     = "invalid"
	rejectUnsupported = "unsupported"
	rejectUnknown     = "unknown_delivery"
	rejectUnavailable = "unavailable"
)

var (
	proxies            = []eventsv1.NodeType{eventsv1.NodeType_NODE_TYPE_PROXY}
	backends           = []eventsv1.NodeType{eventsv1.NodeType_NODE_TYPE_BACKEND}
	proxiesAndBackends = []eventsv1.NodeType{eventsv1.NodeType_NODE_TYPE_PROXY, eventsv1.NodeType_NODE_TYPE_BACKEND}
	uuidText           = regexp.MustCompile(`^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$`)
)

// handle acts on one message from a node. What its node type may not send, or what is invalid, is dropped
// and counted while the session goes on (D-0116); only protocol errors end it.
func (s *Service) handle(ctx context.Context, session *Session, request *nodev1.SessionRequest) error {
	switch message := request.GetMessage().(type) {
	case *nodev1.SessionRequest_Hello:
		return status.Error(codes.FailedPrecondition, "Hello was already sent")
	case *nodev1.SessionRequest_Heartbeat:
		// Template agents share their pod with the server, whose own heartbeats count.
		if session.isNode() {
			s.heartbeat(ctx, session, message.Heartbeat)
		}
	case *nodev1.SessionRequest_GameState:
		if s.permits(session, messageGameState, backends) {
			s.gameState(ctx, session, message.GameState)
		}
	case *nodev1.SessionRequest_PlayerActivity:
		if s.permits(session, messagePlayerActivity, proxies) {
			s.playerActivity(ctx, session, message.PlayerActivity)
		}
	case *nodev1.SessionRequest_Transfer:
		if s.permits(session, messageTransfer, proxiesAndBackends) {
			s.transfer(ctx, session, message.Transfer)
		}
	case *nodev1.SessionRequest_NodeCommand:
		if s.permits(session, messageNodeCommand, proxies) {
			s.nodeCommand(ctx, session, message.NodeCommand)
		}
	case *nodev1.SessionRequest_LiveSubscription:
		if s.permits(session, messageLiveSubscribe, proxiesAndBackends) {
			subscription := message.LiveSubscription
			if err := session.live.subscribe(subscription.GetChannel(), subscription.GetSubscribed()); err != nil {
				s.reject(session, messageLiveSubscribe, err.Error())
			}
		}
	case *nodev1.SessionRequest_LivePublish:
		if s.permits(session, messageLivePublish, proxiesAndBackends) {
			s.livePublish(ctx, session, message.LivePublish)
		}
	case *nodev1.SessionRequest_DeliveryResult:
		s.deliveryResult(session, message.DeliveryResult)
	case *nodev1.SessionRequest_ServerCommand:
		s.serverCommand(ctx, session, message.ServerCommand)
	case *nodev1.SessionRequest_PlayerSnapshot:
		if s.permits(session, messagePlayerSnapshot, proxies) {
			s.playerSnapshot(ctx, session, message.PlayerSnapshot)
		}
	case *nodev1.SessionRequest_NpcChange:
		if s.permits(session, messageNpcChange, backends) {
			s.npcChange(ctx, session, message.NpcChange)
		}
	case *nodev1.SessionRequest_AdminRequest:
		if s.permits(session, messageAdminRequest, proxies) {
			s.adminRequest(ctx, session, message.AdminRequest)
		}
	default:
		s.reject(session, fmt.Sprintf("%T", message), rejectUnsupported)
	}
	return nil
}

func (s *Service) permits(session *Session, message string, nodeTypes []eventsv1.NodeType) bool {
	if slices.Contains(nodeTypes, session.NodeType) {
		return true
	}
	s.reject(session, message, rejectNodeType)
	return false
}

func (s *Service) reject(session *Session, message, reason string) {
	s.Metrics.rejected.WithLabelValues(message, reason).Inc()
	s.Logger.Warn("node message dropped", "serverId", session.Peer.ServerID, "nodeType", session.NodeType.String(),
		"message", message, "reason", reason)
}

// emit publishes an event a node caused; a lost one is logged and counted, the session goes on.
func (s *Service) emit(ctx context.Context, session *Session, message, topic, key string, actor *eventsv1.Actor,
	event func(*eventsv1.EventMetadata) proto.Message,
) {
	metadata, err := s.Stamper.Metadata(actor)
	if err == nil {
		err = s.Events.Publish(ctx, topic, key, event(metadata))
	}
	if err != nil {
		s.Metrics.unsent.WithLabelValues(message).Inc()
		s.Logger.Warn("node event not published", "serverId", session.Peer.ServerID, "message", message, "error", err)
	}
}

// heartbeat forwards a node's vitals and updates its game metrics; a replaced session no longer owns them.
func (s *Service) heartbeat(ctx context.Context, session *Session, heartbeat *nodev1.Heartbeat) {
	if s.Sessions.Holds(session) {
		s.Metrics.heartbeat(session, heartbeat)
	}
	serverID := session.Peer.ServerID
	s.emit(ctx, session, messageHeartbeat, kafkatopics.NodeHeartbeat, serverID, events.System(component),
		func(metadata *eventsv1.EventMetadata) proto.Message {
			return &eventsv1.NodeHeartbeat{
				Metadata: metadata, ServerId: serverID, NodeType: session.NodeType,
				Players: heartbeat.GetPlayers(), MaxPlayers: heartbeat.GetMaxPlayers(), Tps: heartbeat.GetTps(),
				Mspt: heartbeat.GetMspt(), MemoryUsedBytes: heartbeat.GetMemoryUsedBytes(), MemoryMaxBytes: heartbeat.GetMemoryMaxBytes(),
			}
		})
}

func (s *Service) gameState(ctx context.Context, session *Session, update *nodev1.GameStateUpdate) {
	serverID := session.Peer.ServerID
	s.emit(ctx, session, messageGameState, kafkatopics.ServerGamestate, serverID, events.System(component),
		func(metadata *eventsv1.EventMetadata) proto.Message {
			return &eventsv1.GameStateChanged{Metadata: metadata, ServerId: serverID, GameState: update.GetGameState()}
		})
}

// playerActivity forwards a proxy's report; the proxy id is always the reporting pod's.
func (s *Service) playerActivity(ctx context.Context, session *Session, report *nodev1.PlayerActivityReport) {
	activity := &eventsv1.PlayerActivity{PlayerUuid: report.GetPlayerUuid()}
	switch reported := report.GetActivity().(type) {
	case *nodev1.PlayerActivityReport_Connected:
		reported.Connected.ProxyId = session.Peer.ServerID
		activity.Activity = &eventsv1.PlayerActivity_Connected{Connected: reported.Connected}
	case *nodev1.PlayerActivityReport_Switched:
		activity.Activity = &eventsv1.PlayerActivity_Switched{Switched: reported.Switched}
	case *nodev1.PlayerActivityReport_Disconnected:
		reported.Disconnected.ProxyId = session.Peer.ServerID
		activity.Activity = &eventsv1.PlayerActivity_Disconnected{Disconnected: reported.Disconnected}
	}
	if activity.Activity == nil || !uuidText.MatchString(activity.GetPlayerUuid()) {
		s.reject(session, messagePlayerActivity, rejectInvalid)
		return
	}
	s.emit(ctx, session, messagePlayerActivity, kafkatopics.PlayerActivity, activity.GetPlayerUuid(), events.Player(activity.GetPlayerUuid()),
		func(metadata *eventsv1.EventMetadata) proto.Message {
			activity.Metadata = metadata
			return activity
		})
}

func (s *Service) transfer(ctx context.Context, session *Session, transfer *eventsv1.TransferRequested) {
	key := transfer.GetPlayerUuid()
	if key == "" {
		key = transfer.GetFromServerId()
	}
	if key == "" || transfer.GetDestination() == nil {
		s.reject(session, messageTransfer, rejectInvalid)
		return
	}
	s.emit(ctx, session, messageTransfer, kafkatopics.PlayerTransfer, key, events.System(component),
		func(metadata *eventsv1.EventMetadata) proto.Message {
			transfer.Metadata = metadata
			return transfer
		})
}

// nodeCommand publishes a console command for an in-game admin connected through the sending proxy (D-0115).
func (s *Service) nodeCommand(ctx context.Context, session *Session, request *nodev1.NodeCommandRequest) {
	command := request.GetCommand()
	key, ok := nodeCommandKey(command)
	if !ok || command.GetCommand() == "" || !uuidText.MatchString(request.GetActingPlayerUuid()) {
		s.reject(session, messageNodeCommand, rejectInvalid)
		return
	}
	if err := s.adminCheck(ctx, session, request.GetActingPlayerUuid(), permissionnodes.AdminCommand); err != nil {
		s.reject(session, messageNodeCommand, err.Error())
		return
	}
	s.emit(ctx, session, messageNodeCommand, kafkatopics.NodeCommand, key, events.Player(request.GetActingPlayerUuid()),
		func(metadata *eventsv1.EventMetadata) proto.Message {
			command.Metadata = metadata
			return command
		})
}

// playerSnapshot forwards a proxy's online players, named as the reporting pod's (D-0130).
func (s *Service) playerSnapshot(ctx context.Context, session *Session, snapshot *nodev1.PlayerSnapshot) {
	for _, player := range snapshot.GetPlayers() {
		if !uuidText.MatchString(player.GetPlayerUuid()) {
			s.reject(session, messagePlayerSnapshot, rejectInvalid)
			return
		}
	}
	proxyID := session.Peer.ServerID
	s.emit(ctx, session, messagePlayerSnapshot, kafkatopics.PlayerSnapshot, proxyID, events.System(component),
		func(metadata *eventsv1.EventMetadata) proto.Message {
			return &eventsv1.ProxyPlayersSnapshot{Metadata: metadata, ProxyId: proxyID, Players: snapshot.GetPlayers()}
		})
}

// npcChange forwards an in-game admin's NPC edit of the sending server's group to the API (D-0129).
func (s *Service) npcChange(ctx context.Context, session *Session, request *nodev1.NpcChangeRequest) {
	change, err := s.npcChangeOf(session, request)
	if err != nil {
		s.reject(session, messageNpcChange, err.Error())
		return
	}
	if err := s.adminCheck(ctx, session, request.GetActingPlayerUuid(), permissionnodes.AdminNpc); err != nil {
		s.reject(session, messageNpcChange, err.Error())
		return
	}
	s.emit(ctx, session, messageNpcChange, kafkatopics.NpcChange, npcIDOf(change), events.Player(request.GetActingPlayerUuid()),
		func(metadata *eventsv1.EventMetadata) proto.Message {
			change.Metadata = metadata
			return change
		})
}

// npcChangeOf checks a requested change against the sender's group and gives a new NPC its id.
func (s *Service) npcChangeOf(session *Session, request *nodev1.NpcChangeRequest) (*internalv1.NpcChangeRequested, error) {
	if !uuidText.MatchString(request.GetActingPlayerUuid()) {
		return nil, errInvalid
	}
	change := &internalv1.NpcChangeRequested{GroupId: session.Peer.GroupID}
	switch requested := request.GetChange().(type) {
	case *nodev1.NpcChangeRequest_Create:
		if requested.Create.GetGroupId() != session.Peer.GroupID {
			return nil, errInvalid
		}
		id, err := s.IDs.Next()
		if err != nil {
			s.Logger.Error("no NPC id", "error", err)
			return nil, errUnavailable
		}
		npc := proto.CloneOf(requested.Create)
		npc.NpcId = id
		change.Change = &internalv1.NpcChangeRequested_Create{Create: npc}
	case *nodev1.NpcChangeRequest_Update:
		if requested.Update.GetGroupId() != session.Peer.GroupID || !uuidText.MatchString(requested.Update.GetNpcId()) {
			return nil, errInvalid
		}
		change.Change = &internalv1.NpcChangeRequested_Update{Update: requested.Update}
	case *nodev1.NpcChangeRequest_DeleteNpcId:
		if !uuidText.MatchString(requested.DeleteNpcId) {
			return nil, errInvalid
		}
		change.Change = &internalv1.NpcChangeRequested_DeleteNpcId{DeleteNpcId: requested.DeleteNpcId}
	default:
		return nil, errInvalid
	}
	return change, nil
}

// npcIDOf keys a change by its NPC, so edits of one NPC stay in order.
func npcIDOf(change *internalv1.NpcChangeRequested) string {
	switch requested := change.GetChange().(type) {
	case *internalv1.NpcChangeRequested_Create:
		return requested.Create.GetNpcId()
	case *internalv1.NpcChangeRequested_Update:
		return requested.Update.GetNpcId()
	default:
		return change.GetDeleteNpcId()
	}
}

// nodeCommandKey keys a command by its target, as the API does.
func nodeCommandKey(command *eventsv1.NodeCommand) (string, bool) {
	switch target := command.GetTarget().(type) {
	case *eventsv1.NodeCommand_All:
		return kafkakeys.AllNodes, target.All
	case *eventsv1.NodeCommand_ServerId:
		return target.ServerId, target.ServerId != ""
	case *eventsv1.NodeCommand_GroupId:
		return target.GroupId, target.GroupId != ""
	default:
		return "", false
	}
}

func (s *Service) livePublish(ctx context.Context, session *Session, publish *nodev1.LiveChannelPublish) {
	if err := session.live.admit(publish.GetChannel(), publish.GetTypeId(), publish.GetPayload(), s.Now()); err != nil {
		s.reject(session, messageLivePublish, err.Error())
		return
	}
	s.emit(ctx, session, messageLivePublish, kafkatopics.LiveChannel, publish.GetChannel(), events.System(component),
		func(metadata *eventsv1.EventMetadata) proto.Message {
			return &eventsv1.LiveChannelMessage{
				Metadata: metadata, Channel: publish.GetChannel(), SenderServerId: session.Peer.ServerID,
				SenderNodeType: session.NodeType, TypeId: publish.GetTypeId(), Payload: publish.GetPayload(),
			}
		})
}

func (s *Service) deliveryResult(session *Session, result *nodev1.DeliveryResult) {
	delivery, ok := session.deliveries.take(result.GetDeliveryId())
	if !ok {
		s.reject(session, messageDeliveryResult, rejectUnknown)
		return
	}
	if result.GetSuccess() {
		s.Metrics.delivery(delivery.kind, outcomeSuccess)
		return
	}
	s.Metrics.delivery(delivery.kind, outcomeFailure)
	s.Logger.Warn("delivery failed", "serverId", session.Peer.ServerID, "kind", delivery.kind, "message", result.GetMessage())
}
