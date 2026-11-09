package deploytest

import (
	"strings"
	"testing"
)

// Services send traces to Tempo only with the monitoring stack (D-0025): on for Hetzner, off on minikube.
func TestServicesExportTracesOnlyWithTheMonitoringStack(t *testing.T) {
	for values, monitored := range map[string]bool{hetznerValues: true, minikubeValues: false} {
		t.Run(values, func(t *testing.T) {
			objects := render(t, values)
			for _, service := range []string{"ogcloud-api", "ogcloud-controller"} {
				config := str(objects.find(t, "ConfigMap", service), "data", "application.yaml")
				if exports := strings.Contains(config, "endpoint: http://tempo.monitoring.svc:4318/v1/traces"); exports != monitored {
					t.Errorf("%s exports traces: %v, want %v\n%s", service, exports, monitored, config)
				}
			}
			for _, service := range []string{"ogcloud-gateway", "ogcloud-loadbalancer"} {
				env := podEnv(t, firstContainer(t, objects.find(t, "Deployment", service)), nil)
				if _, exports := env["OTEL_EXPORTER_OTLP_ENDPOINT"]; exports != monitored {
					t.Errorf("%s exports traces: %v, want %v", service, exports, monitored)
				}
			}
		})
	}
}
