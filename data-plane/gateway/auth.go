// Package gateway is the node gateway (D-0008): the only endpoint plugins, mods and template agents reach.
// Every call is authenticated by the pod's projected token and bound to the server that pod runs (D-0013).
package gateway

import (
	"context"
	"errors"
	"strings"

	"google.golang.org/grpc"
	"google.golang.org/grpc/codes"
	"google.golang.org/grpc/metadata"
	"google.golang.org/grpc/status"
	corev1 "k8s.io/api/core/v1"

	"github.com/jevzo/ogcloud/data-plane/pkg/kube"
	"github.com/jevzo/ogcloud/data-plane/pkg/podlabels"
	"github.com/jevzo/ogcloud/data-plane/pkg/runtimeprofiles"
)

// Peer is the game server behind an authenticated call.
type Peer struct {
	ServerID string
	GroupID  string
	// Lower case, as on the pod label, e.g. dynamic.
	GroupType string
	// The runtime profile, e.g. paper-26.3, whose bundle the pod loads.
	ProfileID string
	Role      runtimeprofiles.Role
	PodName   string
}

type peerKey struct{}

// PeerFrom returns the authenticated peer of a call.
func PeerFrom(ctx context.Context) (Peer, bool) {
	peer, ok := ctx.Value(peerKey{}).(Peer)
	return peer, ok
}

type reviewer interface {
	Review(ctx context.Context, token string) (kube.Identity, error)
}

type pods interface {
	Pod(name string) (*corev1.Pod, bool)
}

// Authenticator turns a bearer token into the Peer it belongs to.
type Authenticator struct {
	reviewer  reviewer
	pods      pods
	namespace string
}

// NewAuthenticator accepts pod tokens from game pods in namespace (D-0056).
func NewAuthenticator(reviewer reviewer, pods pods, namespace string) *Authenticator {
	return &Authenticator{reviewer: reviewer, pods: pods, namespace: namespace}
}

// Authenticate checks the token with TokenReview and binds it to a live game pod: the pod must exist with
// the token's uid and carry the controller's labels, so a token of a deleted pod is refused too.
func (a *Authenticator) Authenticate(ctx context.Context) (Peer, error) {
	token, err := bearer(ctx)
	if err != nil {
		return Peer{}, err
	}
	identity, err := a.reviewer.Review(ctx, token)
	if errors.Is(err, kube.ErrUnauthenticated) {
		return Peer{}, status.Error(codes.Unauthenticated, "token not accepted")
	}
	if err != nil {
		return Peer{}, status.Errorf(codes.Unavailable, "token review: %v", err)
	}
	if identity.Namespace != a.namespace || identity.PodName == "" {
		return Peer{}, status.Error(codes.PermissionDenied, "not a game pod's token")
	}
	pod, ok := a.pods.Pod(identity.PodName)
	if !ok || string(pod.UID) != identity.PodUID {
		return Peer{}, status.Error(codes.PermissionDenied, "the token's pod is gone")
	}
	labels := pod.Labels
	peer := Peer{
		ServerID:  labels[podlabels.ServerId],
		GroupID:   labels[podlabels.Group],
		GroupType: labels[podlabels.GroupType],
		ProfileID: labels[podlabels.Profile],
		Role:      runtimeprofiles.Role(labels[podlabels.Role]),
		PodName:   pod.Name,
	}
	if labels[podlabels.ManagedBy] != podlabels.ControllerName || peer.ServerID == "" || peer.GroupID == "" ||
		(peer.Role != runtimeprofiles.RoleBackend && peer.Role != runtimeprofiles.RoleProxy) {
		return Peer{}, status.Error(codes.PermissionDenied, "the pod is not a game server")
	}
	return peer, nil
}

// UnaryInterceptor authenticates every unary call.
func (a *Authenticator) UnaryInterceptor() grpc.UnaryServerInterceptor {
	return func(ctx context.Context, request any, _ *grpc.UnaryServerInfo, handler grpc.UnaryHandler) (any, error) {
		peer, err := a.Authenticate(ctx)
		if err != nil {
			return nil, err
		}
		return handler(context.WithValue(ctx, peerKey{}, peer), request)
	}
}

// StreamInterceptor authenticates every stream once, when it opens.
func (a *Authenticator) StreamInterceptor() grpc.StreamServerInterceptor {
	return func(server any, stream grpc.ServerStream, _ *grpc.StreamServerInfo, handler grpc.StreamHandler) error {
		peer, err := a.Authenticate(stream.Context())
		if err != nil {
			return err
		}
		return handler(server, &peerStream{ServerStream: stream, ctx: context.WithValue(stream.Context(), peerKey{}, peer)})
	}
}

type peerStream struct {
	grpc.ServerStream
	ctx context.Context //nolint:containedctx // a stream's context travels with the stream.
}

func (s *peerStream) Context() context.Context { return s.ctx }

func bearer(ctx context.Context) (string, error) {
	values := metadata.ValueFromIncomingContext(ctx, "authorization")
	if len(values) != 1 {
		return "", status.Error(codes.Unauthenticated, "one bearer token is required")
	}
	token, ok := strings.CutPrefix(values[0], "Bearer ")
	if !ok || token == "" {
		return "", status.Error(codes.Unauthenticated, "one bearer token is required")
	}
	return token, nil
}
