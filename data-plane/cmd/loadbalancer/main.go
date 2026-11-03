// Command loadbalancer serves Minecraft clients in front of Velocity, with health and metrics on the admin port.
package main

import (
	"context"
	"crypto/rand"
	"fmt"
	"net"
	"net/netip"
	"time"

	"github.com/pires/go-proxyproto"
	"github.com/twmb/franz-go/pkg/kgo"

	"github.com/jevzo/ogcloud/data-plane/loadbalancer"
	"github.com/jevzo/ogcloud/data-plane/pkg/config"
	"github.com/jevzo/ogcloud/data-plane/pkg/ids"
	"github.com/jevzo/ogcloud/data-plane/pkg/kafka"
	"github.com/jevzo/ogcloud/data-plane/pkg/service"
	"github.com/jevzo/ogcloud/data-plane/pkg/tlsfiles"
	"github.com/jevzo/ogcloud/data-plane/pkg/valkey"
)

// Defaults follow D-0118.
const (
	clientID         = "loadbalancer"
	handshakeTimeout = 5 * time.Second
	maxPacketBytes   = 4 << 10
	maxOpenPerIP     = 8
	newPerSecond     = 10
	newBurst         = 20
	dialTimeout      = 3 * time.Second
	refreshInterval  = 2 * time.Second
)

type settings struct {
	listen  string
	trusted []netip.Prefix
	refresh time.Duration
	kafka   kafka.Settings
	valkey  valkey.Settings
	options loadbalancer.Options
}

func main() {
	service.Start(clientID, load, serve)
}

func load(env *config.Source) (settings, error) {
	// Internal TLS everywhere (D-0041).
	clientTLS, err := tlsfiles.Client(env.Required("OGCLOUD_TLS_CA_FILE"), "")
	if err != nil {
		return settings{}, err
	}
	s := settings{
		listen:  env.String("OGCLOUD_LB_LISTEN", ":25565"),
		refresh: env.Duration("OGCLOUD_LB_REFRESH_INTERVAL", refreshInterval),
		kafka:   kafka.FromConfig(env, clientID, clientTLS),
		valkey:  valkey.FromConfig(env, clientTLS),
		options: loadbalancer.Options{
			HandshakeTimeout: env.Duration("OGCLOUD_LB_HANDSHAKE_TIMEOUT", handshakeTimeout),
			MaxPacketBytes:   env.Int("OGCLOUD_LB_MAX_PACKET_BYTES", maxPacketBytes),
			DialTimeout:      env.Duration("OGCLOUD_LB_DIAL_TIMEOUT", dialTimeout),
			Limits: loadbalancer.Limits{
				Open:  env.Int("OGCLOUD_LB_MAX_OPEN_PER_IP", maxOpenPerIP),
				Rate:  float64(env.Int("OGCLOUD_LB_NEW_PER_SECOND", newPerSecond)),
				Burst: float64(env.Int("OGCLOUD_LB_NEW_BURST", newBurst)),
			},
		},
	}
	for _, cidr := range env.List("OGCLOUD_LB_TRUSTED_PROXIES") {
		prefix, err := netip.ParsePrefix(cidr)
		if err != nil {
			return settings{}, fmt.Errorf("OGCLOUD_LB_TRUSTED_PROXIES: %w", err)
		}
		s.trusted = append(s.trusted, prefix)
	}
	return s, env.Err()
}

func serve(ctx context.Context, s settings, env service.Env) error {
	logger := env.Logger
	s.kafka.Logger, s.valkey.Logger = logger, logger
	store, err := valkey.Connect(s.valkey)
	if err != nil {
		return err
	}
	defer store.Close()
	replica, err := ids.New(time.Now, rand.Reader).Next()
	if err != nil {
		return err
	}
	broadcast, err := kafka.NewBroadcast(s.kafka, logger, "ogcloud-loadbalancer-"+replica, loadbalancer.Topics...)
	if err != nil {
		return err
	}
	metrics := loadbalancer.NewMetrics(env.Registry)
	pool := loadbalancer.NewPool()
	refresher := loadbalancer.NewRefresher(store, pool, metrics, logger)
	if err := refresher.Refresh(ctx); err != nil {
		return err
	}
	tcp, err := (&net.ListenConfig{}).Listen(ctx, "tcp", s.listen)
	if err != nil {
		return fmt.Errorf("listener: %w", err)
	}
	listener := &proxyproto.Listener{Listener: tcp, ConnPolicy: loadbalancer.ProxyPolicy(s.trusted), ReadHeaderTimeout: s.options.HandshakeTimeout}

	server, err := loadbalancer.NewServer(pool, s.options, metrics, logger, time.Now)
	if err != nil {
		return err
	}

	ctx, cancel := context.WithCancel(ctx)
	defer cancel()
	go refresher.Run(ctx, s.refresh)
	consumed := make(chan error, 1)
	go func() {
		consumed <- broadcast.Run(ctx, func(ctx context.Context, record *kgo.Record) { refresher.Handle(ctx, record.Topic, record.Value) })
	}()
	served := make(chan error, 1)
	go func() { served <- server.Serve(ctx, listener) }()
	env.Readiness.Set(true)
	logger.Info("serving", "address", s.listen, "replica", replica)

	select {
	case err := <-served:
		return err
	case err := <-consumed:
		if ctx.Err() != nil {
			return nil
		}
		return fmt.Errorf("kafka broadcast: %w", err)
	case <-ctx.Done():
		return nil
	}
}
