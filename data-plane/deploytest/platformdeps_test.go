package deploytest

import (
	"os"
	"regexp"
	"slices"
	"strings"
	"testing"

	"go.yaml.in/yaml/v3"
)

type platformDeps struct {
	Manifests []struct {
		Name   string `yaml:"name"`
		URL    string `yaml:"url"`
		SHA256 string `yaml:"sha256"`
	} `yaml:"manifests"`
	Charts []struct {
		Name       string         `yaml:"name"`
		When       string         `yaml:"when"`
		Namespace  string         `yaml:"namespace"`
		Repository string         `yaml:"repository"`
		Chart      string         `yaml:"chart"`
		Version    string         `yaml:"version"`
		Values     map[string]any `yaml:"values"`
		Targets    map[string]any `yaml:"targets"`
	} `yaml:"charts"`
}

var (
	exactVersion = regexp.MustCompile(`^v?\d+\.\d+\.\d+$`)
	sha256Hex    = regexp.MustCompile(`^[0-9a-f]{64}$`)
	knownTargets = []string{"minikube", "hetzner"}
)

// Every dependency is pinned to one stable release (AGENTS.md §9), so Tilt, Terraform and k3s install the same.
func TestPlatformDependenciesArePinnedExactly(t *testing.T) {
	data, err := os.ReadFile("../../deploy/platform-deps.yaml")
	if err != nil {
		t.Fatal(err)
	}
	var deps platformDeps
	decoder := yaml.NewDecoder(strings.NewReader(string(data)))
	decoder.KnownFields(true)
	if err := decoder.Decode(&deps); err != nil {
		t.Fatalf("platform-deps.yaml: %v", err)
	}
	for _, manifest := range deps.Manifests {
		if !strings.HasPrefix(manifest.URL, "https://") || !sha256Hex.MatchString(manifest.SHA256) {
			t.Errorf("manifest %s needs an https URL and its sha256", manifest.Name)
		}
	}
	names := map[string]bool{}
	for _, chart := range deps.Charts {
		if names[chart.Name] {
			t.Errorf("chart %s is listed twice", chart.Name)
		}
		names[chart.Name] = true
		if !exactVersion.MatchString(chart.Version) {
			t.Errorf("chart %s version %q is not one stable release", chart.Name, chart.Version)
		}
		if !strings.HasPrefix(chart.Repository, "https://") || chart.Chart == "" || chart.Namespace == "" {
			t.Errorf("chart %s needs an https repository, a chart and a namespace", chart.Name)
		}
		if chart.When != "" && chart.When != "monitoring" {
			t.Errorf("chart %s: unknown toggle %q", chart.Name, chart.When)
		}
		for target := range chart.Targets {
			if !slices.Contains(knownTargets, target) {
				t.Errorf("chart %s: unknown install target %q", chart.Name, target)
			}
		}
	}
}
