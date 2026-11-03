//go:build !contracttest

package main

import (
	"context"
	"crypto/tls"
	"log/slog"
	"os"
	"time"

	"github.com/jevzo/ogcloud/data-plane/gateway"
	"github.com/jevzo/ogcloud/data-plane/pkg/config"
	"github.com/jevzo/ogcloud/data-plane/pkg/kube"
	"github.com/jevzo/ogcloud/data-plane/pkg/tlsfiles"
)

// security is what proves who is talking: internal TLS from the private CA (D-0041) and pod tokens (D-0013).
// contract.go replaces this file in the contract-test build.
type security struct {
	client            *tls.Config
	certFile, keyFile string
}

func loadSecurity(env *config.Source) (security, error) {
	// The controller, Kafka and Valkey present certificates from the private CA.
	client, err := tlsfiles.Client(env.Required("OGCLOUD_TLS_CA_FILE"), "")
	if err != nil {
		return security{}, err
	}
	return security{client: client, certFile: env.Required("OGCLOUD_TLS_CERT_FILE"), keyFile: env.Required("OGCLOUD_TLS_KEY_FILE")}, nil
}

func (s security) clientTLS() *tls.Config { return s.client }

func serverTLS(s security, logger *slog.Logger) (*tls.Config, error) {
	return tlsfiles.Server(s.certFile, s.keyFile, logger)
}

// identities checks tokens with TokenReview and finds their pods in the game namespace's pod cache.
func identities(ctx context.Context, s settings, _ *slog.Logger) (tokenReviewer, podLookup, error) {
	client, err := kube.NewClient(os.Getenv("KUBECONFIG"))
	if err != nil {
		return nil, nil, err
	}
	pods, err := gateway.StartPodCache(ctx, client, s.namespace)
	if err != nil {
		return nil, nil, err
	}
	return kube.NewReviewer(client, s.audience, time.Now), pods, nil
}
