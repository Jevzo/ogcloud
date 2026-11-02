package gateway

import (
	"sync"
	"time"
)

// deliveryKind names what a delivery asks of a node, for metrics and logs.
type deliveryKind string

const (
	deliveryTransfer     deliveryKind = "transfer"
	deliveryCommand      deliveryKind = "command"
	deliveryLinkCode     deliveryKind = "link_code"
	deliveryPushTemplate deliveryKind = "push_template"
)

type pendingDelivery struct {
	id   string
	kind deliveryKind
	sent time.Time
}

// pendingDeliveries are the deliveries a node has not answered yet.
type pendingDeliveries struct {
	mu   sync.Mutex
	byID map[string]pendingDelivery
}

func (p *pendingDeliveries) add(delivery pendingDelivery) {
	p.mu.Lock()
	defer p.mu.Unlock()
	if p.byID == nil {
		p.byID = map[string]pendingDelivery{}
	}
	p.byID[delivery.id] = delivery
}

// take removes and returns the delivery a result answers.
func (p *pendingDeliveries) take(id string) (pendingDelivery, bool) {
	p.mu.Lock()
	defer p.mu.Unlock()
	delivery, ok := p.byID[id]
	delete(p.byID, id)
	return delivery, ok
}

// expire removes and returns the deliveries sent before cutoff.
func (p *pendingDeliveries) expire(cutoff time.Time) []pendingDelivery {
	p.mu.Lock()
	defer p.mu.Unlock()
	var expired []pendingDelivery
	for id, delivery := range p.byID {
		if delivery.sent.Before(cutoff) {
			expired = append(expired, delivery)
			delete(p.byID, id)
		}
	}
	return expired
}
