package gateway

import (
	"errors"
	"regexp"
	"sync"
	"time"
)

// Live-channel limits (D-0116); payloads are capped by the node protocol.
const (
	maxLiveSubscriptions   = 64
	maxLivePayloadBytes    = 64 << 10
	maxLiveTypeIDLength    = 256
	livePublishesPerSecond = 200
)

var liveChannelName = regexp.MustCompile(`^[a-z0-9._:-]{1,64}$`)

var (
	errLiveChannelName  = errors.New("invalid_channel")
	errLiveSubscribers  = errors.New("too_many_subscriptions")
	errLivePayload      = errors.New("payload_too_large")
	errLiveTypeID       = errors.New("invalid_type_id")
	errLivePublishLimit = errors.New("rate_limited")
)

// liveChannels are a session's subscriptions and its publish budget, a token bucket refilled each second.
type liveChannels struct {
	mu         sync.Mutex
	subscribed map[string]struct{}
	tokens     float64
	refilled   time.Time
}

func (l *liveChannels) subscribe(channel string, on bool) error {
	if !liveChannelName.MatchString(channel) {
		return errLiveChannelName
	}
	l.mu.Lock()
	defer l.mu.Unlock()
	if !on {
		delete(l.subscribed, channel)
		return nil
	}
	if l.subscribed == nil {
		l.subscribed = map[string]struct{}{}
	}
	if _, ok := l.subscribed[channel]; !ok && len(l.subscribed) >= maxLiveSubscriptions {
		return errLiveSubscribers
	}
	l.subscribed[channel] = struct{}{}
	return nil
}

func (l *liveChannels) has(channel string) bool {
	l.mu.Lock()
	defer l.mu.Unlock()
	_, ok := l.subscribed[channel]
	return ok
}

// admit checks a publish against the limits and spends one token.
func (l *liveChannels) admit(channel, typeID string, payload []byte, now time.Time) error {
	switch {
	case !liveChannelName.MatchString(channel):
		return errLiveChannelName
	case typeID == "" || len(typeID) > maxLiveTypeIDLength:
		return errLiveTypeID
	case len(payload) > maxLivePayloadBytes:
		return errLivePayload
	}
	l.mu.Lock()
	defer l.mu.Unlock()
	if l.refilled.IsZero() {
		l.tokens = livePublishesPerSecond
	} else {
		l.tokens = min(livePublishesPerSecond, l.tokens+now.Sub(l.refilled).Seconds()*livePublishesPerSecond)
	}
	l.refilled = now
	if l.tokens < 1 {
		return errLivePublishLimit
	}
	l.tokens--
	return nil
}
