package gateway

import (
	"context"
	"errors"
	"fmt"
	"time"

	"google.golang.org/protobuf/proto"

	eventsv1 "github.com/jevzo/ogcloud/data-plane/pkg/gen/proto/ogcloud/events/v1"
	internalv1 "github.com/jevzo/ogcloud/data-plane/pkg/gen/proto/ogcloud/internalapi/v1"
	nodev1 "github.com/jevzo/ogcloud/data-plane/pkg/gen/proto/ogcloud/node/v1"
	"github.com/jevzo/ogcloud/data-plane/pkg/kafkatopics"
)

// Topics lists what every gateway replica reads, each with a consumer group of its own (D-0081, D-0116).
var Topics = []string{
	kafkatopics.ServerLifecycle, kafkatopics.NodeBroadcast, kafkatopics.NodeCommand, kafkatopics.PlayerTransfer,
	kafkatopics.AccountLink, kafkatopics.LiveChannel, kafkatopics.ServerCommand,
}

var errUnroutable = errors.New("nothing to route")

// Router hands records from Kafka to the sessions this replica holds that need them (D-0116).
type Router struct {
	Dependencies
	deliveryTimeout time.Duration
}

// NewRouter routes to dependencies.Sessions; deliveries unanswered for deliveryTimeout count as timed out.
func NewRouter(dependencies Dependencies, deliveryTimeout time.Duration) *Router {
	return &Router{Dependencies: dependencies, deliveryTimeout: deliveryTimeout}
}

// Handle routes one record; one that cannot be routed is logged and counted.
func (r *Router) Handle(ctx context.Context, topic string, value []byte) {
	var err error
	switch topic {
	case kafkatopics.ServerLifecycle:
		err = route(ctx, value, &eventsv1.ServerLifecycleChanged{}, r.lifecycle)
	case kafkatopics.NodeBroadcast:
		err = route(ctx, value, &eventsv1.NodeBroadcast{}, r.broadcast)
	case kafkatopics.NodeCommand:
		err = route(ctx, value, &eventsv1.NodeCommand{}, r.command)
	case kafkatopics.PlayerTransfer:
		err = route(ctx, value, &eventsv1.TransferRequested{}, r.transfer)
	case kafkatopics.AccountLink:
		err = route(ctx, value, &eventsv1.LinkCodeIssued{}, r.linkCode)
	case kafkatopics.LiveChannel:
		err = route(ctx, value, &eventsv1.LiveChannelMessage{}, r.live)
	case kafkatopics.ServerCommand:
		err = route(ctx, value, &eventsv1.ServerCommand{}, r.serverCommand)
	default:
		err = fmt.Errorf("topic %s: %w", topic, errUnroutable)
	}
	if err != nil {
		r.Metrics.unroutable.WithLabelValues(topic).Inc()
		r.Logger.Warn("record not routed", "topic", topic, "error", err)
	}
}

func route[T proto.Message](ctx context.Context, value []byte, message T, handle func(context.Context, T) error) error {
	if err := proto.Unmarshal(value, message); err != nil {
		return fmt.Errorf("decode: %w", err)
	}
	return handle(ctx, message)
}

func (r *Router) lifecycle(_ context.Context, event *eventsv1.ServerLifecycleChanged) error {
	r.send(r.Sessions.Select((*Session).isNode), &nodev1.SessionResponse{Message: &nodev1.SessionResponse_ServerLifecycle{ServerLifecycle: event}})
	return nil
}

func (r *Router) broadcast(ctx context.Context, broadcast *eventsv1.NodeBroadcast) error {
	var targets []*Session
	switch update := broadcast.GetUpdate().(type) {
	case *eventsv1.NodeBroadcast_NetworkSettings, *eventsv1.NodeBroadcast_PermissionGroup:
		targets = r.Sessions.Select((*Session).isNode)
	case *eventsv1.NodeBroadcast_Group:
		targets = r.Sessions.Select(func(s *Session) bool { return isProxy(s) || isBackendOf(s, update.Group.GetGroupId()) })
	case *eventsv1.NodeBroadcast_Npc:
		targets = r.Sessions.Select(func(s *Session) bool { return isBackendOf(s, update.Npc.GetGroupId()) })
	case *eventsv1.NodeBroadcast_PlayerPermissions:
		player, err := r.State.Session(ctx, update.PlayerPermissions.GetPlayerUuid())
		if err != nil {
			return fmt.Errorf("session of %s: %w", update.PlayerPermissions.GetPlayerUuid(), err)
		}
		targets = r.Sessions.Select(func(s *Session) bool { return holds(s, player) })
	default:
		return fmt.Errorf("broadcast without an update: %w", errUnroutable)
	}
	r.send(targets, &nodev1.SessionResponse{Message: &nodev1.SessionResponse_Broadcast{Broadcast: broadcast}})
	return nil
}

func (r *Router) command(_ context.Context, command *eventsv1.NodeCommand) error {
	var match func(*Session) bool
	switch target := command.GetTarget().(type) {
	case *eventsv1.NodeCommand_All:
		match = (*Session).isNode
	case *eventsv1.NodeCommand_ServerId:
		match = func(s *Session) bool { return s.isNode() && s.Peer.ServerID == target.ServerId }
	case *eventsv1.NodeCommand_GroupId:
		match = func(s *Session) bool { return s.isNode() && s.Peer.GroupID == target.GroupId }
	default:
		return fmt.Errorf("command without a target: %w", errUnroutable)
	}
	r.deliver(r.Sessions.Select(match), deliveryCommand, func(d *nodev1.Delivery) { d.Action = &nodev1.Delivery_Command{Command: command} })
	return nil
}

// transfer reaches the proxy holding the player, or every proxy for a whole server's players.
func (r *Router) transfer(ctx context.Context, transfer *eventsv1.TransferRequested) error {
	var targets []*Session
	switch subject := transfer.GetSubject().(type) {
	case *eventsv1.TransferRequested_PlayerUuid:
		proxy, err := r.playersProxy(ctx, subject.PlayerUuid)
		if err != nil {
			return err
		}
		targets = proxy
	case *eventsv1.TransferRequested_FromServerId:
		targets = r.Sessions.Select(isProxy)
	default:
		return fmt.Errorf("transfer without a subject: %w", errUnroutable)
	}
	r.deliver(targets, deliveryTransfer, func(d *nodev1.Delivery) { d.Action = &nodev1.Delivery_Transfer{Transfer: transfer} })
	return nil
}

func (r *Router) linkCode(ctx context.Context, code *eventsv1.LinkCodeIssued) error {
	proxy, err := r.playersProxy(ctx, code.GetPlayerUuid())
	if err != nil {
		return err
	}
	r.deliver(proxy, deliveryLinkCode, func(d *nodev1.Delivery) { d.Action = &nodev1.Delivery_LinkCode{LinkCode: code} })
	return nil
}

func (r *Router) live(_ context.Context, message *eventsv1.LiveChannelMessage) error {
	targets := r.Sessions.Select(func(s *Session) bool { return s.isNode() && s.live.has(message.GetChannel()) })
	r.send(targets, &nodev1.SessionResponse{Message: &nodev1.SessionResponse_LiveMessage{LiveMessage: &nodev1.LiveChannelDelivery{
		Channel: message.GetChannel(), SenderServerId: message.GetSenderServerId(), SenderNodeType: message.GetSenderNodeType(),
		TypeId: message.GetTypeId(), Payload: message.GetPayload(),
	}}})
	return nil
}

// serverCommand delivers template pushes to the server's agent; the controller runs every other command (D-0081).
func (r *Router) serverCommand(_ context.Context, command *eventsv1.ServerCommand) error {
	push := command.GetPushTemplate()
	if push == nil {
		return nil
	}
	agent, ok := r.Sessions.Get(command.GetServerId(), eventsv1.NodeType_NODE_TYPE_TEMPLATE_AGENT)
	if !ok {
		return nil
	}
	r.deliver([]*Session{agent}, deliveryPushTemplate, func(d *nodev1.Delivery) { d.Action = &nodev1.Delivery_PushTemplate{PushTemplate: push} })
	return nil
}

// playersProxy returns the session of the proxy an online player is connected through, if this replica holds it.
func (r *Router) playersProxy(ctx context.Context, playerUUID string) ([]*Session, error) {
	player, err := r.State.Session(ctx, playerUUID)
	if err != nil {
		return nil, fmt.Errorf("session of %s: %w", playerUUID, err)
	}
	if player == nil {
		return nil, nil
	}
	proxy, ok := r.Sessions.Get(player.GetProxyId(), eventsv1.NodeType_NODE_TYPE_PROXY)
	if !ok {
		return nil, nil
	}
	return []*Session{proxy}, nil
}

// send queues one shared, read-only message for every target.
func (r *Router) send(targets []*Session, message *nodev1.SessionResponse) {
	for _, session := range targets {
		session.Send(message)
	}
}

// deliver sends each target its own delivery, which the node must answer (D-0116).
func (r *Router) deliver(targets []*Session, kind deliveryKind, action func(*nodev1.Delivery)) {
	for _, session := range targets {
		id, err := r.IDs.Next()
		if err != nil {
			r.Logger.Error("no delivery id", "kind", kind, "error", err)
			return
		}
		delivery := &nodev1.Delivery{DeliveryId: id}
		action(delivery)
		session.deliveries.add(pendingDelivery{id: id, kind: kind, sent: r.Now()})
		if !session.Send(&nodev1.SessionResponse{Message: &nodev1.SessionResponse_Delivery{Delivery: delivery}}) {
			session.deliveries.take(id)
			r.Metrics.delivery(kind, outcomeDropped)
		}
	}
}

// ExpireDeliveries counts the deliveries nodes left unanswered for longer than the timeout.
func (r *Router) ExpireDeliveries(now time.Time) {
	for _, session := range r.Sessions.All() {
		for _, delivery := range session.deliveries.expire(now.Add(-r.deliveryTimeout)) {
			r.Metrics.delivery(delivery.kind, outcomeTimeout)
			r.Logger.Warn("delivery unanswered", "serverId", session.Peer.ServerID, "kind", delivery.kind, "deliveryId", delivery.id)
		}
	}
}

// RunExpiry calls ExpireDeliveries every interval until ctx ends.
func (r *Router) RunExpiry(ctx context.Context, interval time.Duration) {
	ticker := time.NewTicker(interval)
	defer ticker.Stop()
	for {
		select {
		case <-ctx.Done():
			return
		case <-ticker.C:
			r.ExpireDeliveries(r.Now())
		}
	}
}

func isProxy(s *Session) bool { return s.NodeType == eventsv1.NodeType_NODE_TYPE_PROXY }

func isBackendOf(s *Session, groupID string) bool {
	return s.NodeType == eventsv1.NodeType_NODE_TYPE_BACKEND && s.Peer.GroupID == groupID
}

// holds is true for the proxy and the backend an online player is on.
func holds(s *Session, player *internalv1.PlayerSession) bool {
	if player == nil {
		return false
	}
	return (isProxy(s) && s.Peer.ServerID == player.GetProxyId()) ||
		(s.NodeType == eventsv1.NodeType_NODE_TYPE_BACKEND && s.Peer.ServerID == player.GetServerId())
}
