package deploytest

import (
	"maps"
	"slices"
	"strings"
	"testing"
)

// Admin surfaces are never public (D-0045): the public Gateway routes only to the API and the dashboard, and
// Infisical and Grafana attach to the internal Gateway alone; no value set publishes Grafana (D-0236 is opt-in).
func TestAdminSurfacesNeverAttachToThePublicGateway(t *testing.T) {
	public := []string{"ogcloud-api", "ogcloud-web"}
	for _, values := range valueSets(t) {
		t.Run(values, func(t *testing.T) {
			admin := 0
			for _, route := range render(t, values).all("HTTPRoute") {
				var parents, backends []string
				refs, _ := get(route, "spec", "parentRefs").([]any)
				for _, ref := range refs {
					parents = append(parents, str(ref, "name"))
				}
				rules, _ := get(route, "spec", "rules").([]any)
				for _, rule := range rules {
					backendRefs, _ := get(rule, "backendRefs").([]any)
					for _, backend := range backendRefs {
						backends = append(backends, str(backend, "name"))
					}
				}
				for _, backend := range backends {
					if slices.Contains(parents, "ogcloud-public") && !slices.Contains(public, backend) {
						t.Errorf("HTTPRoute %s puts %s on the public Gateway", route.name(), backend)
					}
				}
				if slices.Contains(parents, "ogcloud-admin") {
					admin++
					if len(parents) != 1 {
						t.Errorf("admin HTTPRoute %s also attaches to %v", route.name(), parents)
					}
				}
			}
			if values == hetznerValues && admin != 2 {
				t.Errorf("the internal Gateway has %d routes, want Infisical's and Grafana's", admin)
			}
		})
	}
}

// One host serves everything (D-0098): the sign-in and API paths go to the API, and the rest to the dashboard, which
// Gateway API's longest-prefix rule keeps from taking the API's paths.
func TestThePublicOriginIsSplitByPath(t *testing.T) {
	want := map[string][]string{
		"ogcloud-api": {"/.well-known", "/api", "/login", "/oauth2", "/webauthn"},
		"ogcloud-web": {"/"},
	}
	for _, values := range valueSets(t) {
		t.Run(values, func(t *testing.T) {
			got := map[string][]string{}
			for _, route := range render(t, values).all("HTTPRoute") {
				refs, _ := get(route, "spec", "parentRefs").([]any)
				if len(refs) == 0 || str(refs[0], "name") != "ogcloud-public" {
					continue
				}
				rules, _ := get(route, "spec", "rules").([]any)
				for _, rule := range rules {
					backendRefs, _ := get(rule, "backendRefs").([]any)
					matches, _ := get(rule, "matches").([]any)
					for _, backend := range backendRefs {
						for _, match := range matches {
							if str(get(match, "path"), "type") != "PathPrefix" {
								t.Errorf("route %s matches %v, want path prefixes", route.name(), match)
							}
							name := str(backend, "name")
							got[name] = append(got[name], str(get(match, "path"), "value"))
						}
					}
				}
			}
			for backend, paths := range got {
				slices.Sort(paths)
				got[backend] = paths
			}
			if !maps.EqualFunc(got, want, slices.Equal) {
				t.Errorf("the public Gateway routes %v, want %v", got, want)
			}
		})
	}
}

// The public Gateway answers for publicOrigin's host, also when the origin names a port, as minikube's does.
func TestThePublicGatewayServesThePublicOriginsHost(t *testing.T) {
	for host, values := range map[string][]string{
		"cloud.example.com": {hetznerValues},
		"ogcloud.localhost": {minikubeValues, "../../deploy/dev/values.yaml"},
	} {
		listeners, _ := get(render(t, values...).find(t, "Gateway", "ogcloud-public"), "spec", "listeners").([]any)
		for _, listener := range listeners {
			if got := str(listener, "hostname"); got != host {
				t.Errorf("%v: listener %s answers for %q, want %q", values, str(listener, "name"), got, host)
			}
		}
	}
}

// Let's Encrypt solves DNS-01 with the Cloudflare token the operator seeds, synced into the namespace a ClusterIssuer
// reads its Secrets from; both Gateways' certificates come from it (D-0160).
func TestLetsEncryptSolvesDNS01WithTheSeededToken(t *testing.T) {
	objects := render(t, hetznerValues)
	solvers, _ := get(objects.find(t, "ClusterIssuer", "ogcloud-letsencrypt"), "spec", "acme", "solvers").([]any)
	if len(solvers) != 1 {
		t.Fatalf("Let's Encrypt has %d solvers, want 1", len(solvers))
	}
	ref := get(solvers[0], "dns01", "cloudflare", "apiTokenSecretRef")
	targets, _ := get(objects.find(t, "InfisicalStaticSecret", str(ref, "name")), "spec", "targets").([]any)
	if len(targets) != 1 {
		t.Fatalf("the Cloudflare token goes to %d Secrets, want 1", len(targets))
	}
	if got := str(targets[0], "namespace"); got != "cert-manager" {
		t.Errorf("the Cloudflare token lands in %q, want cert-manager's namespace", got)
	}
	if got := str(targets[0], "template", "data", str(ref, "key")); got != "{{ .CLOUDFLARE_API_TOKEN.Value }}" {
		t.Errorf("the solver's key holds %q, want the seeded CLOUDFLARE_API_TOKEN", got)
	}
	for _, certificate := range []string{"ogcloud-public", "ogcloud-admin"} {
		if got := str(objects.find(t, "Certificate", certificate), "spec", "issuerRef", "name"); got != "ogcloud-letsencrypt" {
			t.Errorf("Certificate %s is issued by %q, want ogcloud-letsencrypt", certificate, got)
		}
	}
}

func TestLetsEncryptStaysOffUnlessEnabled(t *testing.T) {
	for _, issuer := range render(t, minikubeValues).all("ClusterIssuer") {
		if issuer.name() == "ogcloud-letsencrypt" {
			t.Error("minikube got the Let's Encrypt issuer")
		}
	}
}

// On a private test cluster Grafana may be public at a host of its own, behind OgCloud's sign-in only (D-0236): its
// own listener and certificate name, its route, and Grafana's settings follow that host.
func TestGrafanaCanBePublicAtAHostOfItsOwn(t *testing.T) {
	const host = "monitoring.example.com"
	output, err := helmTemplate(t, []string{minikubeValues},
		"--set", "monitoring.enabled=true", "--set", "admin.grafana.publicHost="+host)
	if err != nil {
		t.Fatalf("helm template: %v", err)
	}
	objects := decodeRendered(t, output)
	listeners, _ := get(objects.find(t, "Gateway", "ogcloud-public"), "spec", "listeners").([]any)
	served := map[string]string{}
	for _, listener := range listeners {
		served[str(listener, "name")] = str(listener, "hostname")
	}
	if served["grafana"] != host || served["grafana-http"] != host {
		t.Errorf("the public Gateway's listeners are %v, want grafana and grafana-http at %s", served, host)
	}
	names, _ := get(objects.find(t, "Certificate", "ogcloud-public"), "spec", "dnsNames").([]any)
	if !slices.Contains(names, any(host)) {
		t.Errorf("the public certificate names %v, not %s", names, host)
	}
	route := objects.find(t, "HTTPRoute", "ogcloud-grafana")
	refs, _ := get(route, "spec", "parentRefs").([]any)
	if len(refs) != 1 || str(refs[0], "name") != "ogcloud-public" || str(refs[0], "sectionName") != "grafana" {
		t.Errorf("Grafana's route attaches to %v, want the public Gateway's grafana listener only", refs)
	}
	targets, _ := get(objects.find(t, "InfisicalStaticSecret", "ogcloud-grafana-oauth"), "spec", "targets").([]any)
	if len(targets) != 1 {
		t.Fatalf("Grafana's OAuth settings sync to %d targets, want 1", len(targets))
	}
	data, _ := get(targets[0], "template", "data").(map[string]any)
	if got := data["GF_SERVER_ROOT_URL"]; got != "https://"+host {
		t.Errorf("Grafana's root URL = %v, want https://%s", got, host)
	}
	if got := data["GF_AUTH_DISABLE_LOGIN_FORM"]; got != "true" {
		t.Errorf("public Grafana keeps its password form: %v", got)
	}
}

// A public Grafana needs the monitoring stack, and never sits on both Gateways (D-0045, D-0236).
func TestAPublicGrafanaNeedsMonitoringAndNoInternalGateway(t *testing.T) {
	refused := func(values, want string, args ...string) {
		t.Helper()
		if _, err := helmTemplate(t, []string{values}, args...); err == nil || !strings.Contains(err.Error(), want) {
			t.Errorf("%v rendered with %v, want %q", args, err, want)
		}
	}
	refused(minikubeValues, "needs monitoring.enabled", "--set", "admin.grafana.publicHost=monitoring.example.com")
	refused(hetznerValues, "cannot be used with the internal Gateway",
		"--set", "admin.grafana.publicHost=monitoring.example.com")
}
