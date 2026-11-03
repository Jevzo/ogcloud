package loadbalancer

import (
	"errors"
	"net"
	"strconv"
	"sync"

	eventsv1 "github.com/jevzo/ogcloud/data-plane/pkg/gen/proto/ogcloud/events/v1"
	internalv1 "github.com/jevzo/ogcloud/data-plane/pkg/gen/proto/ogcloud/internalapi/v1"
)

var errNoProxy = errors.New("no running proxy with room")

type proxy struct {
	id      string
	address string
	players uint32
	max     uint32
	// Logins sent since the record was read, so a burst does not all land on one proxy.
	pending uint32
}

// fill is the share of the proxy in use; a proxy that has not reported its capacity yet counts its players.
func (p *proxy) fill() float64 {
	return float64(p.players+p.pending) / float64(max(p.max, 1))
}

// full is never true before the proxy reported its capacity.
func (p *proxy) full() bool { return p.max > 0 && p.players+p.pending >= p.max }

// Pool holds the proxies that may take logins and the network's state for status pings.
type Pool struct {
	mu      sync.Mutex
	proxies []*proxy
	online  uint32
	network *eventsv1.NetworkSettingsUpdated
	next    int
	// generation counts removals; removed holds the generation each removed proxy left at, so a refresh
	// that read Valkey before a removal cannot bring the proxy back.
	generation uint64
	removed    map[string]uint64
}

// NewPool starts empty; until the first refresh every login is refused.
func NewPool() *Pool {
	return &Pool{network: &eventsv1.NetworkSettingsUpdated{}, removed: map[string]uint64{}}
}

// Generation is taken before reading the records Update receives.
func (p *Pool) Generation() uint64 {
	p.mu.Lock()
	defer p.mu.Unlock()
	return p.generation
}

// Update replaces the membership with the RUNNING proxies among records, read at generation since.
// Membership comes from the controller's state only, never from heartbeats, so a draining proxy cannot come
// back (v1 bug 1).
func (p *Pool) Update(since uint64, records []*internalv1.ServerRecord, network *eventsv1.NetworkSettingsUpdated) {
	var proxies []*proxy
	var online uint32
	for _, record := range records {
		if record.GetGroupType() != eventsv1.GroupType_GROUP_TYPE_PROXY {
			continue
		}
		online += record.GetPlayers()
		if record.GetState() != eventsv1.ServerState_SERVER_STATE_RUNNING || record.GetHost() == "" || record.GetPort() == 0 {
			continue
		}
		proxies = append(proxies, &proxy{
			id: record.GetServerId(), address: net.JoinHostPort(record.GetHost(), strconv.FormatUint(uint64(record.GetPort()), 10)),
			players: record.GetPlayers(), max: record.GetMaxPlayers(),
		})
	}
	p.mu.Lock()
	defer p.mu.Unlock()
	kept := proxies[:0]
	for _, candidate := range proxies {
		if left, removed := p.removed[candidate.id]; !removed || left <= since {
			kept = append(kept, candidate)
		}
	}
	// Records read after a removal already show it, so it need not be remembered any longer.
	for id, left := range p.removed {
		if left <= since {
			delete(p.removed, id)
		}
	}
	p.proxies, p.online = kept, online
	if network != nil {
		p.network = network
	}
}

// Remove drops a proxy at once, e.g. when it starts draining.
func (p *Pool) Remove(serverID string) {
	p.mu.Lock()
	defer p.mu.Unlock()
	p.generation++
	p.removed[serverID] = p.generation
	for i, candidate := range p.proxies {
		if candidate.id == serverID {
			p.proxies = append(p.proxies[:i:i], p.proxies[i+1:]...)
			return
		}
	}
}

func (p *Pool) size() int {
	p.mu.Lock()
	defer p.mu.Unlock()
	return len(p.proxies)
}

// Status returns what a status ping shows.
func (p *Pool) Status() (*eventsv1.NetworkSettingsUpdated, uint32) {
	p.mu.Lock()
	defer p.mu.Unlock()
	return p.network, p.online
}

// Select picks a proxy with room by the network's routing strategy: round robin, or the least filled.
func (p *Pool) Select() (id, address string, err error) {
	p.mu.Lock()
	defer p.mu.Unlock()
	var open []*proxy
	for _, candidate := range p.proxies {
		if !candidate.full() {
			open = append(open, candidate)
		}
	}
	if len(open) == 0 {
		return "", "", errNoProxy
	}
	chosen := open[0]
	if p.network.GetRoutingStrategy() == eventsv1.RoutingStrategy_ROUTING_STRATEGY_ROUND_ROBIN {
		chosen = open[p.next%len(open)]
		p.next++
	} else {
		for _, candidate := range open[1:] {
			if candidate.fill() < chosen.fill() {
				chosen = candidate
			}
		}
	}
	chosen.pending++
	return chosen.id, chosen.address, nil
}
