// Package service runs a component until it is told to stop: SIGTERM handling, an admin HTTP server with
// liveness, readiness and metrics, and an orderly shutdown.
package service

import (
	"context"
	"errors"
	"fmt"
	"net"
	"net/http"
	"os"
	"os/signal"
	"sync/atomic"
	"syscall"
	"time"

	"github.com/prometheus/client_golang/prometheus"
	"github.com/prometheus/client_golang/prometheus/promhttp"
)

const (
	shutdownTimeout   = 10 * time.Second
	readHeaderTimeout = 5 * time.Second
)

// Readiness is set once the component can serve, and cleared while it stops.
type Readiness struct {
	ready atomic.Bool
}

// Set marks the component ready or not.
func (r *Readiness) Set(ready bool) { r.ready.Store(ready) }

// AdminHandler serves /healthz/live, /healthz/ready and /metrics.
func AdminHandler(registry *prometheus.Registry, readiness *Readiness) http.Handler {
	mux := http.NewServeMux()
	mux.HandleFunc("GET /healthz/live", func(w http.ResponseWriter, _ *http.Request) {
		w.WriteHeader(http.StatusOK)
	})
	mux.HandleFunc("GET /healthz/ready", func(w http.ResponseWriter, _ *http.Request) {
		if readiness.ready.Load() {
			w.WriteHeader(http.StatusOK)
			return
		}
		w.WriteHeader(http.StatusServiceUnavailable)
	})
	mux.Handle("GET /metrics", promhttp.HandlerFor(registry, promhttp.HandlerOpts{}))
	return mux
}

// Run serves the admin endpoints on admin and runs work until SIGTERM or work returns. Work should return nil
// once its context ends; an admin server failure cancels it.
func Run(
	ctx context.Context,
	admin net.Listener,
	registry *prometheus.Registry,
	work func(ctx context.Context, readiness *Readiness) error,
) error {
	ctx, stop := signal.NotifyContext(ctx, syscall.SIGTERM, os.Interrupt)
	defer stop()
	ctx, cancel := context.WithCancelCause(ctx)
	defer cancel(nil)

	readiness := &Readiness{}
	server := &http.Server{Handler: AdminHandler(registry, readiness), ReadHeaderTimeout: readHeaderTimeout}
	go func() {
		if err := server.Serve(admin); err != nil && !errors.Is(err, http.ErrServerClosed) {
			cancel(fmt.Errorf("admin server: %w", err))
		}
	}()

	workErr := work(ctx, readiness)
	readiness.Set(false)
	shutdownCtx, done := context.WithTimeout(context.WithoutCancel(ctx), shutdownTimeout)
	defer done()
	if err := server.Shutdown(shutdownCtx); err != nil {
		workErr = errors.Join(workErr, fmt.Errorf("stop admin server: %w", err))
	}
	if cause := context.Cause(ctx); cause != nil && !errors.Is(cause, context.Canceled) {
		workErr = errors.Join(workErr, cause)
	}
	return workErr
}
