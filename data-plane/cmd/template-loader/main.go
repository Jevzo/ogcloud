// Command template-loader runs in every game pod: `load` fills the data directory before the server starts
// (init container), `agent` pushes it back as a template version (native sidecar).
package main

import (
	"context"
	"errors"
	"fmt"
	"log/slog"
	"net/http"
	"os"
	"os/signal"
	"syscall"
	"time"

	"google.golang.org/grpc"
	"google.golang.org/grpc/credentials"

	"github.com/jevzo/ogcloud/data-plane/pkg/config"
	nodev1 "github.com/jevzo/ogcloud/data-plane/pkg/gen/proto/ogcloud/node/v1"
	"github.com/jevzo/ogcloud/data-plane/pkg/kube"
	"github.com/jevzo/ogcloud/data-plane/pkg/logging"
	"github.com/jevzo/ogcloud/data-plane/pkg/runtimeprofiles"
	"github.com/jevzo/ogcloud/data-plane/pkg/tlsfiles"
	templateloader "github.com/jevzo/ogcloud/data-plane/template-loader"
)

const (
	component   = "template-loader"
	loadTimeout = 10 * time.Minute
	// Bounded by the pod's 90 s grace period.
	pushTimeout     = 80 * time.Second
	maxExtractBytes = 64 << 30
)

// version is set at build time, e.g. -ldflags "-X main.version=2.0.0".
var version = "dev"

func main() {
	if err := run(os.Args[1:]); err != nil {
		fmt.Fprintln(os.Stderr, component+":", err)
		os.Exit(1)
	}
}

func run(args []string) error {
	if len(args) != 1 || args[0] != "load" && args[0] != "agent" {
		return errors.New("usage: template-loader <load|agent>")
	}
	env := config.FromEnvironment()
	address := env.Required("OGCLOUD_GATEWAY_ADDRESS")
	tokenFile := env.Required("OGCLOUD_GATEWAY_TOKEN_FILE")
	caFile := env.Required("OGCLOUD_GATEWAY_CA_FILE")
	dataDir := env.String("OGCLOUD_DATA_DIR", "/data")
	logLevel := env.String("OGCLOUD_LOG_LEVEL", "info")
	profileID := env.String("OGCLOUD_PROFILE_ID", "")
	pushOnShutdown := env.Bool("OGCLOUD_PUSH_ON_SHUTDOWN", false)
	timeouts := struct{ load, push time.Duration }{
		env.Duration("OGCLOUD_LOAD_TIMEOUT", loadTimeout), env.Duration("OGCLOUD_PUSH_TIMEOUT", pushTimeout),
	}
	if err := env.Err(); err != nil {
		return err
	}
	logger, err := logging.New(os.Stdout, component, logLevel)
	if err != nil {
		return err
	}
	gateway, err := dial(address, caFile, tokenFile)
	if err != nil {
		return err
	}
	defer func() { _ = gateway.Close() }()
	data, err := os.OpenRoot(dataDir)
	if err != nil {
		return fmt.Errorf("data directory: %w", err)
	}
	defer func() { _ = data.Close() }()
	ctx, stop := signal.NotifyContext(context.Background(), syscall.SIGTERM, os.Interrupt)
	defer stop()

	if args[0] == "load" {
		profile, ok := runtimeprofiles.Find(profileID)
		if !ok {
			return fmt.Errorf("OGCLOUD_PROFILE_ID: unknown profile %q", profileID)
		}
		ctx, cancel := context.WithTimeout(ctx, timeouts.load)
		defer cancel()
		loader := &templateloader.Loader{
			Assets: nodev1.NewTemplateAgentServiceClient(gateway), HTTP: http.DefaultClient, Data: data, Profile: profile,
			MaxExtractBytes: maxExtractBytes, Logger: logger,
		}
		return loader.Run(ctx)
	}
	agent := &templateloader.Agent{
		Gateway: nodev1.NewGatewayServiceClient(gateway), Uploads: nodev1.NewTemplateAgentServiceClient(gateway),
		HTTP: http.DefaultClient, Data: data, TmpDir: os.TempDir(), PushOnShutdown: pushOnShutdown,
		ShutdownTimeout: timeouts.push, Version: version, Logger: logger.With(slog.Bool("pushOnShutdown", pushOnShutdown)),
	}
	return agent.Run(ctx)
}

// dial reaches the gateway over TLS from the internal CA with the pod's projected token (D-0013, D-0041).
func dial(address, caFile, tokenFile string) (*grpc.ClientConn, error) {
	clientTLS, err := tlsfiles.Client(caFile, "")
	if err != nil {
		return nil, err
	}
	conn, err := grpc.NewClient(address,
		grpc.WithTransportCredentials(credentials.NewTLS(clientTLS)),
		grpc.WithPerRPCCredentials(kube.NewTokenFile(tokenFile, os.ReadFile, time.Now)),
	)
	if err != nil {
		return nil, fmt.Errorf("gateway client: %w", err)
	}
	return conn, nil
}
