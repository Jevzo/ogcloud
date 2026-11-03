package loadbalancer

import "github.com/prometheus/client_golang/prometheus"

// Connection outcomes.
const (
	outcomeStatus      = "status"
	outcomeLogin       = "login"
	outcomeLimited     = "limited"
	outcomeMalformed   = "malformed"
	outcomeUnsupported = "unsupported"
	outcomeNoProxy     = "no_proxy"
	outcomeDialFailed  = "dial_failed"
	// A trusted network sent no PROXY header, or an untrusted one sent one: the settings are wrong.
	outcomeProxyHeaderMissing   = "proxy_header_missing"
	outcomeProxyHeaderUntrusted = "proxy_header_untrusted"
)

// Metrics count connections by outcome and show the proxies that take logins.
type Metrics struct {
	connections *prometheus.CounterVec
	active      prometheus.Gauge
	proxies     prometheus.Gauge
}

// NewMetrics registers the series with registerer.
func NewMetrics(registerer prometheus.Registerer) *Metrics {
	m := &Metrics{
		connections: prometheus.NewCounterVec(prometheus.CounterOpts{
			Name: "ogcloud_lb_connections_total", Help: "Client connections by outcome.",
		}, []string{"outcome"}),
		active:  prometheus.NewGauge(prometheus.GaugeOpts{Name: "ogcloud_lb_active_logins", Help: "Players connected through this load balancer."}),
		proxies: prometheus.NewGauge(prometheus.GaugeOpts{Name: "ogcloud_lb_proxies", Help: "Running proxies that take logins."}),
	}
	registerer.MustRegister(m.connections, m.active, m.proxies)
	return m
}

func (m *Metrics) outcome(outcome string) { m.connections.WithLabelValues(outcome).Inc() }
