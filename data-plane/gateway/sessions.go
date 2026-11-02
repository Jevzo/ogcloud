package gateway

import (
	"context"
	"errors"
	"sync"

	eventsv1 "github.com/jevzo/ogcloud/data-plane/pkg/gen/proto/ogcloud/events/v1"
	nodev1 "github.com/jevzo/ogcloud/data-plane/pkg/gen/proto/ogcloud/node/v1"
)

var (
	// ErrSlowNode ends a session whose node reads too slowly; it would otherwise hold up the others.
	ErrSlowNode = errors.New("the node fell too far behind")
	// ErrReplaced ends a session when the same node connects again.
	ErrReplaced = errors.New("replaced by a newer session")
)

// Session is one node's open stream. Messages for it are queued and sent by the stream's own goroutine.
type Session struct {
	Peer         Peer
	NodeType     eventsv1.NodeType
	Capabilities []string

	out        chan *nodev1.SessionResponse
	cancel     context.CancelCauseFunc
	live       liveChannels
	deliveries pendingDeliveries
}

func newSession(peer Peer, hello *nodev1.Hello, queueSize int, cancel context.CancelCauseFunc) *Session {
	return &Session{
		Peer:         peer,
		NodeType:     hello.GetNodeType(),
		Capabilities: hello.GetCapabilities(),
		out:          make(chan *nodev1.SessionResponse, queueSize),
		cancel:       cancel,
	}
}

// Send queues message without blocking; a full queue disconnects the node instead.
func (s *Session) Send(message *nodev1.SessionResponse) bool {
	select {
	case s.out <- message:
		return true
	default:
		s.cancel(ErrSlowNode)
		return false
	}
}

// isNode is true for proxies and backends, the sessions that run game code.
func (s *Session) isNode() bool {
	return s.NodeType == eventsv1.NodeType_NODE_TYPE_PROXY || s.NodeType == eventsv1.NodeType_NODE_TYPE_BACKEND
}

func (s *Session) gameLabels() []string {
	return []string{s.Peer.ServerID, s.Peer.GroupID, nodeTypeLabel(s.NodeType)}
}

type sessionKey struct {
	serverID string
	nodeType eventsv1.NodeType
}

// Registry holds the sessions this replica serves, one per server and node type.
type Registry struct {
	mu       sync.RWMutex
	sessions map[sessionKey]*Session
}

// NewRegistry returns an empty registry.
func NewRegistry() *Registry {
	return &Registry{sessions: map[sessionKey]*Session{}}
}

// Add registers session and ends the one it replaces, e.g. after a reconnect.
func (r *Registry) Add(session *Session) {
	key := sessionKey{session.Peer.ServerID, session.NodeType}
	r.mu.Lock()
	previous := r.sessions[key]
	r.sessions[key] = session
	r.mu.Unlock()
	if previous != nil {
		previous.cancel(ErrReplaced)
	}
}

// Remove forgets session unless a newer one replaced it.
func (r *Registry) Remove(session *Session) {
	key := sessionKey{session.Peer.ServerID, session.NodeType}
	r.mu.Lock()
	defer r.mu.Unlock()
	if r.sessions[key] == session {
		delete(r.sessions, key)
	}
}

// Get returns the session of a server's node type.
func (r *Registry) Get(serverID string, nodeType eventsv1.NodeType) (*Session, bool) {
	r.mu.RLock()
	defer r.mu.RUnlock()
	session, ok := r.sessions[sessionKey{serverID, nodeType}]
	return session, ok
}

// All returns the current sessions.
func (r *Registry) All() []*Session {
	return r.Select(func(*Session) bool { return true })
}

// Select returns the current sessions that match.
func (r *Registry) Select(match func(*Session) bool) []*Session {
	r.mu.RLock()
	defer r.mu.RUnlock()
	var sessions []*Session
	for _, session := range r.sessions {
		if match(session) {
			sessions = append(sessions, session)
		}
	}
	return sessions
}
