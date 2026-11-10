package gateway

import (
	"context"

	"google.golang.org/grpc/codes"
	"google.golang.org/grpc/status"

	internalv1 "github.com/jevzo/ogcloud/data-plane/pkg/gen/proto/ogcloud/internalapi/v1"
	nodev1 "github.com/jevzo/ogcloud/data-plane/pkg/gen/proto/ogcloud/node/v1"
	"github.com/jevzo/ogcloud/data-plane/pkg/runtimeprofiles"
)

// ResolveLogin asks the controller whether a player may join through the calling proxy.
func (s *Service) ResolveLogin(ctx context.Context, request *nodev1.ResolveLoginRequest) (*nodev1.ResolveLoginResponse, error) {
	peer, ok := PeerFrom(ctx)
	if !ok || peer.Role != runtimeprofiles.RoleProxy {
		return nil, status.Error(codes.PermissionDenied, "only proxies resolve logins")
	}
	started := s.Now()
	response, err := s.Logins.ResolveLogin(ctx, &internalv1.ResolveLoginRequest{Login: request, ProxyServerId: peer.ServerID})
	took := s.Now().Sub(started)
	if err != nil {
		s.Metrics.loginResolved(loginFailed, took)
		return nil, controllerError("login resolution", err)
	}
	result := loginRefused
	if response.GetResult().GetAllowed() {
		result = loginAllowed
	}
	s.Metrics.loginResolved(result, took)
	return response.GetResult(), nil
}

// controllerError keeps the controller's status code, so a refused request stays refused and an outage stays
// retryable.
func controllerError(what string, err error) error {
	return status.Errorf(status.Code(err), "%s: %s", what, status.Convert(err).Message())
}
