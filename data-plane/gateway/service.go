package gateway

import (
	"context"
	"errors"
	"fmt"
	"io"
	"log/slog"
	"time"

	"google.golang.org/grpc"
	"google.golang.org/grpc/codes"
	"google.golang.org/grpc/status"
	"google.golang.org/protobuf/proto"

	"github.com/jevzo/ogcloud/data-plane/pkg/events"
	eventsv1 "github.com/jevzo/ogcloud/data-plane/pkg/gen/proto/ogcloud/events/v1"
	internalv1 "github.com/jevzo/ogcloud/data-plane/pkg/gen/proto/ogcloud/internalapi/v1"
	nodev1 "github.com/jevzo/ogcloud/data-plane/pkg/gen/proto/ogcloud/node/v1"
	"github.com/jevzo/ogcloud/data-plane/pkg/ids"
	"github.com/jevzo/ogcloud/data-plane/pkg/runtimeprofiles"
	"github.com/jevzo/ogcloud/data-plane/pkg/valkeykeys"
)

const component = "gateway"

// Reasons a session ends, for metrics.
const (
	endClosed   = "closed"
	endReplaced = "replaced"
	endSlow     = "slow"
	endError    = "error"
)

// State is the live state the gateway serves from (D-0077).
type State interface {
	Server(ctx context.Context, id string) (*internalv1.ServerRecord, error)
	Servers(ctx context.Context) ([]*internalv1.ServerRecord, error)
	ServersOfGroup(ctx context.Context, groupID string) ([]*internalv1.ServerRecord, error)
	Session(ctx context.Context, playerUUID string) (*internalv1.PlayerSession, error)
	SessionByName(ctx context.Context, name string) (*internalv1.PlayerSession, error)
	OnlineSessions(ctx context.Context) ([]*internalv1.PlayerSession, error)
	ReadModel(ctx context.Context, kind valkeykeys.ReadModelKind, id string) ([]byte, error)
}

// Publisher sends events to Kafka.
type Publisher interface {
	Publish(ctx context.Context, topic, key string, event proto.Message) error
}

// LoginResolver is the controller's login service (internal gRPC).
type LoginResolver interface {
	ResolveLogin(ctx context.Context, in *internalv1.ResolveLoginRequest, opts ...grpc.CallOption) (*internalv1.ResolveLoginResponse, error)
}

// TemplateRegistry is the controller's template service (internal gRPC, D-0081).
type TemplateRegistry interface {
	RegisterTemplateVersion(ctx context.Context, in *internalv1.RegisterTemplateVersionRequest,
		opts ...grpc.CallOption) (*internalv1.RegisterTemplateVersionResponse, error)
}

// Presigner signs object-storage URLs, so pods never hold storage credentials (D-0010).
type Presigner interface {
	Get(ctx context.Context, key string) (string, error)
	Put(ctx context.Context, key string) (string, error)
}

// Dependencies are what the gateway's parts share.
type Dependencies struct {
	Sessions  *Registry
	State     State
	Events    Publisher
	Stamper   *events.Stamper
	IDs       *ids.Generator
	Metrics   *Metrics
	Logger    *slog.Logger
	Now       func() time.Time
	Logins    LoginResolver
	Templates TemplateRegistry
	Objects   Presigner
}

// Options tune sessions.
type Options struct {
	// How long a new stream may take to say Hello.
	HelloTimeout time.Duration
	// Messages queued for a node before it counts as too slow.
	QueueSize int
	// How long a node may take to answer a delivery.
	DeliveryTimeout time.Duration
	// How long a template agent may take to answer a push, which archives and uploads the whole server.
	PushTimeout time.Duration
}

func (o Options) validate() error {
	if o.HelloTimeout <= 0 || o.QueueSize <= 0 || o.DeliveryTimeout <= 0 || o.PushTimeout <= 0 {
		return fmt.Errorf("gateway options must be positive: %+v", o)
	}
	return nil
}

// Service implements GatewayService.
type Service struct {
	nodev1.UnimplementedGatewayServiceServer
	Dependencies
	options Options
}

// NewService serves node sessions.
func NewService(dependencies Dependencies, options Options) (*Service, error) {
	if err := options.validate(); err != nil {
		return nil, err
	}
	return &Service{Dependencies: dependencies, options: options}, nil
}

// NewServer serves the gateway's services with every call authenticated (D-0013); opts add e.g. TLS.
func NewServer(auth *Authenticator, service *Service, agents *AgentService, opts ...grpc.ServerOption) *grpc.Server {
	server := grpc.NewServer(append(opts,
		grpc.UnaryInterceptor(auth.UnaryInterceptor()),
		grpc.StreamInterceptor(auth.StreamInterceptor()), //nolint:contextcheck // a stream's handler uses the stream's own context.
	)...)
	nodev1.RegisterGatewayServiceServer(server, service)
	nodev1.RegisterTemplateAgentServiceServer(server, agents)
	return server
}

// Session holds one node's stream: Hello first, then Welcome, then messages both ways.
func (s *Service) Session(stream nodev1.GatewayService_SessionServer) error {
	peer, ok := PeerFrom(stream.Context())
	if !ok {
		return status.Error(codes.Internal, "unauthenticated stream")
	}
	hello, err := s.awaitHello(stream)
	if err == nil {
		err = checkHello(peer, hello)
	}
	if err != nil {
		return s.refuse(peer, err)
	}
	ctx, cancel := context.WithCancelCause(stream.Context())
	defer cancel(nil)
	session := newSession(peer, hello, s.options.QueueSize, cancel)
	// Registered before the state is read, so no broadcast falls between the two; the Welcome still goes out
	// first because nothing drains the queue until it is sent.
	s.Sessions.Add(session)
	if err := s.sendWelcome(ctx, stream, peer); err != nil {
		s.Sessions.Remove(session)
		return s.refuse(peer, err)
	}
	s.Metrics.opened(session)
	logger := s.Logger.With("serverId", peer.ServerID, "nodeType", hello.GetNodeType().String())
	logger.Info("session opened", "integrationVersion", hello.GetIntegrationVersion())

	received := make(chan error, 1)
	go func() { received <- s.receive(ctx, stream, session) }()
	// Sending runs apart from the handler: a node that stops reading blocks a send, and only returning from
	// the handler ends the stream and frees it.
	sent := make(chan error, 1)
	go func() { sent <- forward(ctx, stream, session) }()
	select {
	case err = <-received:
	case err = <-sent:
	case <-ctx.Done():
		err = context.Cause(ctx)
	}
	current := s.Sessions.Remove(session)
	s.Metrics.closed(session, endReason(err), current)
	logger.Info("session closed", "reason", err)
	return closing(err)
}

func (s *Service) sendWelcome(ctx context.Context, stream nodev1.GatewayService_SessionServer, peer Peer) error {
	welcome, err := s.welcome(ctx, peer)
	if err != nil {
		return err
	}
	if err := stream.Send(&nodev1.SessionResponse{Message: &nodev1.SessionResponse_Welcome{Welcome: welcome}}); err != nil {
		return fmt.Errorf("send welcome: %w", err)
	}
	return nil
}

// refuse logs and counts a session that never opened.
func (s *Service) refuse(peer Peer, err error) error {
	refusal := closing(err)
	s.Metrics.refused.WithLabelValues(status.Code(refusal).String()).Inc()
	s.Logger.Warn("session refused", "serverId", peer.ServerID, "error", refusal)
	return refusal
}

// awaitHello reads the first message, which must arrive in time and be a Hello.
func (s *Service) awaitHello(stream nodev1.GatewayService_SessionServer) (*nodev1.Hello, error) {
	first := make(chan *nodev1.SessionRequest, 1)
	failed := make(chan error, 1)
	go func() {
		request, err := stream.Recv()
		if err != nil {
			failed <- err
			return
		}
		first <- request
	}()
	timer := time.NewTimer(s.options.HelloTimeout)
	defer timer.Stop()
	select {
	case request := <-first:
		hello := request.GetHello()
		if hello == nil {
			return nil, status.Error(codes.FailedPrecondition, "the first message must be a Hello")
		}
		return hello, nil
	case err := <-failed:
		return nil, closing(err)
	case <-timer.C:
		return nil, status.Error(codes.DeadlineExceeded, "no Hello in time")
	}
}

// checkHello refuses other protocol versions, and node types the pod's role does not run.
func checkHello(peer Peer, hello *nodev1.Hello) error {
	if hello.GetProtocolVersion() != nodev1.ProtocolVersion_PROTOCOL_VERSION_1 {
		return status.Errorf(codes.FailedPrecondition, "unsupported protocol version %v", hello.GetProtocolVersion())
	}
	nodeType := hello.GetNodeType()
	switch peer.Role {
	case runtimeprofiles.RoleProxy:
		// Proxy pods run the template agent too, so proxy groups push templates like the rest (D-0120).
		if nodeType == eventsv1.NodeType_NODE_TYPE_PROXY || nodeType == eventsv1.NodeType_NODE_TYPE_TEMPLATE_AGENT {
			return nil
		}
	case runtimeprofiles.RoleBackend:
		if nodeType == eventsv1.NodeType_NODE_TYPE_BACKEND || nodeType == eventsv1.NodeType_NODE_TYPE_TEMPLATE_AGENT {
			return nil
		}
	}
	return status.Errorf(codes.PermissionDenied, "a %s pod cannot run a %v node", peer.Role, nodeType)
}

// welcome gives a node its identity and the current state, before the first broadcast arrives.
func (s *Service) welcome(ctx context.Context, peer Peer) (*nodev1.Welcome, error) {
	encoded, err := s.State.ReadModel(ctx, valkeykeys.ReadModelNetworkSettings, valkeykeys.NetworkSettingsId)
	if err != nil {
		return nil, status.Errorf(codes.Unavailable, "network settings: %v", err)
	}
	if encoded == nil {
		return nil, status.Error(codes.Unavailable, "the network settings are not published yet")
	}
	network := &eventsv1.NetworkSettingsUpdated{}
	if err := proto.Unmarshal(encoded, network); err != nil {
		return nil, status.Errorf(codes.Internal, "network settings: %v", err)
	}
	welcome := &nodev1.Welcome{
		ProtocolVersion: nodev1.ProtocolVersion_PROTOCOL_VERSION_1,
		ServerId:        peer.ServerID,
		GroupId:         peer.GroupID,
		GroupType:       peer.GroupType,
		NetworkSettings: network,
	}
	if peer.Role == runtimeprofiles.RoleProxy {
		records, err := s.State.Servers(ctx)
		if err == nil {
			welcome.Servers, err = s.serverInfos(ctx, records)
		}
		if err != nil {
			return nil, status.Errorf(codes.Unavailable, "servers: %v", err)
		}
	}
	return welcome, nil
}

// serverInfos adds each group's maintenance flag to live server records, for proxies to route.
func (s *Service) serverInfos(ctx context.Context, records []*internalv1.ServerRecord) ([]*nodev1.ServerInfo, error) {
	maintenance := map[string]bool{}
	infos := make([]*nodev1.ServerInfo, 0, len(records))
	for _, record := range records {
		inMaintenance, known := maintenance[record.GetGroupId()]
		if !known {
			var err error
			if inMaintenance, err = s.groupMaintenance(ctx, record.GetGroupId()); err != nil {
				return nil, err
			}
			maintenance[record.GetGroupId()] = inMaintenance
		}
		infos = append(infos, &nodev1.ServerInfo{
			ServerId: record.GetServerId(), GroupId: record.GetGroupId(), GroupType: record.GetGroupType(),
			ProfileId: record.GetProfileId(), State: record.GetState(), GameState: record.GetGameState(),
			Host: record.GetHost(), Port: record.GetPort(), Players: record.GetPlayers(), MaxPlayers: record.GetMaxPlayers(),
			GroupMaintenance: inMaintenance, DisplayName: record.GetDisplayName(),
		})
	}
	return infos, nil
}

func (s *Service) groupMaintenance(ctx context.Context, groupID string) (bool, error) {
	encoded, err := s.State.ReadModel(ctx, valkeykeys.ReadModelGroup, groupID)
	if err != nil || encoded == nil {
		return false, err
	}
	group := &eventsv1.GroupUpdated{}
	if err := proto.Unmarshal(encoded, group); err != nil {
		return false, fmt.Errorf("group %s: %w", groupID, err)
	}
	return group.GetMaintenance(), nil
}

// receive handles what the node sends until it closes the stream.
func (s *Service) receive(ctx context.Context, stream nodev1.GatewayService_SessionServer, session *Session) error {
	for {
		request, err := stream.Recv()
		if errors.Is(err, io.EOF) {
			return nil
		}
		if err != nil {
			return fmt.Errorf("receive: %w", err)
		}
		if err := s.handle(ctx, session, request); err != nil {
			return err
		}
	}
}

// forward sends queued messages on the stream until the session ends.
func forward(ctx context.Context, stream nodev1.GatewayService_SessionServer, session *Session) error {
	for {
		select {
		case message := <-session.out:
			if err := stream.Send(message); err != nil {
				return fmt.Errorf("send: %w", err)
			}
		case <-ctx.Done():
			return fmt.Errorf("session ended: %w", context.Cause(ctx))
		}
	}
}

func endReason(err error) string {
	switch {
	case err == nil, errors.Is(err, io.EOF), errors.Is(err, context.Canceled):
		return endClosed
	case errors.Is(err, ErrReplaced):
		return endReplaced
	case errors.Is(err, ErrSlowNode):
		return endSlow
	default:
		return endError
	}
}

// closing turns why a session ended into the status the node sees.
func closing(err error) error {
	switch {
	case err == nil, errors.Is(err, io.EOF):
		return nil
	case errors.Is(err, ErrReplaced):
		return status.Error(codes.Aborted, err.Error())
	case errors.Is(err, ErrSlowNode):
		return status.Error(codes.ResourceExhausted, err.Error())
	case errors.Is(err, context.Canceled):
		return status.Error(codes.Canceled, "the session was cancelled")
	default:
		if _, ok := status.FromError(err); ok {
			return err
		}
		return status.Error(codes.Internal, err.Error())
	}
}
