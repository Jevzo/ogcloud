package deploytest

import (
	"path/filepath"
	"testing"
)

const helmChartsScript = "../../deploy/k3s/helmcharts.sh"

// installed runs helmcharts.sh's installed for the chart ogcloud against a kubectl that answers with job (the
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
	script, err := filepath.Abs(helmChartsScript)
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
