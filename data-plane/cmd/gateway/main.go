// Command gateway serves the node gateway (D-0008) on its gRPC port, with health and metrics on the admin port.
package main

import (
	"context"
	"crypto/rand"
	"fmt"
	"net"
	"os"
	"time"

	"github.com/twmb/franz-go/pkg/kgo"
	"google.golang.org/grpc"
	"google.golang.org/grpc/credentials"
	corev1 "k8s.io/api/core/v1"

	"github.com/jevzo/ogcloud/data-plane/gateway"
	"github.com/jevzo/ogcloud/data-plane/pkg/config"
	"github.com/jevzo/ogcloud/data-plane/pkg/events"
	internalv1 "github.com/jevzo/ogcloud/data-plane/pkg/gen/proto/ogcloud/internalapi/v1"
	"github.com/jevzo/ogcloud/data-plane/pkg/ids"
	"github.com/jevzo/ogcloud/data-plane/pkg/kafka"
	"github.com/jevzo/ogcloud/data-plane/pkg/kube"
	"github.com/jevzo/ogcloud/data-plane/pkg/objects"
	"github.com/jevzo/ogcloud/data-plane/pkg/service"
	"github.com/jevzo/ogcloud/data-plane/pkg/valkey"
)

const (
	clientID        = "gateway"
	stopTimeout     = 10 * time.Second
	helloTimeout    = 10 * time.Second
	deliveryTimeout = 30 * time.Second
	pushTimeout     = 15 * time.Minute
	expiryInterval  = 5 * time.Second
	presignExpiry   = 15 * time.Minute
	queueSize       = 256
	// Spreads login resolution over every controller replica behind the headless service.
	controllerServiceConfig = `{"loadBalancingConfig":[{"round_robin":{}}]}`
)

// tokenReviewer and podLookup are what the authenticator needs to bind a token to a pod (D-0013).
type (
	tokenReviewer interface {
		Review(ctx context.Context, token string) (kube.Identity, error)
	}
	podLookup interface {
		Pod(name string) (*corev1.Pod, bool)
	}
)

type settings struct {
	listen, namespace, audience string
	security                    security
	controller, controllerToken string
	kafka                       kafka.Settings
	valkey                      valkey.Settings
	objects                     objects.Settings
	presignExpiry               time.Duration
	options                     gateway.Options
}

func main() {
	service.Start(clientID, load, serve)
}

func load(env *config.Source) (settings, error) {
	security, err := loadSecurity(env)
	if err != nil {
		return settings{}, err
	}
	s := settings{
		listen:          env.String("OGCLOUD_GATEWAY_LISTEN", ":9443"),
		namespace:       env.String("OGCLOUD_GAME_NAMESPACE", "ogcloud-servers"),
		audience:        env.String("OGCLOUD_GATEWAY_AUDIENCE", "ogcloud-gateway"),
		controller:      env.Required("OGCLOUD_CONTROLLER_ADDRESS"),
		controllerToken: env.String("OGCLOUD_CONTROLLER_TOKEN_FILE", "/var/run/secrets/ogcloud/controller/token"),
		security:        security,
		kafka:           kafka.FromConfig(env, clientID, security.clientTLS()),
		valkey:          valkey.FromConfig(env, security.clientTLS()),
		objects:         objects.FromConfig(env),
		presignExpiry:   env.Duration("OGCLOUD_S3_PRESIGN_EXPIRY", presignExpiry),
		options: gateway.Options{
			HelloTimeout:    env.Duration("OGCLOUD_GATEWAY_HELLO_TIMEOUT", helloTimeout),
			QueueSize:       env.Int("OGCLOUD_GATEWAY_QUEUE_SIZE", queueSize),
			DeliveryTimeout: env.Duration("OGCLOUD_GATEWAY_DELIVERY_TIMEOUT", deliveryTimeout),
			PushTimeout:     env.Duration("OGCLOUD_GATEWAY_PUSH_TIMEOUT", pushTimeout),
		},
	}
	return s, env.Err()
}

func serve(ctx context.Context, s settings, env service.Env) error {
	logger := env.Logger
	s.kafka.Logger, s.valkey.Logger = logger, logger
	reviewer, pods, err := identities(ctx, s, logger)
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
	serverTLS, err := serverTLS(s.security, logger)
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
	// The gateway's own projected token, for the controller's audience (D-0013).
	controller, err := grpc.NewClient(s.controller,
		grpc.WithTransportCredentials(credentials.NewTLS(s.security.clientTLS())),
		grpc.WithPerRPCCredentials(kube.NewTokenFile(s.controllerToken, os.ReadFile, time.Now)),
		grpc.WithDefaultServiceConfig(controllerServiceConfig),
	)
	if err != nil {
		return fmt.Errorf("controller client: %w", err)
	}
	defer func() { _ = controller.Close() }()

	dependencies := gateway.Dependencies{
		Sessions: gateway.NewRegistry(), State: store, Events: producer,
		Stamper: events.NewStamper(idGenerator, time.Now, clientID), IDs: idGenerator,
		Metrics: gateway.NewMetrics(env.Registry), Logger: logger, Now: time.Now,
		Logins: internalv1.NewLoginServiceClient(controller), Templates: internalv1.NewTemplateServiceClient(controller),
		Objects: objects.NewPresigner(s.objects, s.presignExpiry),
	}
	router, err := gateway.NewRouter(dependencies, s.options)
	if err != nil {
		return err
	}
	service, err := gateway.NewService(dependencies, s.options)
	if err != nil {
		return err
	}
	auth := gateway.NewAuthenticator(reviewer, pods, s.namespace, dependencies.Metrics, logger)
	//nolint:contextcheck // stream handlers use the stream's own context.
	server := gateway.NewServer(auth, service, gateway.NewAgentService(dependencies), grpc.Creds(credentials.NewTLS(serverTLS)))
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
	env.Readiness.Set(true)
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
