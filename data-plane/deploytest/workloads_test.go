package deploytest

import (
	"testing"
)

// workloads are the Deployments and Jobs the chart runs itself.
func (r rendered) workloads() []object {
	return append(r.all("Deployment"), r.all("Job")...)
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
				if get(pod, "automountServiceAccountToken") != false {
					t.Errorf("%s automounts a service-account token", where)
				}
				if str(pod, "priorityClassName") != "ogcloud-platform" {
					t.Errorf("%s has priority %q, want ogcloud-platform (D-0056)", where, str(pod, "priorityClassName"))
				}
				containers, _ := pod["containers"].([]any)
				for _, container := range containers {
					checkContainer(t, where+" "+str(container, "name"), container, workload.kind() == "Deployment")
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
