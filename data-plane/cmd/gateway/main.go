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

	"github.com/prometheus/client_golang/prometheus"
	"github.com/twmb/franz-go/pkg/kgo"
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
	clientID        = "gateway"
	stopTimeout     = 10 * time.Second
	helloTimeout    = 10 * time.Second
	deliveryTimeout = 30 * time.Second
	expiryInterval  = 5 * time.Second
	queueSize       = 256
)

type settings struct {
	listen, admin, namespace, audience string
	certFile, keyFile                  string
	tracing, logLevel                  string
	kafka                              kafka.Settings
	valkey                             valkey.Settings
	options                            gateway.Options
}

func main() {
	if err := run(); err != nil {
		fmt.Fprintln(os.Stderr, "gateway:", err)
		os.Exit(1)
	}
}

func run() error {
	s, err := load(config.FromEnvironment())
	if err != nil {
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
	registry := telemetry.NewRegistry()
	return service.Run(ctx, admin, registry, func(ctx context.Context, readiness *service.Readiness) error {
		return serve(ctx, s, logger, registry, readiness)
	})
}

func load(env *config.Source) (settings, error) {
	var clientTLS *tls.Config
	if caFile := env.String("OGCLOUD_TLS_CA_FILE", ""); caFile != "" {
		var err error
		if clientTLS, err = tlsfiles.Client(caFile, ""); err != nil {
			return settings{}, err
		}
	}
	s := settings{
		listen:    env.String("OGCLOUD_GATEWAY_LISTEN", ":9443"),
		admin:     env.String("OGCLOUD_ADMIN_LISTEN", ":8081"),
		namespace: env.String("OGCLOUD_GAME_NAMESPACE", "ogcloud-servers"),
		audience:  env.String("OGCLOUD_GATEWAY_AUDIENCE", "ogcloud-gateway"),
		certFile:  env.Required("OGCLOUD_TLS_CERT_FILE"),
		keyFile:   env.Required("OGCLOUD_TLS_KEY_FILE"),
		tracing:   env.String("OTEL_EXPORTER_OTLP_ENDPOINT", ""),
		logLevel:  env.String("OGCLOUD_LOG_LEVEL", "info"),
		kafka:     kafka.FromConfig(env, clientID, clientTLS),
		valkey:    valkey.FromConfig(env, clientTLS),
		options: gateway.Options{
			HelloTimeout:    env.Duration("OGCLOUD_GATEWAY_HELLO_TIMEOUT", helloTimeout),
			QueueSize:       env.Int("OGCLOUD_GATEWAY_QUEUE_SIZE", queueSize),
			DeliveryTimeout: env.Duration("OGCLOUD_GATEWAY_DELIVERY_TIMEOUT", deliveryTimeout),
		},
	}
	return s, env.Err()
}

func serve(ctx context.Context, s settings, logger *slog.Logger, registry prometheus.Registerer, readiness *service.Readiness) error {
	client, err := kube.NewClient(os.Getenv("KUBECONFIG"))
	if err != nil {
		return err
	}
	pods, err := gateway.StartPodCache(ctx, client, s.namespace)
	if err != nil {
		return err
	}
	store, err := valkey.Connect(s.valkey)
	if err != nil {
		return err
	}
	defer store.Close()
	producer, err := kafka.NewProducer(s.kafka)
	if err != nil {
		return err
	}
	defer producer.Close()
	serverTLS, err := tlsfiles.Server(s.certFile, s.keyFile)
	if err != nil {
		return err
	}
	idGenerator := ids.New(time.Now, rand.Reader)
	replica, err := idGenerator.Next()
	if err != nil {
		return err
	}
	broadcast, err := kafka.NewBroadcast(s.kafka, logger, "ogcloud-gateway-"+replica, gateway.Topics...)
	if err != nil {
		return err
	}

	dependencies := gateway.Dependencies{
		Sessions: gateway.NewRegistry(), State: store, Events: producer,
		Stamper: events.NewStamper(idGenerator, time.Now, clientID), IDs: idGenerator,
		Metrics: gateway.NewMetrics(registry), Logger: logger, Now: time.Now,
	}
	router := gateway.NewRouter(dependencies, s.options.DeliveryTimeout)
	auth := gateway.NewAuthenticator(kube.NewReviewer(client, s.audience, time.Now), pods, s.namespace)
	server := grpc.NewServer(
		grpc.Creds(credentials.NewTLS(serverTLS)),
		grpc.UnaryInterceptor(auth.UnaryInterceptor()),
		grpc.StreamInterceptor(auth.StreamInterceptor()), //nolint:contextcheck // a stream's handler uses the stream's own context.
	)
	nodev1.RegisterGatewayServiceServer(server, gateway.NewService(dependencies, s.options))
	listener, err := (&net.ListenConfig{}).Listen(ctx, "tcp", s.listen)
	if err != nil {
		return fmt.Errorf("gateway listener: %w", err)
	}

	ctx, cancel := context.WithCancel(ctx)
	defer cancel()
	consumed := make(chan error, 1)
	go func() {
		consumed <- broadcast.Run(ctx, func(ctx context.Context, record *kgo.Record) { router.Handle(ctx, record.Topic, record.Value) })
	}()
	go router.RunExpiry(ctx, expiryInterval)
	served := make(chan error, 1)
	go func() { served <- server.Serve(listener) }()
	readiness.Set(true)
	logger.Info("serving", "address", s.listen, "replica", replica)

	select {
	case err := <-served:
		return fmt.Errorf("gateway server: %w", err)
	case err := <-consumed:
		stop(server)
		if ctx.Err() != nil {
			return nil
		}
		return fmt.Errorf("kafka broadcast: %w", err)
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
