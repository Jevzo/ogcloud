package loadbalancer

import (
	"net/netip"
	"slices"
	"testing"
	"time"

	eventsv1 "github.com/jevzo/ogcloud/data-plane/pkg/gen/proto/ogcloud/events/v1"
	internalv1 "github.com/jevzo/ogcloud/data-plane/pkg/gen/proto/ogcloud/internalapi/v1"
)

func proxyRecord(id string, state eventsv1.ServerState, players, maxPlayers uint32) *internalv1.ServerRecord {
	return &internalv1.ServerRecord{
		ServerId: id, GroupId: "proxy", GroupType: eventsv1.GroupType_GROUP_TYPE_PROXY, State: state,
		Host: "10.0.0." + id[len(id)-1:], Port: 25577, Players: players, MaxPlayers: maxPlayers,
	}
}

const running = eventsv1.ServerState_SERVER_STATE_RUNNING

func TestADrainingProxyNeverTakesLogins(t *testing.T) {
	// v1 bug 1: heartbeats of a draining proxy put it back into the pool.
	pool := NewPool()
	pool.Update(0, []*internalv1.ServerRecord{
		proxyRecord("p-1", eventsv1.ServerState_SERVER_STATE_DRAINING, 0, 100),
		proxyRecord("p-2", running, 90, 100),
	}, network)

	for range 5 {
		if id, _, err := pool.Select(); err != nil || id != "p-2" {
			t.Fatalf("selected %s, %v", id, err)
		}
	}
	if _, online := pool.Status(); online != 90 {
		t.Errorf("online = %d; draining proxies' players still count", online)
	}
}

func TestLoginsGoToTheLeastFilledProxyAndSkipFullOnes(t *testing.T) {
	pool := NewPool()
	pool.Update(0, []*internalv1.ServerRecord{
		proxyRecord("p-1", running, 50, 100), proxyRecord("p-2", running, 10, 20), proxyRecord("p-3", running, 20, 20),
		{ServerId: "lobby-1", GroupType: eventsv1.GroupType_GROUP_TYPE_DYNAMIC, State: running, Host: "10.0.1.1", Port: 25565, Players: 9},
	}, network)

	var picks []string
	for range 4 {
		id, _, err := pool.Select()
		if err != nil {
			t.Fatal(err)
		}
		picks = append(picks, id)
	}

	// Both open proxies start half full; each pending login raises p-2's share five times as much as p-1's.
	if want := []string{"p-1", "p-2", "p-1", "p-1"}; !slices.Equal(picks, want) {
		t.Errorf("picks %v, want %v", picks, want)
	}
}

func TestRoundRobinAndNoProxy(t *testing.T) {
	pool := NewPool()
	if _, _, err := pool.Select(); err == nil {
		t.Error("an empty pool selected a proxy")
	}
	pool.Update(0, []*internalv1.ServerRecord{proxyRecord("p-1", running, 0, 100), proxyRecord("p-2", running, 0, 100)},
		&eventsv1.NetworkSettingsUpdated{RoutingStrategy: eventsv1.RoutingStrategy_ROUTING_STRATEGY_ROUND_ROBIN})

	first, _, _ := pool.Select()
	second, address, _ := pool.Select()
	pool.Remove(first)
	third, _, _ := pool.Select()

	if first == second || address != "10.0.0.2:25577" && address != "10.0.0.1:25577" || third == first {
		t.Errorf("picks %s %s %s (%s)", first, second, third, address)
	}
}

func TestClientsAreLimitedPerAddress(t *testing.T) {
	now := time.Date(2026, 11, 2, 12, 0, 0, 0, time.UTC)
	limits := newLimiter(Limits{Open: 2, Rate: 1, Burst: 3})
	addr, other := netip.MustParseAddr("203.0.113.7"), netip.MustParseAddr("203.0.113.8")

	release, ok := limits.acquire(addr, now)
	_, second := limits.acquire(addr, now)
	_, third := limits.acquire(addr, now)
	if !ok || !second || third {
		t.Fatalf("open limit: %v %v %v", ok, second, third)
	}
	release()
	release()
	if _, ok := limits.acquire(addr, now); !ok {
		t.Fatal("a released connection still counts")
	}
	if _, ok := limits.acquire(addr, now); ok {
		t.Fatal("the burst of 3 was exceeded")
	}
	releaseOther, ok := limits.acquire(other, now)
	if !ok {
		t.Fatal("another address was limited")
	}
	releaseOther()

	limits.sweep(now.Add(10 * time.Second))
	if len(limits.byAddr) != 1 {
		t.Errorf("tracked %d addresses; only the one with open connections should stay", len(limits.byAddr))
	}
}

func TestARefreshReadBeforeARemovalCannotBringTheProxyBack(t *testing.T) {
	pool := NewPool()
	records := []*internalv1.ServerRecord{proxyRecord("p-1", running, 0, 100), proxyRecord("p-2", running, 0, 100)}
	pool.Update(pool.Generation(), records, network)
	staleRead := pool.Generation()

	// p-1 starts draining after the refresh read Valkey, but before it applied what it read.
	pool.Remove("p-1")
	pool.Update(staleRead, records, network)

	for range 3 {
		if id, _, err := pool.Select(); err != nil || id != "p-2" {
			t.Fatalf("selected %s, %v", id, err)
		}
	}
	// A later read shows the drain itself, so the removal need not be remembered any longer.
	pool.Update(pool.Generation(), []*internalv1.ServerRecord{proxyRecord("p-1", eventsv1.ServerState_SERVER_STATE_DRAINING, 0, 100), records[1]}, network)
	if _, ok := pool.removed["p-1"]; ok {
		t.Error("the removal outlived a read that already reflects it")
	}
}
