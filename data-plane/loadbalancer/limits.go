package loadbalancer

import (
	"net/netip"
	"sync"
	"time"
)

// Limits bound what one client address may hold and open (D-0118).
type Limits struct {
	// Open connections per address.
	Open int
	// New connections per second per address, and the burst above that.
	Rate  float64
	Burst float64
}

type client struct {
	open     int
	tokens   float64
	refilled time.Time
}

// limiter tracks clients by address; idle ones are forgotten by Sweep.
type limiter struct {
	limits Limits
	mu     sync.Mutex
	byAddr map[netip.Addr]*client
}

func newLimiter(limits Limits) *limiter {
	return &limiter{limits: limits, byAddr: map[netip.Addr]*client{}}
}

// acquire admits a new connection from addr and returns its release, or false when over a limit.
func (l *limiter) acquire(addr netip.Addr, now time.Time) (func(), bool) {
	l.mu.Lock()
	defer l.mu.Unlock()
	c, ok := l.byAddr[addr]
	if !ok {
		c = &client{tokens: l.limits.Burst, refilled: now}
		l.byAddr[addr] = c
	}
	c.tokens = min(l.limits.Burst, c.tokens+now.Sub(c.refilled).Seconds()*l.limits.Rate)
	c.refilled = now
	if c.open >= l.limits.Open || c.tokens < 1 {
		return nil, false
	}
	c.tokens--
	c.open++
	var once sync.Once
	return func() {
		once.Do(func() {
			l.mu.Lock()
			defer l.mu.Unlock()
			c.open--
		})
	}, true
}

// sweep forgets clients without connections whose budget has refilled.
func (l *limiter) sweep(now time.Time) {
	l.mu.Lock()
	defer l.mu.Unlock()
	refill := time.Duration(l.limits.Burst / l.limits.Rate * float64(time.Second))
	for addr, c := range l.byAddr {
		if c.open == 0 && now.Sub(c.refilled) >= refill {
			delete(l.byAddr, addr)
		}
	}
}
