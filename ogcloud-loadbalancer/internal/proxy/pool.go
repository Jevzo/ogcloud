package proxy

import (
	"errors"
	"fmt"
	"net"
	"sort"
	"strings"
	"sync"
	"sync/atomic"
	"time"

	"go.uber.org/zap"
)

var ErrNoBackendAvailable = errors.New("no healthy backend available")

const backendDialTimeout = 5 * time.Second

type Backend struct {
	ProxyID     string
	Address     string
	activeConns atomic.Int32
	playerCount atomic.Int32
	maxPlayers  atomic.Int32
	lastSeen    atomic.Int64
}

func (b *Backend) isHealthy(timeout time.Duration) bool {
	last := time.UnixMilli(b.lastSeen.Load())
	return time.Since(last) < timeout
}

func (b *Backend) currentPlayers() int32 {
	playerCount := b.playerCount.Load()
	activeConns := b.activeConns.Load()
	if activeConns > playerCount {
		return activeConns
	}
	return playerCount
}

func (b *Backend) isFull() bool {
	maxPlayers := b.maxPlayers.Load()
	if maxPlayers <= 0 {
		return false
	}
	return b.currentPlayers() >= maxPlayers
}

type Pool struct {
	backends      map[string]*Backend
	healthTimeout time.Duration
	mu            sync.RWMutex
	logger        *zap.Logger
	roundRobinIdx atomic.Uint64
}

func NewPool(healthTimeout time.Duration, logger *zap.Logger) *Pool {
	return &Pool{
		backends:      make(map[string]*Backend),
		healthTimeout: healthTimeout,
		logger:        logger,
	}
}

func (p *Pool) UpdateBackend(proxyID, address string, playerCount int, maxPlayers int) {
	p.mu.Lock()
	defer p.mu.Unlock()

	b, exists := p.backends[proxyID]
	if !exists {
		b = &Backend{
			ProxyID: proxyID,
			Address: address,
		}
		p.backends[proxyID] = b
		p.logger.Info("backend added", zap.String("proxyId", proxyID), zap.String("address", address))
	}

	b.Address = address
	b.playerCount.Store(int32(playerCount))
	b.maxPlayers.Store(int32(maxPlayers))
	b.lastSeen.Store(time.Now().UnixMilli())
}

func (p *Pool) RemoveBackend(proxyID string) {
	p.mu.Lock()
	defer p.mu.Unlock()

	if b, exists := p.backends[proxyID]; exists {
		p.logger.Info("backend removed", zap.String("proxyId", proxyID), zap.String("address", b.Address))
		delete(p.backends, proxyID)
	}
}

func (p *Pool) SelectBackend(strategy string) (*Backend, error) {
	p.mu.RLock()
	defer p.mu.RUnlock()

	healthy := p.healthyBackendsLocked()
	if len(healthy) == 0 {
		return nil, ErrNoBackendAvailable
	}

	switch normalizeRoutingStrategy(strategy) {
	case RoutingStrategyRoundRobin:
		return p.selectRoundRobinBackend(healthy), nil
	default:
		return p.selectLoadBasedBackend(healthy), nil
	}
}

func (p *Pool) healthyBackendsLocked() []*Backend {
	healthy := make([]*Backend, 0, len(p.backends))
	for _, backend := range p.backends {
		if !backend.isHealthy(p.healthTimeout) || backend.isFull() {
			continue
		}
		healthy = append(healthy, backend)
	}
	sort.Slice(healthy, func(i, j int) bool {
		return healthy[i].ProxyID < healthy[j].ProxyID
	})
	return healthy
}

func (p *Pool) selectLoadBasedBackend(backends []*Backend) *Backend {
	best := backends[0]
	bestLoad := best.currentPlayers()
	for _, backend := range backends[1:] {
		load := backend.currentPlayers()
		if load < bestLoad {
			best = backend
			bestLoad = load
		}
	}
	return best
}

func (p *Pool) selectRoundRobinBackend(backends []*Backend) *Backend {
	index := p.roundRobinIdx.Add(1) - 1
	selectedIndex := int(index % uint64(len(backends)))
	return backends[selectedIndex]
}

func (p *Pool) Dial(b *Backend) (net.Conn, error) {
	conn, err := net.DialTimeout("tcp", b.Address, backendDialTimeout)
	if err != nil {
		return nil, fmt.Errorf("dial backend %s: %w", b.Address, err)
	}
	b.activeConns.Add(1)
	return conn, nil
}

func (p *Pool) ReleaseBackend(b *Backend) {
	b.activeConns.Add(-1)
}

func (p *Pool) RemoveStale() {
	p.mu.Lock()
	defer p.mu.Unlock()

	for id, b := range p.backends {
		if !b.isHealthy(p.healthTimeout) {
			p.logger.Info("removing stale backend", zap.String("proxyId", id), zap.String("address", b.Address))
			delete(p.backends, id)
		}
	}
}

func normalizeRoutingStrategy(value string) string {
	switch strings.ToUpper(strings.TrimSpace(value)) {
	case RoutingStrategyRoundRobin:
		return RoutingStrategyRoundRobin
	case RoutingStrategyLoadBased:
		return RoutingStrategyLoadBased
	default:
		return RoutingStrategyLoadBased
	}
}

const (
	RoutingStrategyRoundRobin = "ROUND_ROBIN"
	RoutingStrategyLoadBased  = "LOAD_BASED"
)
