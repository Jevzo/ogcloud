package deploytest

import (
	"encoding/json"
	"os"
	"regexp"
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

var dashboardMetric = regexp.MustCompile(`\bogcloud_[a-z_]+`)

// A renamed metric would leave a dashboard panel empty without a word.
func TestDashboardsQueryOnlyMetricsTheServicesExport(t *testing.T) {
	var exported strings.Builder
	for _, source := range []string{"../gateway/metrics.go", "../loadbalancer/metrics.go"} {
		code, err := os.ReadFile(source)
		if err != nil {
			t.Fatal(err)
		}
		exported.Write(code)
	}
	dashboards := 0
	for _, configMap := range render(t, hetznerValues).all("ConfigMap") {
		if str(configMap, "metadata", "labels", "grafana_dashboard") != "1" {
			continue
		}
		data, _ := get(configMap, "data").(map[string]any)
		for name, content := range data {
			dashboards++
			var dashboard struct {
				Panels []struct {
					Title   string
					Targets []struct{ Expr string }
				}
			}
			if err := json.Unmarshal([]byte(content.(string)), &dashboard); err != nil {
				t.Fatalf("%s: %v", name, err)
			}
			for _, panel := range dashboard.Panels {
				for _, target := range panel.Targets {
					for _, metric := range dashboardMetric.FindAllString(target.Expr, -1) {
						if !strings.Contains(exported.String(), `"`+strings.TrimSuffix(metric, "_total")) {
							t.Errorf("%s panel %q queries %s, which no service exports", name, panel.Title, metric)
						}
					}
				}
			}
		}
	}
	if dashboards == 0 {
		t.Error("monitoring renders no dashboard")
	}
}

// Grafana takes the role the API names and keeps no refresh token, since its tokens may reach data sources (D-0185).
func TestGrafanaSignsInWithTheAPIsRoleAndNoRefreshToken(t *testing.T) {
	targets, _ := get(render(t, hetznerValues).find(t, "InfisicalStaticSecret", "ogcloud-grafana-oauth"), "spec", "targets").([]any)
	if len(targets) != 1 {
		t.Fatalf("Grafana's OAuth settings sync to %d targets, want 1", len(targets))
	}
	data, _ := get(targets[0], "template", "data").(map[string]any)
	if got := data["GF_AUTH_GENERIC_OAUTH_ROLE_ATTRIBUTE_PATH"]; got != "grafana_role" {
		t.Errorf("Grafana's role path = %v, want the API's grafana_role claim", got)
	}
	if got := data["GF_AUTH_GENERIC_OAUTH_ROLE_ATTRIBUTE_STRICT"]; got != "true" {
		t.Errorf("Grafana admits users without a role: strict = %v", got)
	}
	if _, refresh := data["GF_AUTH_GENERIC_OAUTH_USE_REFRESH_TOKEN"]; refresh {
		t.Error("Grafana still asks for refresh tokens")
	}
}
