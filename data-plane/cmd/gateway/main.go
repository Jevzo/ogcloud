// Command gateway serves the node gateway (D-0008) on its gRPC port, with health and metrics on the admin port.
package main

import (
	"context"
	"crypto/rand"
	"crypto/tls"
	"fmt"
	"log/slog"
	"net"
	"os"
	"time"

	"google.golang.org/grpc"
	"google.golang.org/grpc/credentials"

	"github.com/jevzo/ogcloud/data-plane/gateway"
	"github.com/jevzo/ogcloud/data-plane/pkg/config"
	"github.com/jevzo/ogcloud/data-plane/pkg/events"
	nodev1 "github.com/jevzo/ogcloud/data-plane/pkg/gen/proto/ogcloud/node/v1"
	"github.com/jevzo/ogcloud/data-plane/pkg/ids"
	"github.com/jevzo/ogcloud/data-plane/pkg/kafka"
	"github.com/jevzo/ogcloud/data-plane/pkg/kube"
	"github.com/jevzo/ogcloud/data-plane/pkg/logging"
	"github.com/jevzo/ogcloud/data-plane/pkg/service"
	"github.com/jevzo/ogcloud/data-plane/pkg/telemetry"
	"github.com/jevzo/ogcloud/data-plane/pkg/tlsfiles"
	"github.com/jevzo/ogcloud/data-plane/pkg/valkey"
)

const (
	clientID     = "gateway"
	stopTimeout  = 10 * time.Second
	helloTimeout = 10 * time.Second
	queueSize    = 256
)

type settings struct {
	listen, admin, namespace, audience string
	certFile, keyFile, caFile          string
	tracing, logLevel                  string
	options                            gateway.Options
}

func main() {
	if err := run(); err != nil {
		fmt.Fprintln(os.Stderr, "gateway:", err)
		os.Exit(1)
	}
}

func run() error {
	env := config.FromEnvironment()
	s := settings{
		listen:    env.String("OGCLOUD_GATEWAY_LISTEN", ":9443"),
		admin:     env.String("OGCLOUD_ADMIN_LISTEN", ":8081"),
		namespace: env.String("OGCLOUD_GAME_NAMESPACE", "ogcloud-servers"),
		audience:  env.String("OGCLOUD_GATEWAY_AUDIENCE", "ogcloud-gateway"),
		certFile:  env.Required("OGCLOUD_TLS_CERT_FILE"),
		keyFile:   env.Required("OGCLOUD_TLS_KEY_FILE"),
		caFile:    env.String("OGCLOUD_TLS_CA_FILE", ""),
		tracing:   env.String("OTEL_EXPORTER_OTLP_ENDPOINT", ""),
		logLevel:  env.String("OGCLOUD_LOG_LEVEL", "info"),
		options: gateway.Options{
			HelloTimeout: env.Duration("OGCLOUD_GATEWAY_HELLO_TIMEOUT", helloTimeout),
			QueueSize:    env.Int("OGCLOUD_GATEWAY_QUEUE_SIZE", queueSize),
		},
	}
	var clientTLS *tls.Config
	if s.caFile != "" {
		var err error
		if clientTLS, err = tlsfiles.Client(s.caFile, ""); err != nil {
			return err
		}
	}
	kafkaSettings := kafka.FromConfig(env, clientID, clientTLS)
	valkeySettings := valkey.FromConfig(env, clientTLS)
	if err := env.Err(); err != nil {
		return err
	}
	logger, err := logging.New(os.Stdout, clientID, s.logLevel)
	if err != nil {
		return err
	}
	ctx := context.Background()
	stopTracing, err := telemetry.StartTracing(ctx, clientID, s.tracing)
	if err != nil {
		return err
	}
	defer func() { _ = stopTracing(context.WithoutCancel(ctx)) }()
	admin, err := (&net.ListenConfig{}).Listen(ctx, "tcp", s.admin)
	if err != nil {
		return fmt.Errorf("admin listener: %w", err)
	}
	return service.Run(ctx, admin, telemetry.NewRegistry(), func(ctx context.Context, readiness *service.Readiness) error {
		return serve(ctx, s, kafkaSettings, valkeySettings, logger, readiness)
	})
}

func serve(ctx context.Context, s settings, kafkaSettings kafka.Settings, valkeySettings valkey.Settings, logger *slog.Logger, readiness *service.Readiness) error {
	client, err := kube.NewClient(os.Getenv("KUBECONFIG"))
	if err != nil {
		return err
	}
	pods, err := gateway.StartPodCache(ctx, client, s.namespace)
	if err != nil {
		return err
	}
	store, err := valkey.Connect(valkeySettings)
	if err != nil {
		return err
	}
	defer store.Close()
	producer, err := kafka.NewProducer(kafkaSettings)
	if err != nil {
		return err
	}
	defer producer.Close()
	serverTLS, err := tlsfiles.Server(s.certFile, s.keyFile)
	if err != nil {
		return err
	}

	auth := gateway.NewAuthenticator(kube.NewReviewer(client, s.audience, time.Now), pods, s.namespace)
	stamper := events.NewStamper(ids.New(time.Now, rand.Reader), time.Now, clientID)
	server := grpc.NewServer(
		grpc.Creds(credentials.NewTLS(serverTLS)),
		grpc.UnaryInterceptor(auth.UnaryInterceptor()),
		grpc.StreamInterceptor(auth.StreamInterceptor()), //nolint:contextcheck // a stream's handler uses the stream's own context.
	)
	nodev1.RegisterGatewayServiceServer(server, gateway.NewService(gateway.NewRegistry(), store, producer, stamper, logger, s.options))
	listener, err := (&net.ListenConfig{}).Listen(ctx, "tcp", s.listen)
	if err != nil {
		return fmt.Errorf("gateway listener: %w", err)
	}
	served := make(chan error, 1)
	go func() { served <- server.Serve(listener) }()
	readiness.Set(true)
	logger.Info("serving", "address", s.listen)

	select {
	case err := <-served:
		return fmt.Errorf("gateway server: %w", err)
	case <-ctx.Done():
		stop(server)
		return nil
	}
}

// stop lets sessions end on their own for a while, then cuts them; nodes reconnect to another replica.
func stop(server *grpc.Server) {
	stopped := make(chan struct{})
	go func() {
		server.GracefulStop()
		close(stopped)
	}()
	select {
	case <-stopped:
	case <-time.After(stopTimeout):
		server.Stop()
	}
}
