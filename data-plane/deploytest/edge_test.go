package deploytest

import (
	"slices"
	"testing"
)

// Admin surfaces are never public (D-0045): the public Gateway routes only to the API, and Infisical and Grafana
// attach to the internal Gateway alone.
func TestAdminSurfacesNeverAttachToThePublicGateway(t *testing.T) {
	public := []string{"ogcloud-api"}
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
