package loadbalancer

import (
	"context"
	"io"
	"log/slog"
	"testing"

	"github.com/prometheus/client_golang/prometheus"
	"github.com/prometheus/client_golang/prometheus/testutil"
	"google.golang.org/protobuf/proto"

	eventsv1 "github.com/jevzo/ogcloud/data-plane/pkg/gen/proto/ogcloud/events/v1"
	internalv1 "github.com/jevzo/ogcloud/data-plane/pkg/gen/proto/ogcloud/internalapi/v1"
	"github.com/jevzo/ogcloud/data-plane/pkg/kafkatopics"
	"github.com/jevzo/ogcloud/data-plane/pkg/valkeykeys"
)

type fakeSource struct {
	records []*internalv1.ServerRecord
	network []byte
}

func (f *fakeSource) Servers(context.Context) ([]*internalv1.ServerRecord, error) {
	return f.records, nil
}

func (f *fakeSource) ReadModel(_ context.Context, kind valkeykeys.ReadModelKind, id string) ([]byte, error) {
	if kind != valkeykeys.ReadModelNetworkSettings || id != valkeykeys.NetworkSettingsId {
		return nil, nil
	}
	return f.network, nil
}

func TestTheRefresherFollowsValkeyAndDropsLeavingProxiesAtOnce(t *testing.T) {
	encoded, err := proto.Marshal(network)
	if err != nil {
		t.Fatal(err)
	}
	source := &fakeSource{records: []*internalv1.ServerRecord{proxyRecord("p-1", running, 3, 100), proxyRecord("p-2", running, 4, 100)}, network: encoded}
	pool := NewPool()
	metrics := NewMetrics(prometheus.NewRegistry())
	refresher := NewRefresher(source, pool, metrics, slog.New(slog.NewTextHandler(io.Discard, nil)))

	if err := refresher.Refresh(context.Background()); err != nil {
		t.Fatal(err)
	}
	draining, err := proto.Marshal(&eventsv1.ServerLifecycleChanged{
		ServerId: "p-1", GroupType: eventsv1.GroupType_GROUP_TYPE_PROXY, State: eventsv1.ServerState_SERVER_STATE_DRAINING,
	})
	if err != nil {
		t.Fatal(err)
	}
	refresher.Handle(context.Background(), kafkatopics.ServerLifecycle, draining)

	settings, online := pool.Status()
	if settings.GetMotd() != network.GetMotd() || online != 7 || testutil.ToFloat64(metrics.proxies) != 2 {
		t.Errorf("status %v %d, proxies %v", settings, online, testutil.ToFloat64(metrics.proxies))
	}
	for range 3 {
		if id, _, err := pool.Select(); err != nil || id != "p-2" {
			t.Fatalf("selected %s, %v; the draining proxy must be gone before the next refresh", id, err)
		}
	}
	select {
	case <-refresher.nudges:
	default:
		t.Error("the event did not nudge a refresh")
	}
}
