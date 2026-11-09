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
