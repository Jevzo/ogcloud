package deploytest

import (
	"maps"
	"slices"
	"strconv"
	"strings"
	"testing"

	"go.yaml.in/yaml/v3"
)

// workloads are the pods the release runs itself, subcharts included.
func (r rendered) workloads() []object {
	return slices.Concat(r.all("Deployment"), r.all("StatefulSet"), r.all("Job"), r.all("CronJob"))
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
	if o.kind() == "CronJob" {
		spec, _ := get(o, "spec", "jobTemplate", "spec", "template", "spec").(map[string]any)
		return spec
	}
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
					// Jobs run to completion; only long-running containers need probes.
					longRunning := workload.kind() != "Job" && workload.kind() != "CronJob"
					checkContainer(t, where+" "+str(container, "name"), container, longRunning)
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

// Proxies check players with Mojang on every install but the minikube dev profile, where bots join without accounts
// (D-0162).
func TestProxiesAreOnlineUnlessTheDevProfileTurnsItOff(t *testing.T) {
	config := func(values ...string) string {
		return str(render(t, values...).find(t, "ConfigMap", "ogcloud-controller"), "data", "application.yaml")
	}
	for _, values := range valueSets(t) {
		if !containsLine(config(values), "proxy-online-mode: true") {
			t.Errorf("%s: proxies are not in online mode", values)
		}
	}
	if !containsLine(config(minikubeValues, "../../deploy/dev/values.yaml"), "proxy-online-mode: false") {
		t.Error("the dev profile's proxies are in online mode")
	}
}

// fabric8's leader election reads, creates and patches its Lease (D-0007); without patch no replica ever leads, and
// no leader loop runs.
// The controller's RBAC allows each call it makes; a missing verb fails at run time only. It patches a game pod's
// safe-to-evict annotation when its players come or go (D-0047), and fabric8's leader election patches its Lease.
func TestTheControllerMayMakeEveryCallItMakes(t *testing.T) {
	objects := render(t, minikubeValues)
	for _, want := range []struct {
		role, resource string
		verbs          []string
	}{
		{"ogcloud-controller", "pods", []string{"get", "list", "watch", "create", "delete", "patch"}},
		{"ogcloud-controller", "persistentvolumeclaims", []string{"get", "list", "watch", "create", "patch", "delete"}},
		{"ogcloud-controller-leader", "leases", []string{"get", "create", "update", "patch"}},
	} {
		rules, _ := get(objects.find(t, "Role", want.role), "rules").([]any)
		for _, verb := range want.verbs {
			if !slices.ContainsFunc(rules, func(rule any) bool {
				return slices.Contains(stringList(get(rule, "resources")), want.resource) &&
					slices.Contains(stringList(get(rule, "verbs")), verb)
			}) {
				t.Errorf("Role %s does not let the controller %s %s", want.role, verb, want.resource)
			}
		}
	}
}

// The load balancer's per-address limits keep its own defaults unless set (D-0118); load runs raise them, since a
// test host's bots share one address (D-0175).
func TestTheLoadBalancerTakesOnlyTheLimitsItIsGiven(t *testing.T) {
	limits := func(values ...string) map[string]string {
		t.Helper()
		env := podEnv(t, firstContainer(t, render(t, values...).find(t, "Deployment", "ogcloud-loadbalancer")), nil)
		set := map[string]string{}
		for _, name := range []string{"OGCLOUD_LB_MAX_OPEN_PER_IP", "OGCLOUD_LB_NEW_PER_SECOND", "OGCLOUD_LB_NEW_BURST"} {
			if value, ok := env[name]; ok {
				set[name] = value
			}
		}
		return set
	}
	if set := limits(minikubeValues); len(set) != 0 {
		t.Errorf("the chart sets limits nobody asked for: %v", set)
	}
	raised := valuesFile(t, "loadbalancer: {limits: {openPerAddress: 1000, newBurst: 500}}\n")
	if set := limits(minikubeValues, raised); !maps.Equal(set, map[string]string{
		"OGCLOUD_LB_MAX_OPEN_PER_IP": "1000", "OGCLOUD_LB_NEW_BURST": "500",
	}) {
		t.Errorf("raised limits = %v", set)
	}
}

// A load run admits only its own host to the Minecraft port (D-0175); by default the Service admits everyone.
func TestTheMinecraftServiceAdmitsOnlyTheGivenSources(t *testing.T) {
	ranges := func(values ...string) []string {
		t.Helper()
		return stringList(get(render(t, values...).find(t, "Service", "ogcloud-loadbalancer"), "spec", "loadBalancerSourceRanges"))
	}
	if got := ranges(minikubeValues); len(got) != 0 {
		t.Errorf("the Service limits its sources unasked: %v", got)
	}
	limited := valuesFile(t, "loadbalancer: {service: {sourceRanges: [198.51.100.7/32]}}\n")
	if got := ranges(minikubeValues, limited); !slices.Equal(got, []string{"198.51.100.7/32"}) {
		t.Errorf("source ranges = %v", got)
	}
}

// The load balancer requires a PROXY header from its trusted proxies (D-0118), so on Hetzner the cloud's load
// balancer must send one, or every player is refused.
func TestHetznersMinecraftLoadBalancerSendsTheProxyProtocol(t *testing.T) {
	service := render(t, hetznerValues).find(t, "Service", "ogcloud-loadbalancer")
	if got := str(service, "metadata", "annotations", "load-balancer.hetzner.cloud/uses-proxyprotocol"); got != "true" {
		t.Errorf("uses-proxyprotocol = %q, want true behind loadbalancer.trustedProxies", got)
	}
}

// The controller's lifecycle timeouts are chart values (D-0179); unset ones keep the controller's own defaults.
func TestTheControllerTakesOnlyTheLifecycleTimeoutsGiven(t *testing.T) {
	timeouts := func(values ...string) any {
		t.Helper()
		var config map[string]any
		data := str(render(t, values...).find(t, "ConfigMap", "ogcloud-controller"), "data", "application.yaml")
		if err := yaml.Unmarshal([]byte(data), &config); err != nil {
			t.Fatalf("the controller's config: %v", err)
		}
		return get(config, "ogcloud", "controller", "lifecycle", "timeouts")
	}
	if got := timeouts(minikubeValues); got != nil {
		t.Errorf("the chart sets timeouts nobody asked for: %v", got)
	}
	given := valuesFile(t, "controller: {lifecycle: {timeouts: {starting: 7m, transferGrace: 45s}}}\n")
	want := map[string]any{"starting": "7m", "transfer-grace": "45s"}
	if got, _ := timeouts(minikubeValues, given).(map[string]any); !maps.Equal(got, want) {
		t.Errorf("timeouts = %v, want %v", got, want)
	}
}
