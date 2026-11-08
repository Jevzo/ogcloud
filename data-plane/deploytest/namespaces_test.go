package deploytest

import (
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
	objects := render(t)
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
	servers := render(t).find(t, "Namespace", "ogcloud-servers")
	if str(servers, "metadata", "annotations", "helm.sh/resource-policy") != "keep" {
		t.Error("deleting the game namespace on uninstall would delete STATIC volumes")
	}
}
