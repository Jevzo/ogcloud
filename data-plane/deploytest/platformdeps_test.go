package deploytest

import (
	"errors"
	"os"
	"os/exec"
	"path/filepath"
	"regexp"
	"slices"
	"strings"
	"testing"

	"go.yaml.in/yaml/v3"
)

type platformDeps struct {
	InstallTargets []string `yaml:"targets"`
	Manifests      []struct {
		Name    string `yaml:"name"`
		When    string `yaml:"when"`
		URL     string `yaml:"url"`
		SHA256  string `yaml:"sha256"`
		Follows *struct {
			Chart   string `yaml:"chart"`
			Version string `yaml:"version"`
		} `yaml:"follows"`
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
)

// Every dependency is pinned to one stable release (AGENTS.md §9), so Tilt, Terraform and k3s install the same.
func TestPlatformDependenciesArePinnedExactly(t *testing.T) {
	deps := loadPlatformDeps(t)
	for _, manifest := range deps.Manifests {
		if !strings.HasPrefix(manifest.URL, "https://") || !sha256Hex.MatchString(manifest.SHA256) {
			t.Errorf("manifest %s needs an https URL and its sha256", manifest.Name)
		}
		if manifest.When != "" && manifest.When != "monitoring" {
			t.Errorf("manifest %s: unknown toggle %q", manifest.Name, manifest.When)
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
			if !slices.Contains(deps.InstallTargets, target) {
				t.Errorf("chart %s: unknown install target %q", chart.Name, target)
			}
		}
	}
}

// helm upgrade never changes a chart's crds/, so those CRDs come from a manifest pinned to the chart's release
// (D-0171). A chart bump, Renovate's included, fails here until its manifest is bumped too.
func TestCRDManifestsFollowTheirCharts(t *testing.T) {
	deps := loadPlatformDeps(t)
	charts := map[string]string{}
	toggles := map[string]string{}
	for _, chart := range deps.Charts {
		charts[chart.Name] = chart.Version
		toggles[chart.Name] = chart.When
	}
	followed := map[string]bool{}
	for _, manifest := range deps.Manifests {
		if manifest.Follows == nil {
			continue
		}
		version, ok := charts[manifest.Follows.Chart]
		switch {
		case !ok:
			t.Errorf("manifest %s follows chart %q, which platform-deps.yaml does not list", manifest.Name, manifest.Follows.Chart)
		case manifest.Follows.Version != version:
			t.Errorf("manifest %s follows %s %s, but the chart is pinned to %s: bump its url, sha256 and follows.version",
				manifest.Name, manifest.Follows.Chart, manifest.Follows.Version, version)
		case manifest.When != toggles[manifest.Follows.Chart]:
			t.Errorf("manifest %s installs with %q, its chart with %q", manifest.Name, manifest.When, toggles[manifest.Follows.Chart])
		}
		followed[manifest.Follows.Chart] = true
	}
	// The charts that keep CRDs in crds/ and whose CRDs OgCloud uses.
	for _, chart := range []string{"strimzi", "kube-prometheus-stack"} {
		if !followed[chart] {
			t.Errorf("no manifest follows %s, so its CRDs would stay at their first install", chart)
		}
	}
}

func loadPlatformDeps(t *testing.T) platformDeps {
	t.Helper()
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
	return deps
}

// Traefik binds a Gateway listener only to an entrypoint on the same port, the one in its ports section, not
// the Service's 80 or 443; a mismatch leaves the Gateway unattached and the site unreachable.
func TestGatewaysListenOnTraefiksEntrypoints(t *testing.T) {
	var entrypoints []int
	for _, chart := range loadPlatformDeps(t).Charts {
		if chart.Name != "traefik" {
			continue
		}
		ports, _ := chart.Values["ports"].(map[string]any)
		for _, port := range ports {
			if number, ok := get(port, "port").(int); ok {
				entrypoints = append(entrypoints, number)
			}
		}
	}
	if len(entrypoints) == 0 {
		t.Fatal("platform-deps.yaml pins no Traefik entrypoint ports")
	}
	for _, values := range valueSets(t) {
		for _, gateway := range render(t, values).all("Gateway") {
			listeners, _ := get(gateway, "spec", "listeners").([]any)
			for _, listener := range listeners {
				port, _ := get(listener, "port").(int)
				if !slices.Contains(entrypoints, port) {
					t.Errorf("%s: Gateway %s listener %s is on port %d, no Traefik entrypoint's %v",
						values, gateway.name(), str(listener, "name"), port, entrypoints)
				}
			}
		}
	}
}

// renderPlatformChartsEnv opts into TestEveryPlatformChartRendersWithItsValues, which downloads every chart;
// `mise run check:chart` sets it.
const renderPlatformChartsEnv = "OGCLOUD_RENDER_PLATFORM_CHARTS"

// Every third-party chart renders with its values, alone and with each install target's (D-0187): a value a chart
// stopped taking, or a target's values that no longer fit, fails here instead of in a cluster.
func TestEveryPlatformChartRendersWithItsValues(t *testing.T) {
	if os.Getenv(renderPlatformChartsEnv) != "true" {
		t.Skipf("set %s=true; it downloads every chart in platform-deps.yaml", renderPlatformChartsEnv)
	}
	deps := loadPlatformDeps(t)
	cache := filepath.Join("..", "..", "build", "charts")
	for _, chart := range deps.Charts {
		t.Run(chart.Name, func(t *testing.T) {
			// A directory per pin: an archive is named after the chart's own name, which may differ from chart.Chart.
			dir := filepath.Join(cache, chart.Name, chart.Version)
			archives, _ := filepath.Glob(filepath.Join(dir, "*.tgz"))
			if len(archives) != 1 {
				if err := errors.Join(os.RemoveAll(dir), os.MkdirAll(dir, 0o750)); err != nil {
					t.Fatal(err)
				}
				run(t, "helm", "pull", chart.Chart, "--repo", chart.Repository, "--version", chart.Version, "--destination", dir)
				archives, _ = filepath.Glob(filepath.Join(dir, "*.tgz"))
			}
			if len(archives) != 1 {
				t.Fatalf("helm pull left %v in %s", archives, dir)
			}
			archive := archives[0]
			base := writeYAML(t, chart.Values)
			for _, target := range append([]string{""}, deps.InstallTargets...) {
				args := []string{"template", chart.Name, archive, "--namespace", chart.Namespace, "-f", base}
				if target != "" {
					values, ok := chart.Targets[target]
					if !ok {
						continue
					}
					args = append(args, "-f", writeYAML(t, values))
				}
				run(t, "helm", args...)
			}
		})
	}
}

func writeYAML(t *testing.T, value any) string {
	t.Helper()
	data, err := yaml.Marshal(value)
	if err != nil {
		t.Fatal(err)
	}
	file := filepath.Join(t.TempDir(), "values.yaml")
	if err := os.WriteFile(file, data, 0o600); err != nil {
		t.Fatal(err)
	}
	return file
}

func run(t *testing.T, name string, args ...string) {
	t.Helper()
	command := exec.CommandContext(t.Context(), name, args...)
	if output, err := command.CombinedOutput(); err != nil {
		t.Fatalf("%s %s: %v\n%s", name, strings.Join(args, " "), err, output)
	}
}
