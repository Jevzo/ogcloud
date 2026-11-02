package constants

import (
	"flag"
	"os"
	"path/filepath"
	"strings"
	"testing"
)

var update = flag.Bool("update", false, "rewrite golden files")

func TestRenderersMatchGoldenFiles(t *testing.T) {
	catalog := &Catalog{
		Object: "PodLabels",
		Doc:    "Labels on game server pods.",
		Constants: []Constant{
			{Name: "serverId", Value: "cloud.ogwars.io/server-id"},
			{Name: "role", Value: "cloud.ogwars.io/role", Doc: "backend or proxy."},
		},
	}
	goSource, err := RenderGo("podlabels", "contracts/pod-labels.yaml", catalog)
	if err != nil {
		t.Fatalf("RenderGo: %v", err)
	}
	for golden, got := range map[string][]byte{
		"labels.go.golden":    goSource,
		"PodLabels.kt.golden": RenderKotlin("io.ogwars.cloud.controller.kube", "contracts/pod-labels.yaml", catalog),
	} {
		path := filepath.Join("testdata", golden)
		if *update {
			if err := os.WriteFile(path, got, 0o600); err != nil {
				t.Fatalf("write golden: %v", err)
			}
		}
		want, err := os.ReadFile(path)
		if err != nil {
			t.Fatalf("read golden: %v", err)
		}
		if string(got) != string(want) {
			t.Errorf("%s differs; rerun with -update after reviewing\n%s", golden, got)
		}
	}
}

func TestValidateRejectsBadNamesAndEmptyValues(t *testing.T) {
	catalog := Catalog{Object: "podLabels", Constants: []Constant{{Name: "a", Value: "x"}, {Name: "a", Value: "y"}, {Name: "b"}}}

	err := catalog.Validate()

	for _, want := range []string{"PascalCase", "unique lowerCamelCase", "no value"} {
		if err == nil || !strings.Contains(err.Error(), want) {
			t.Errorf("missing %q in %v", want, err)
		}
	}
}
