package deploytest

import (
	"path/filepath"
	"testing"
)

const convergedScript = "../../deploy/k3s/converged.sh"

// installed runs converged.sh's installed for the chart ogcloud against a kubectl that answers with job (the
// chart's job uid and successes), releases (the release's revisions and states, one per line) and the changed charts
// and earlier jobs install.sh recorded.
func installed(t *testing.T, job, releases, changed, jobsBefore string) bool {
	t.Helper()
	dir, env := fakes(t,
		map[string]string{"kubectl": `case "$*" in
  *"get job helm-install-"*) cat "$FAKE/job" ;;
  *"get helmchart"*) echo ogcloud ;;
  *"get secrets"*) cat "$FAKE/releases" ;;
  *) echo "unexpected kubectl $*" >&2; exit 1 ;;
esac
`},
		map[string]string{"job": job, "releases": releases, "changed.txt": changed, "jobs-before.txt": jobsBefore})
	script, err := filepath.Abs(convergedScript)
	if err != nil {
		t.Fatal(err)
	}
	driver := filepath.Join(dir, "driver.sh")
	writeFile(t, driver, "set -euo pipefail\nwork=$FAKE\nsource "+script+"\ninstalled ogcloud\n", 0o600)
	out, err := runScript(t, env, driver)
	if err != nil && out != "" {
		t.Logf("installed printed: %s", out)
	}
	return err == nil
}

// k3s may replace a chart's job after one succeeded, as when it retries a failed release as an upgrade; the new job
// then still runs the release's hooks (the template restore among them) while the old one's success would pass.
func TestAChartIsInstalledOnlyOnceItsNewestReleaseIsDeployed(t *testing.T) {
	for _, tc := range []struct {
		name, job, releases, changed, jobsBefore string
		want                                     bool
	}{
		{name: "deployed", job: "uid-1 1", releases: "1 deployed\n", want: true},
		{name: "retried as an upgrade", job: "uid-1 1", releases: "1 failed\n2 pending-upgrade\n"},
		{name: "revisions sort as numbers", job: "uid-1 1", releases: "9 superseded\n10 deployed\n", want: true},
		{name: "job still running", job: "uid-1 ", releases: "1 deployed\n"},
		{name: "changed chart's old job", job: "uid-1 1", releases: "2 deployed\n", changed: "ogcloud\n", jobsBefore: "helm-install-ogcloud uid-1\n"},
		{name: "changed chart's new job", job: "uid-2 1", releases: "2 deployed\n", changed: "ogcloud\n", jobsBefore: "helm-install-ogcloud uid-1\n", want: true},
	} {
		t.Run(tc.name, func(t *testing.T) {
			if got := installed(t, tc.job, tc.releases, tc.changed, tc.jobsBefore); got != tc.want {
				t.Errorf("installed = %v, want %v", got, tc.want)
			}
		})
	}
}

// unreadyPods runs converged.sh's unready_pods against a kubectl that lists pods as name, deletion time and Ready
// status, tab-separated, one per line, as its jsonpath prints them.
func unreadyPods(t *testing.T, pods string) string {
	t.Helper()
	dir, env := fakes(t,
		map[string]string{"kubectl": `case "$*" in
  *"get pods --field-selector=status.phase!=Succeeded,status.phase!=Failed "*) cat "$FAKE/pods" ;;
  *) echo "unexpected kubectl $*" >&2; exit 1 ;;
esac
`},
		map[string]string{"pods": pods})
	script, err := filepath.Abs(convergedScript)
	if err != nil {
		t.Fatal(err)
	}
	driver := filepath.Join(dir, "driver.sh")
	writeFile(t, driver, "set -euo pipefail\nsource "+script+"\nunready_pods\n", 0o600)
	out, err := runScript(t, env, driver)
	if err != nil {
		t.Fatalf("unready_pods failed: %v: %s", err, out)
	}
	return out
}

// install.sh's last wait: kubectl wait followed a replaced revision's pod that was shutting down, unready, until it
// was deleted, and then until the timeout.
func TestPodsBeingDeletedAreNotWaitedFor(t *testing.T) {
	for _, tc := range []struct{ name, pods, want string }{
		{name: "ready", pods: "api-new\t\tTrue\n"},
		{name: "starting", pods: "api-new\t\tFalse\n", want: "api-new\n"},
		{name: "no conditions yet", pods: "api-new\t\t\n", want: "api-new\n"},
		{name: "shutting down", pods: "api-old\t2026-11-16T00:00:00Z\tFalse\napi-new\t\tTrue\n"},
		{name: "some not ready", pods: "a\t\tTrue\nb\t\tFalse\nc\t\tFalse\n", want: "b\nc\n"},
	} {
		t.Run(tc.name, func(t *testing.T) {
			if got := unreadyPods(t, tc.pods); got != tc.want {
				t.Errorf("unready_pods printed %q, want %q", got, tc.want)
			}
		})
	}
}
