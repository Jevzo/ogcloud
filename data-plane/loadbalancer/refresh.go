package loadbalancer

import (
	"context"
	"fmt"
	"log/slog"
	"time"

	"google.golang.org/protobuf/proto"

	eventsv1 "github.com/jevzo/ogcloud/data-plane/pkg/gen/proto/ogcloud/events/v1"
	internalv1 "github.com/jevzo/ogcloud/data-plane/pkg/gen/proto/ogcloud/internalapi/v1"
	"github.com/jevzo/ogcloud/data-plane/pkg/kafkatopics"
	"github.com/jevzo/ogcloud/data-plane/pkg/valkeykeys"
)

// Topics are read with a consumer group per replica: a proxy leaving RUNNING is dropped at once, everything
// else nudges a refresh.
var Topics = []string{kafkatopics.ServerLifecycle, kafkatopics.NodeBroadcast}

// Source is the controller's live state in Valkey.
type Source interface {
	Servers(ctx context.Context) ([]*internalv1.ServerRecord, error)
	ReadModel(ctx context.Context, kind valkeykeys.ReadModelKind, id string) ([]byte, error)
}

// Refresher keeps the pool in step with Valkey: periodically, and soon after a lifecycle or network change.
type Refresher struct {
	source  Source
	pool    *Pool
	metrics *Metrics
	logger  *slog.Logger
	nudges  chan struct{}
}

// NewRefresher refreshes pool from source.
func NewRefresher(source Source, pool *Pool, metrics *Metrics, logger *slog.Logger) *Refresher {
	return &Refresher{source: source, pool: pool, metrics: metrics, logger: logger, nudges: make(chan struct{}, 1)}
}

// Refresh reads every server record and the network settings once.
func (r *Refresher) Refresh(ctx context.Context) error {
	since := r.pool.Generation()
	records, err := r.source.Servers(ctx)
	if err != nil {
		return fmt.Errorf("servers: %w", err)
	}
	encoded, err := r.source.ReadModel(ctx, valkeykeys.ReadModelNetworkSettings, valkeykeys.NetworkSettingsId)
	if err != nil {
		return fmt.Errorf("network settings: %w", err)
	}
	var network *eventsv1.NetworkSettingsUpdated
	if encoded != nil {
		network = &eventsv1.NetworkSettingsUpdated{}
		if err := proto.Unmarshal(encoded, network); err != nil {
			return fmt.Errorf("network settings: %w", err)
		}
	}
	r.pool.Update(since, records, network)
	r.metrics.proxies.Set(float64(r.pool.size()))
	return nil
}

// Run refreshes every interval and on nudges until ctx ends; failures keep the last state.
func (r *Refresher) Run(ctx context.Context, interval time.Duration) {
	ticker := time.NewTicker(interval)
	defer ticker.Stop()
	for {
		if err := r.Refresh(ctx); err != nil && ctx.Err() == nil {
			r.logger.Warn("refresh failed", "error", err)
		}
		select {
		case <-ctx.Done():
			return
		case <-ticker.C:
		case <-r.nudges:
		}
	}
}

// Handle reacts to a record: a proxy leaving RUNNING is dropped at once, everything else nudges a refresh.
func (r *Refresher) Handle(_ context.Context, topic string, value []byte) {
	if topic == kafkatopics.ServerLifecycle {
		event := &eventsv1.ServerLifecycleChanged{}
		if err := proto.Unmarshal(value, event); err != nil {
			r.logger.Warn("lifecycle event not decoded", "error", err)
		} else if event.GetGroupType() == eventsv1.GroupType_GROUP_TYPE_PROXY && event.GetState() != eventsv1.ServerState_SERVER_STATE_RUNNING {
			r.pool.Remove(event.GetServerId())
		}
	}
	select {
	case r.nudges <- struct{}{}:
	default:
	}
}
