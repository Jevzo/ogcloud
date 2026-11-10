package deploytest

import (
	"bytes"
	"context"
	"fmt"
	"os"
	"os/exec"
	"path/filepath"
	"reflect"
	"strings"
	"testing"
)

// baseRefEnv names the release the chart must upgrade from; by default the last pushed state, the closest N-1 until
// tags exist (D-0172).
const baseRefEnv = "OGCLOUD_CHART_BASE"

// Kubernetes refuses to change these on an existing object, so a chart that changes them cannot upgrade a release
// without deleting what holds them, Valkey's data among them.
var immutable = map[string][][]string{
	"Deployment":  {{"spec", "selector"}},
	"StatefulSet": {{"spec", "selector"}, {"spec", "serviceName"}, {"spec", "volumeClaimTemplates"}, {"spec", "podManagementPolicy"}},
	"DaemonSet":   {{"spec", "selector"}},
}

// What the heavy upgrade test finds only on main (D-0165), checked on every change: no immutable field differs from
// the base release's rendering.
func TestTheChartChangesNoFieldAnUpgradeCannotChange(t *testing.T) {
	ref := os.Getenv(baseRefEnv)
	if ref == "" {
		ref = "origin/v2"
	}
	if err := exec.CommandContext(t.Context(), "git", "rev-parse", "--verify", "-q", ref+"^{commit}").Run(); err != nil {
		t.Skipf("%s is not in this clone; set %s to the release to upgrade from", ref, baseRefEnv)
	}
	trackChart(t)
	base := baseChart(t, ref)
	for _, values := range []string{"minikube.yaml", "hetzner.yaml", "external.yaml"} {
		t.Run(values, func(t *testing.T) {
			if _, err := os.Stat(filepath.Join(base, "ci", values)); err != nil {
				t.Skipf("%s has no %s", ref, values)
			}
			before := renderChart(t, base, filepath.Join(base, "ci", values))
			after := renderChart(t, chartDir, filepath.Join(chartDir, "ci", values))
			for key, previous := range before {
				current, ok := after[key]
				if !ok {
					continue
				}
				for _, path := range immutable[previous.kind()] {
					if was, now := get(map[string]any(previous), path...), get(map[string]any(current), path...); !reflect.DeepEqual(was, now) {
						t.Errorf("%s %s changes %s, which an upgrade from %s cannot:\n%v\n->\n%v",
							previous.kind(), previous.name(), strings.Join(path, "."), ref, was, now)
					}
				}
			}
		})
	}
}

// baseChart extracts the chart as ref has it.
func baseChart(t *testing.T, ref string) string {
	t.Helper()
	dir := t.TempDir()
	archive := exec.CommandContext(t.Context(), "git", "archive", ref, "deploy/helm/ogcloud")
	archive.Dir = filepath.Join("..", "..")
	var tarball bytes.Buffer
	archive.Stdout = &tarball
	if err := archive.Run(); err != nil {
		t.Fatalf("git archive %s: %v", ref, err)
	}
	extract := exec.CommandContext(context.Background(), "tar", "-x", "-C", dir)
	extract.Stdin = &tarball
	if output, err := extract.CombinedOutput(); err != nil {
		t.Fatalf("extract the chart of %s: %v\n%s", ref, err, output)
	}
	return filepath.Join(dir, "deploy", "helm", "ogcloud")
}

// renderChart renders chart with values, by kind and name.
func renderChart(t *testing.T, chart, values string) map[string]object {
	t.Helper()
	output, err := exec.CommandContext(t.Context(), "helm", "template", "ogcloud", chart, "--namespace", namespace, "-f", values).Output()
	if err != nil {
		t.Fatalf("render %s: %v", chart, err)
	}
	objects := map[string]object{}
	for _, o := range decodeRendered(t, output) {
		objects[fmt.Sprintf("%s/%s", o.kind(), o.name())] = o
	}
	return objects
}
