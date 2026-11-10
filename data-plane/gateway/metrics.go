package gateway

import (
	"strings"
	"time"

	"github.com/prometheus/client_golang/prometheus"

	eventsv1 "github.com/jevzo/ogcloud/data-plane/pkg/gen/proto/ogcloud/events/v1"
	nodev1 "github.com/jevzo/ogcloud/data-plane/pkg/gen/proto/ogcloud/node/v1"
)

// Outcomes of a delivery.
const (
	outcomeSuccess = "success"
	outcomeFailure = "failure"
	outcomeTimeout = "timeout"
	// The node's queue was full, so it was disconnected instead.
	outcomeDropped = "dropped"
)

// Results of a login resolution.
const (
	loginAllowed = "allowed"
	loginRefused = "refused"
	loginFailed  = "failed"
)

var gameLabels = []string{"server_id", "group_id", "node_type"}

// Metrics are the gateway's own series plus the game metrics of the nodes it holds (D-0025); a node's
// series disappear with its session.
type Metrics struct {
	players, maxPlayers, tps, mspt, memoryUsed, memoryMax *prometheus.GaugeVec

	sessions   *prometheus.GaugeVec
	ended      *prometheus.CounterVec
	refused    *prometheus.CounterVec
	authFailed *prometheus.CounterVec
	rejected   *prometheus.CounterVec
	deliveries *prometheus.CounterVec
	unroutable *prometheus.CounterVec
	unsent     *prometheus.CounterVec
	logins     *prometheus.HistogramVec
}

// NewMetrics registers the series with registerer.
func NewMetrics(registerer prometheus.Registerer) *Metrics {
	gauge := func(name, help string, labels ...string) *prometheus.GaugeVec {
		vector := prometheus.NewGaugeVec(prometheus.GaugeOpts{Name: name, Help: help}, labels)
		registerer.MustRegister(vector)
		return vector
	}
	counter := func(name, help string, labels ...string) *prometheus.CounterVec {
		vector := prometheus.NewCounterVec(prometheus.CounterOpts{Name: name, Help: help}, labels)
		registerer.MustRegister(vector)
		return vector
	}
	logins := prometheus.NewHistogramVec(prometheus.HistogramOpts{
		Name: "ogcloud_gateway_login_resolution_seconds",
		Help: "How long the controller took to resolve a proxy's login, by result (D-0175).",
		// Fine around the 50 ms target.
		Buckets: []float64{0.005, 0.01, 0.025, 0.05, 0.1, 0.25, 0.5, 1, 2.5},
	}, []string{"result"})
	registerer.MustRegister(logins)
	return &Metrics{
		players:    gauge("ogcloud_node_players", "Players on a node, from its heartbeats.", gameLabels...),
		maxPlayers: gauge("ogcloud_node_max_players", "A node's player limit.", gameLabels...),
		tps:        gauge("ogcloud_node_tps", "Ticks per second of a backend.", gameLabels...),
		mspt:       gauge("ogcloud_node_mspt", "Milliseconds per tick of a backend.", gameLabels...),
		memoryUsed: gauge("ogcloud_node_memory_used_bytes", "Heap a node uses.", gameLabels...),
		memoryMax:  gauge("ogcloud_node_memory_max_bytes", "Heap a node may use.", gameLabels...),
		sessions:   gauge("ogcloud_gateway_sessions", "Open node sessions.", "node_type"),
		ended:      counter("ogcloud_gateway_sessions_ended_total", "Node sessions that ended, by reason.", "reason"),
		refused:    counter("ogcloud_gateway_sessions_refused_total", "Node sessions refused before their Welcome, by status code.", "code"),
		authFailed: counter("ogcloud_gateway_auth_failures_total", "Calls refused while authenticating the pod, by status code.", "code"),
		rejected: counter("ogcloud_gateway_rejected_messages_total",
			"Node messages dropped because the node may not send them or they were invalid (D-0116).", "message", "reason"),
		deliveries: counter("ogcloud_gateway_deliveries_total", "Deliveries to nodes, by outcome.", "kind", "outcome"),
		unroutable: counter("ogcloud_gateway_unroutable_records_total", "Kafka records the gateway could not route.", "topic"),
		unsent:     counter("ogcloud_gateway_unpublished_events_total", "Node events that did not reach Kafka.", "message"),
		logins:     logins,
	}
}

func (m *Metrics) opened(session *Session) {
	m.sessions.WithLabelValues(nodeTypeLabel(session.NodeType)).Inc()
}

// closed counts the end of a session and forgets its game series, unless a newer session of the same
// server took them over.
func (m *Metrics) closed(session *Session, reason string, current bool) {
	m.sessions.WithLabelValues(nodeTypeLabel(session.NodeType)).Dec()
	m.ended.WithLabelValues(reason).Inc()
	if !current {
		return
	}
	labels := session.gameLabels()
	for _, gauge := range []*prometheus.GaugeVec{m.players, m.maxPlayers, m.tps, m.mspt, m.memoryUsed, m.memoryMax} {
		gauge.DeleteLabelValues(labels...)
	}
}

func (m *Metrics) heartbeat(session *Session, heartbeat *nodev1.Heartbeat) {
	labels := session.gameLabels()
	m.players.WithLabelValues(labels...).Set(float64(heartbeat.GetPlayers()))
	m.maxPlayers.WithLabelValues(labels...).Set(float64(heartbeat.GetMaxPlayers()))
	m.memoryUsed.WithLabelValues(labels...).Set(float64(heartbeat.GetMemoryUsedBytes()))
	m.memoryMax.WithLabelValues(labels...).Set(float64(heartbeat.GetMemoryMaxBytes()))
	if session.NodeType == eventsv1.NodeType_NODE_TYPE_BACKEND {
		m.tps.WithLabelValues(labels...).Set(heartbeat.GetTps())
		m.mspt.WithLabelValues(labels...).Set(heartbeat.GetMspt())
	}
}

func (m *Metrics) loginResolved(result string, took time.Duration) {
	m.logins.WithLabelValues(result).Observe(took.Seconds())
}

func (m *Metrics) delivery(kind deliveryKind, outcome string) {
	m.deliveries.WithLabelValues(string(kind), outcome).Inc()
}

// nodeTypeLabel turns NODE_TYPE_TEMPLATE_AGENT into template_agent.
func nodeTypeLabel(nodeType eventsv1.NodeType) string {
	return strings.ToLower(strings.TrimPrefix(nodeType.String(), "NODE_TYPE_"))
}
