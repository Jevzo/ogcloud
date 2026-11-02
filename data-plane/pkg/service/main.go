package service

import (
	"context"
	"fmt"
	"log/slog"
	"net"
	"os"

	"github.com/prometheus/client_golang/prometheus"

	"github.com/jevzo/ogcloud/data-plane/pkg/config"
	"github.com/jevzo/ogcloud/data-plane/pkg/logging"
	"github.com/jevzo/ogcloud/data-plane/pkg/telemetry"
)

// Env is what Start hands a service's body.
type Env struct {
	Logger    *slog.Logger
	Registry  *prometheus.Registry
	Readiness *Readiness
}

// Start loads a service's settings from the environment, sets up JSON logs, traces and the admin port, and
// runs serve as Run does. It exits the process when anything fails.
func Start[S any](component string, load func(*config.Source) (S, error), serve func(context.Context, S, Env) error) {
	if err := start(component, config.FromEnvironment(), load, serve); err != nil {
		fmt.Fprintf(os.Stderr, "%s: %v\n", component, err)
		os.Exit(1)
	}
}

func start[S any](component string, env *config.Source, load func(*config.Source) (S, error), serve func(context.Context, S, Env) error) error {
	admin := env.String("OGCLOUD_ADMIN_LISTEN", ":8081")
	tracing := env.String("OTEL_EXPORTER_OTLP_ENDPOINT", "")
	logLevel := env.String("OGCLOUD_LOG_LEVEL", "info")
	settings, err := load(env)
	if err != nil {
		return err
	}
	logger, err := logging.New(os.Stdout, component, logLevel)
	if err != nil {
		return err
	}
	ctx := context.Background()
	stopTracing, err := telemetry.StartTracing(ctx, component, tracing)
	if err != nil {
		return err
	}
	defer func() { _ = stopTracing(context.WithoutCancel(ctx)) }()
	listener, err := (&net.ListenConfig{}).Listen(ctx, "tcp", admin)
	if err != nil {
		return fmt.Errorf("admin listener: %w", err)
	}
	registry := telemetry.NewRegistry()
	return Run(ctx, listener, registry, func(ctx context.Context, readiness *Readiness) error {
		return serve(ctx, settings, Env{Logger: logger, Registry: registry, Readiness: readiness})
	})
}
