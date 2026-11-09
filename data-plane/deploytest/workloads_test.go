package deploytest

import (
	"slices"
	"strconv"
	"strings"
	"testing"
)

// workloads are the pods the release runs itself, subcharts included.
func (r rendered) workloads() []object {
	return slices.Concat(r.all("Deployment"), r.all("StatefulSet"), r.all("Job"))
}

// mountsToken tells whether pod gets a service-account token automounted, by its own setting or its account's.
func (r rendered) mountsToken(pod map[string]any) bool {
	if setting, ok := pod["automountServiceAccountToken"].(bool); ok {
		return setting
	}
	account := str(pod, "serviceAccountName")
	for _, o := range r.all("ServiceAccount") {
		if o.name() == account {
			return get(o, "automountServiceAccountToken") != false
		}
	}
	return true
}

func (o object) podSpec() map[string]any {
	spec, _ := get(o, "spec", "template", "spec").(map[string]any)
	return spec
}

func (r rendered) all(kind string) []object {
	var matching []object
	for _, o := range r {
		if o.kind() == kind {
			matching = append(matching, o)
		}
	}
	return matching
}

func TestPlatformPodsRunRestrictedWithResources(t *testing.T) {
	for _, values := range valueSets(t) {
		t.Run(values, func(t *testing.T) {
			objects := render(t, values)
			for _, workload := range objects.workloads() {
				pod := workload.podSpec()
				where := workload.kind() + " " + workload.name()
				if get(pod, "securityContext", "runAsNonRoot") != true || get(pod, "securityContext", "runAsUser") != 1000 {
					t.Errorf("%s does not run as uid 1000", where)
				}
				if objects.mountsToken(pod) {
					t.Errorf("%s automounts a service-account token", where)
				}
				if str(pod, "priorityClassName") != "ogcloud-platform" {
					t.Errorf("%s has priority %q, want ogcloud-platform (D-0056)", where, str(pod, "priorityClassName"))
				}
				containers, _ := pod["containers"].([]any)
				for _, container := range containers {
					checkContainer(t, where+" "+str(container, "name"), container, workload.kind() != "Job")
				}
			}
		})
	}
}

func checkContainer(t *testing.T, where string, container any, long bool) {
	t.Helper()
	security := get(container, "securityContext")
	if get(security, "readOnlyRootFilesystem") != true || get(security, "allowPrivilegeEscalation") != false {
		t.Errorf("%s is not read-only and unprivileged", where)
	}
	if drop, _ := get(security, "capabilities", "drop").([]any); len(drop) != 1 || drop[0] != "ALL" {
		t.Errorf("%s keeps capabilities", where)
	}
	if get(container, "resources", "requests", "memory") == nil || get(container, "resources", "limits", "memory") == nil {
		t.Errorf("%s has no memory request and limit", where)
	}
	if long && (get(container, "readinessProbe") == nil || get(container, "livenessProbe") == nil) {
		t.Errorf("%s has no readiness and liveness probes", where)
	}
}

func TestOnlyTheGatewayMayCallTheController(t *testing.T) {
	config := str(render(t, minikubeValues).find(t, "ConfigMap", "ogcloud-controller"), "data", "application.yaml")
	if !containsLine(config, "- system:serviceaccount:ogcloud:ogcloud-gateway") {
		t.Errorf("the controller's allowed callers are not the gateway's account:\n%s", config)
	}
}

// Hooks after install wait for what only `bootstrap infisical` creates, the synced secrets, so it must run first;
// Helm runs hooks one at a time, lowest weight first.
func TestBootstrapRunsBeforeEveryOtherPostInstallHook(t *testing.T) {
	for _, values := range valueSets(t) {
		t.Run(values, func(t *testing.T) {
			weight := func(o object) int {
				w, _ := strconv.Atoi(str(o, "metadata", "annotations", "helm.sh/hook-weight"))
				return w
			}
			objects := render(t, values)
			bootstrap := weight(objects.find(t, "Job", "ogcloud-bootstrap-infisical"))
			for _, o := range objects {
				hook := str(o, "metadata", "annotations", "helm.sh/hook")
				if o.name() != "ogcloud-bootstrap-infisical" && strings.Contains(hook, "post-install") && weight(o) <= bootstrap {
					t.Errorf("%s %s runs after install with weight %d, not after bootstrap (%d)", o.kind(), o.name(), weight(o), bootstrap)
				}
			}
		})
	}
}

// A node drain may take only one pod at a time from a workload that runs several.
func TestWorkloadsWithSeveralPodsHaveADisruptionBudget(t *testing.T) {
	for _, values := range valueSets(t) {
		t.Run(values, func(t *testing.T) {
			objects := render(t, values)
			budgets := objects.all("PodDisruptionBudget")
			for _, workload := range append(objects.all("Deployment"), objects.all("StatefulSet")...) {
				replicas, _ := get(workload, "spec", "replicas").(int)
				if replicas < 2 {
					continue
				}
				labels, _ := get(workload, "spec", "template", "metadata", "labels").(map[string]any)
				covered := false
				for _, budget := range budgets {
					selector := get(budget, "spec", "selector", "matchLabels")
					stringLabels := map[string]string{}
					for key, value := range labels {
						stringLabels[key], _ = value.(string)
					}
					covered = covered || (labelsMatch(selector, stringLabels) && get(budget, "spec", "maxUnavailable") == 1)
				}
				if !covered {
					t.Errorf("%s %s runs %d pods without a budget of one", workload.kind(), workload.name(), replicas)
				}
			}
		})
	}
}
