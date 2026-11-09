package deploytest

import (
	"slices"
	"testing"
)

func TestEveryValueSetRenders(t *testing.T) {
	for _, values := range valueSets(t) {
		t.Run(values, func(t *testing.T) {
			if len(render(t, values)) == 0 {
				t.Error("nothing rendered")
			}
		})
	}
}

func TestPriorityClassesRankThePlatformAboveProxiesAboveTheDefaultGroupAboveGames(t *testing.T) {
	objects := render(t, minikubeValues)
	order := []string{"ogcloud-platform", "ogcloud-proxy", "ogcloud-default-group", "ogcloud-game"}
	for i := 1; i < len(order); i++ {
		higher := objects.find(t, "PriorityClass", order[i-1])["value"].(int)
		lower := objects.find(t, "PriorityClass", order[i])["value"].(int)
		if higher <= lower {
			t.Errorf("%s (%d) must outrank %s (%d) (D-0056)", order[i-1], higher, order[i], lower)
		}
	}
}

func TestTheGameNamespaceSurvivesAnUninstall(t *testing.T) {
	servers := render(t, minikubeValues).find(t, "Namespace", "ogcloud-servers")
	if str(servers, "metadata", "annotations", "helm.sh/resource-policy") != "keep" {
		t.Error("deleting the game namespace on uninstall would delete STATIC volumes")
	}
}

// Every namespaced object names its namespace, so it lands in the release's however it is applied: Tilt and
// kubectl read the manifest back, and k3s HelmCharts apply it as Helm does (D-0020).
func TestEveryNamespacedObjectNamesItsNamespace(t *testing.T) {
	clusterScoped := []string{
		"Namespace", "PriorityClass", "ClusterRole", "ClusterRoleBinding", "ClusterIssuer", "Bundle",
	}
	for _, values := range valueSets(t) {
		for _, o := range render(t, values) {
			if !slices.Contains(clusterScoped, o.kind()) && str(o, "metadata", "namespace") == "" {
				t.Errorf("%s: %s %s names no namespace", values, o.kind(), o.name())
			}
		}
	}
}
