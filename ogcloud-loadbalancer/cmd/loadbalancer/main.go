package main

import (
	"context"
	"fmt"
	"os/signal"
	"syscall"
	"time"

	"go.uber.org/zap"

	"github.com/ogwars/ogcloud-loadbalancer/internal/api"
	"github.com/ogwars/ogcloud-loadbalancer/internal/config"
	"github.com/ogwars/ogcloud-loadbalancer/internal/kafka"
	"github.com/ogwars/ogcloud-loadbalancer/internal/proxy"
)

func main() {
	logger, _ := zap.NewProduction()
	defer logger.Sync()

	cfg := config.Load()
	ctx, cancel := signal.NotifyContext(context.Background(), syscall.SIGTERM, syscall.SIGINT)
	defer cancel()

	networkState := kafka.NewNetworkState()

	settings, err := waitForInitialNetworkSettings(ctx, cfg, logger)
	if err != nil {
		logger.Fatal("failed to initialize from API before startup", zap.Error(err))
	}
	networkState.ApplyAPISettings(*settings)
	logger.Info("loaded network settings from API",
		zap.Bool("maintenance", settings.Maintenance),
		zap.Int("maxPlayers", settings.MaxPlayers),
		zap.String("proxyRoutingStrategy", networkState.GetProxyRoutingStrategy()),
	)

	pool := proxy.NewPool(time.Duration(cfg.ProxyHealthTimeout)*time.Second, logger)

	consumer, err := kafka.NewConsumer(cfg.Kafka.Brokers, cfg.Kafka.GroupID, networkState, pool, logger)
	if err != nil {
		logger.Fatal("failed to create kafka consumer", zap.Error(err))
	}

	consumer.Start(ctx)
	logger.Info("kafka consumer started")

	go func() {
		ticker := time.NewTicker(10 * time.Second)
		defer ticker.Stop()
		for {
			select {
			case <-ticker.C:
				pool.RemoveStale()
			case <-ctx.Done():
				return
			}
		}
	}()

	handler := proxy.NewHandler(pool, networkState, cfg.ProxyProtocol, logger)
	drainTimeout := time.Duration(cfg.DrainTimeoutSeconds) * time.Second
	server := proxy.NewServer(handler, drainTimeout, logger)

	go func() {
		if err := server.Start(cfg.ListenAddr); err != nil {
			logger.Error("tcp server stopped", zap.Error(err))
			cancel()
		}
	}()

	<-ctx.Done()
	logger.Info("shutting down")

	server.Stop()
	if err := consumer.Close(); err != nil {
		logger.Error("failed to close kafka consumer", zap.Error(err))
	}

	logger.Info("shutdown complete")
}

func waitForInitialNetworkSettings(
	ctx context.Context,
	cfg *config.Config,
	logger *zap.Logger,
) (*kafka.APINetworkResponse, error) {
	startupTimeout := time.Duration(cfg.ApiStartupTimeoutSeconds) * time.Second
	retryInterval := time.Duration(cfg.ApiStartupPollIntervalSeconds) * time.Second
	if startupTimeout <= 0 {
		startupTimeout = 5 * time.Minute
	}
	if retryInterval <= 0 {
		retryInterval = 5 * time.Second
	}

	deadline := time.Now().Add(startupTimeout)
	attempt := 1
	var lastErr error

	for {
		settings, err := api.FetchNetworkSettings(cfg.ApiURL, cfg.ApiEmail, cfg.ApiPassword)
		if err == nil {
			return settings, nil
		}

		lastErr = err
		remaining := time.Until(deadline)
		if remaining <= 0 {
			return nil, fmt.Errorf("API not reachable after %s: %w", startupTimeout, lastErr)
		}

		sleepDuration := retryInterval
		if sleepDuration > remaining {
			sleepDuration = remaining
		}

		logger.Warn(
			"API not ready yet, waiting before startup",
			zap.Error(err),
			zap.Int("attempt", attempt),
			zap.Duration("retryIn", sleepDuration),
			zap.Duration("timeRemaining", remaining),
		)

		timer := time.NewTimer(sleepDuration)
		select {
		case <-ctx.Done():
			if !timer.Stop() {
				<-timer.C
			}
			return nil, ctx.Err()
		case <-timer.C:
		}

		attempt++
	}
}
