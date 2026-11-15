package deploytest

import (
	"os"
	"path/filepath"
	"slices"
	"testing"

	"go.yaml.in/yaml/v3"
)

// The chart's vendored subcharts are exactly what Chart.lock pins: a version bump to Chart.yaml and the lock alone,
// as a Renovate PR makes, would otherwise keep shipping the old archive. `helm dependency build` refreshes them.
func TestVendoredSubchartsMatchTheLock(t *testing.T) {
	data, err := os.ReadFile(filepath.Join(chartDir, "Chart.lock"))
	if err != nil {
		t.Fatal(err)
	}
	var lock struct {
		Dependencies []struct {
			Name    string `yaml:"name"`
			Version string `yaml:"version"`
		} `yaml:"dependencies"`
	}
	if err := yaml.Unmarshal(data, &lock); err != nil {
		t.Fatalf("Chart.lock: %v", err)
	}
	var want []string
	for _, dependency := range lock.Dependencies {
		want = append(want, dependency.Name+"-"+dependency.Version+".tgz")
	}
	archives, err := filepath.Glob(filepath.Join(chartDir, "charts", "*.tgz"))
	if err != nil {
		t.Fatal(err)
	}
	var got []string
	for _, archive := range archives {
		got = append(got, filepath.Base(archive))
	}
	slices.Sort(want)
	slices.Sort(got)
	if !slices.Equal(got, want) {
		t.Errorf("charts/ holds %v, Chart.lock pins %v; run helm dependency build", got, want)
	}
}
