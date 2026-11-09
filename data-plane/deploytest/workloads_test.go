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
				if str(pod, "priorityClassName") != "ogcloud-platform" && !preInstallHook(workload) {
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

func preInstallHook(o object) bool {
	return strings.Contains(str(o, "metadata", "annotations", "helm.sh/hook"), "pre-install")
}

// Helm runs pre-install hooks before it creates the release's other resources, so a hook's pod may use only what
// other pre-install hooks create; anything else would leave it pending forever and the install with it.
func TestPreInstallHooksUseOnlyWhatExistsBeforeTheRelease(t *testing.T) {
	for _, values := range valueSets(t) {
		t.Run(values, func(t *testing.T) {
			objects := render(t, values)
			early := map[string]bool{}
			for _, o := range objects {
				if preInstallHook(o) {
					early[o.kind()+"/"+o.name()] = true
				}
			}
			for _, job := range objects.all("Job") {
				if !preInstallHook(job) {
					continue
				}
				pod := job.podSpec()
				needs := []string{"ServiceAccount/" + str(pod, "serviceAccountName")}
				if class := str(pod, "priorityClassName"); class != "" {
					needs = append(needs, "PriorityClass/"+class)
				}
				volumes, _ := get(pod, "volumes").([]any)
				for _, volume := range volumes {
					if name := str(volume, "configMap", "name"); name != "" {
						needs = append(needs, "ConfigMap/"+name)
					}
					if name := str(volume, "secret", "secretName"); name != "" {
						needs = append(needs, "Secret/"+name)
					}
				}
				for _, need := range needs {
					if !early[need] {
						t.Errorf("pre-install Job %s needs %s, which Helm creates only after the hooks", job.name(), need)
					}
				}
			}
		})
	}
}

// Kubernetes resolves a probe's named port among its own container's ports only; any other name leaves the
// probe unable to run and the pod never ready.
func TestProbesNameOnlyTheirContainersPorts(t *testing.T) {
	for _, values := range valueSets(t) {
		for _, workload := range render(t, values).workloads() {
			containers, _ := get(workload.podSpec(), "containers").([]any)
			for _, container := range containers {
				var names []string
				ports, _ := get(container, "ports").([]any)
				for _, port := range ports {
					names = append(names, str(port, "name"))
				}
				for _, probe := range []string{"startupProbe", "readinessProbe", "livenessProbe"} {
					for _, handler := range []string{"httpGet", "tcpSocket", "grpc"} {
						if port, named := get(container, probe, handler, "port").(string); named && !slices.Contains(names, port) {
							t.Errorf("%s: %s %s container %s: %s names port %q, which the container does not declare",
								values, workload.kind(), workload.name(), str(container, "name"), probe, port)
						}
					}
				}
			}
		}
	}
}

// cert-manager writes PKCS#1 or SEC1 keys by default, which Java's PEM keystores, Kafka's among them, refuse.
func TestEveryCertificateKeyIsPKCS8(t *testing.T) {
	for _, values := range valueSets(t) {
		for _, certificate := range render(t, values).all("Certificate") {
			if got := str(certificate, "spec", "privateKey", "encoding"); got != "PKCS8" {
				t.Errorf("%s: Certificate %s has key encoding %q, want PKCS8", values, certificate.name(), got)
			}
		}
	}
}

// Kubernetes' service links put <SERVICE>_PORT=tcp://… into every pod, which Spring's relaxed binding reads as
// ogcloud.<service>.port: ogcloud-valkey's broke both Spring services.
func TestPodsGetNoServiceLinks(t *testing.T) {
	for _, values := range valueSets(t) {
		for _, workload := range render(t, values).workloads() {
			if get(workload.podSpec(), "enableServiceLinks") != false {
				t.Errorf("%s: %s %s keeps service links", values, workload.kind(), workload.name())
			}
		}
	}
}
