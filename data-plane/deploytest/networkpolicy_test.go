package deploytest

import (
	"fmt"
	"net/netip"
	"strings"
	"testing"
)

// The rendered NetworkPolicies, evaluated as Kubernetes does, against the flows D-0154 allows and denies. Strimzi
// writes the Kafka listener's own policy, so Kafka is checked through its networkPolicyPeers instead.

// endpoint is a pod (namespace set) or an address outside the cluster.
type endpoint struct {
	namespace string
	labels    map[string]string
	ip        string
}

func component(name string) endpoint {
	return endpoint{namespace: namespace, labels: map[string]string{
		"app.kubernetes.io/name": "ogcloud", "app.kubernetes.io/instance": "ogcloud", "app.kubernetes.io/component": name,
	}}
}

func pod(ns string, labels ...string) endpoint {
	e := endpoint{namespace: ns, labels: map[string]string{}}
	for i := 0; i+1 < len(labels); i += 2 {
		e.labels[labels[i]] = labels[i+1]
	}
	return e
}

var (
	api             = component("api")
	web             = component("web")
	backupJob       = component("backup")
	bootstrapJob    = component("bootstrap")
	controller      = component("controller")
	gateway         = component("gateway")
	loadbalancer    = component("loadbalancer")
	infisicalPod    = component("infisical")
	infisicalValkey = component("infisical-valkey")
	appValkey       = component("valkey")
	seaweed         = pod(namespace, "app.kubernetes.io/name", "seaweedfs")
	postgresPod     = pod(namespace, "cnpg.io/cluster", "ogcloud-postgres")
	backend         = pod("ogcloud-servers", "cloud.ogwars.io/role", "backend")
	proxy           = pod("ogcloud-servers", "cloud.ogwars.io/role", "proxy")
	traefik         = pod("traefik", "app.kubernetes.io/name", "traefik")
	prometheus      = pod("monitoring", "app.kubernetes.io/name", "prometheus")
	tempo           = pod("monitoring", "app.kubernetes.io/name", "tempo")
	secretsOperator = pod("infisical-operator", "control-plane", "controller-manager")
	coreDNS         = pod("kube-system", "k8s-app", "kube-dns")
	intruder        = pod("default", "app", "anything")
	internet        = endpoint{ip: "142.250.74.14"}
	privateHost     = endpoint{ip: "10.20.30.40"}
	// externalS3Cidrs in ci/hetzner.yaml.
	objectStorage = endpoint{ip: "203.0.113.10"}
	// backup.s3.cidrs in ci/hetzner.yaml (D-0166).
	backupStorage = endpoint{ip: "198.51.100.10"}
)

type flow struct {
	from, to endpoint
	port     int
	protocol string
	allowed  bool
	why      string
}

func tcp(from, to endpoint, port int, allowed bool, why string) flow {
	return flow{from, to, port, "TCP", allowed, why}
}

func TestNetworkPoliciesAllowOnlyWhatEachWorkloadUses(t *testing.T) {
	flows := []flow{
		tcp(traefik, api, 8080, true, "Traefik serves the API (D-0108)"),
		tcp(intruder, api, 8080, false, "nothing else reaches the API"),
		tcp(traefik, web, 8080, true, "Traefik serves the dashboard (D-0214)"),
		tcp(intruder, web, 8080, false, "nothing else reaches the dashboard"),
		tcp(web, api, 8080, false, "the dashboard's server calls nothing; browsers call the API"),
		tcp(web, internet, 443, false, "the dashboard's server calls nothing outside"),
		tcp(backend, api, 8080, false, "game pods never reach the API"),
		tcp(proxy, backend, 25565, true, "proxies forward players to backends"),
		tcp(backend, backend, 25565, false, "backends never reach each other: the forwarding token is in the handshake (D-0065)"),
		tcp(intruder, backend, 25565, false, "only proxies reach backends"),
		tcp(loadbalancer, proxy, 25565, true, "the load balancer forwards players"),
		tcp(internet, loadbalancer, 25565, true, "players connect from anywhere"),
		tcp(backend, gateway, 9443, true, "game pods hold sessions with the gateway"),
		tcp(proxy, gateway, 9443, true, "game pods hold sessions with the gateway"),
		tcp(backend, internet, 443, false, "backends run third-party plugins and get no internet"),
		tcp(proxy, internet, 443, true, "online mode asks Mojang's session servers"),
		tcp(proxy, privateHost, 443, false, "the public egress excludes private ranges"),
		tcp(backend, seaweed, 8333, true, "presigned template downloads"),
		tcp(backend, appValkey, 6379, false, "game pods hold no infrastructure access (D-0008)"),
		tcp(backend, postgresPod, 5432, false, "game pods hold no infrastructure access (D-0008)"),
		{backend, coreDNS, 53, "UDP", true, "every pod resolves names"},
		tcp(api, internet, 443, false, "the API calls nothing outside"),
		tcp(controller, internet, 443, true, "the controller downloads bundle plugins from Modrinth (D-0079)"),
		tcp(controller, infisicalPod, 8443, true, "the controller rotates the forwarding tokens (D-0152)"),
		tcp(api, infisicalPod, 8443, false, "the API reads its secrets from files, not Infisical"),
		tcp(secretsOperator, infisicalPod, 8443, true, "the Secrets Operator syncs from Infisical"),
		tcp(gateway, controller, 9090, true, "the gateway resolves logins at the controller"),
		tcp(api, controller, 9090, false, "only the gateway calls the controller"),
		tcp(api, postgresPod, 5432, true, "the API owns the schema"),
		tcp(gateway, postgresPod, 5432, false, "the gateway reads Valkey only"),
		tcp(infisicalPod, infisicalValkey, 6379, true, "Infisical's own Valkey (D-0151)"),
		tcp(api, infisicalValkey, 6379, false, "only Infisical uses its Valkey"),
		tcp(infisicalPod, appValkey, 6379, false, "Infisical has left the app Valkey (D-0151)"),
		tcp(gateway, appValkey, 26379, true, "Sentinel discovery"),
		tcp(intruder, appValkey, 6379, false, "nothing else reaches Valkey"),
		tcp(gateway, privateHost, 443, true, "the gateway reviews tokens at the API server"),
		tcp(api, privateHost, 443, false, "the API never talks to Kubernetes"),
		tcp(prometheus, api, 8081, false, "no scraping without monitoring"),
		tcp(api, tempo, 4318, false, "no traces without monitoring"),
		// The platform's own paths (D-0154): each workload reaches its dependencies and nothing else does.
		tcp(controller, postgresPod, 5432, true, "the controller reads and writes control-plane state"),
		tcp(infisicalPod, postgresPod, 5432, true, "Infisical's database is in the shared cluster"),
		tcp(loadbalancer, postgresPod, 5432, false, "the load balancer holds no database access"),
		tcp(backupJob, postgresPod, 5432, false, "the template copy needs no database"),
		tcp(api, appValkey, 6379, true, "the API's sessions, rate limits and read models"),
		tcp(controller, appValkey, 6379, true, "the controller writes the read models"),
		tcp(api, seaweed, 8333, true, "template uploads stream into S3 (D-0087)"),
		tcp(controller, seaweed, 8333, true, "the controller stores the runtime bundles"),
		tcp(intruder, seaweed, 8333, false, "nothing else reaches S3"),
		tcp(backend, seaweed, 9333, false, "SeaweedFS's master port has no authentication"),
		tcp(controller, privateHost, 443, true, "the controller manages pods through the API server"),
		tcp(bootstrapJob, infisicalPod, 8443, true, "bootstrap sets up Infisical"),
		tcp(bootstrapJob, privateHost, 443, true, "bootstrap writes the root Secrets"),
		tcp(intruder, infisicalPod, 8443, false, "Infisical is no one else's"),
		tcp(intruder, controller, 9090, false, "only the gateway calls the controller"),
		tcp(intruder, gateway, 9443, false, "only game pods hold gateway sessions"),
		// Port 443 to anywhere stays open to the API server until Terraform names its addresses (T-0802).
		tcp(gateway, internet, 80, false, "the gateway calls nothing outside"),
		tcp(loadbalancer, internet, 443, false, "the load balancer calls nothing outside"),
	}
	objects := render(t, minikubeValues)
	for _, f := range flows {
		if got := objects.allows(t, f.from, f.to, f.port, f.protocol); got != f.allowed {
			t.Errorf("%s -> %s :%d allowed %v, want %v: %s", describe(f.from), describe(f.to), f.port, got, f.allowed, f.why)
		}
	}

	monitored := render(t, hetznerValues)
	for _, f := range []flow{
		tcp(prometheus, api, 8081, true, "Prometheus scrapes the API's management port (D-0190)"),
		tcp(prometheus, api, 8080, false, "Prometheus never reaches the API's own port, where X-Forwarded-For counts (D-0190)"),
		tcp(prometheus, controller, 8081, true, "Prometheus scrapes the controller"),
		tcp(prometheus, gateway, 8081, true, "Prometheus scrapes the gateway"),
		tcp(api, tempo, 4318, true, "traces go to Tempo"),
		tcp(backend, objectStorage, 443, true, "backends download templates from Hetzner Object Storage"),
		tcp(backend, internet, 443, false, "S3's addresses, not the internet (D-0154)"),
		tcp(postgresPod, backupStorage, 443, true, "PostgreSQL archives into the backup target (D-0166)"),
		tcp(backend, backupStorage, 443, false, "game pods never reach the backups"),
		tcp(api, backupStorage, 443, false, "the API never reaches the backups"),
		tcp(backupJob, objectStorage, 443, true, "the template copy reads the data bucket"),
		tcp(backupJob, backupStorage, 443, true, "the template copy writes the backup target"),
		tcp(backupJob, internet, 443, false, "the template copy reaches nothing else"),
	} {
		if got := monitored.allows(t, f.from, f.to, f.port, f.protocol); got != f.allowed {
			t.Errorf("hetzner: %s -> %s :%d allowed %v, want %v: %s", describe(f.from), describe(f.to), f.port, got, f.allowed, f.why)
		}
	}
}

func TestOnlyOgCloudsServicesReachKafkasListener(t *testing.T) {
	kafka := render(t, minikubeValues).find(t, "Kafka", "ogcloud")
	listeners, _ := get(kafka, "spec", "kafka", "listeners").([]any)
	if len(listeners) != 1 {
		t.Fatalf("Kafka has %d listeners, want 1", len(listeners))
	}
	peers, _ := get(listeners[0], "networkPolicyPeers").([]any)
	allowed := func(e endpoint) bool {
		for _, peer := range peers {
			if peerMatches(t, peer, namespace, e) {
				return true
			}
		}
		return false
	}
	for _, client := range []endpoint{api, controller, gateway, loadbalancer} {
		if !allowed(client) {
			t.Errorf("%s cannot reach Kafka", describe(client))
		}
	}
	for _, other := range []endpoint{infisicalPod, backend, proxy, intruder} {
		if allowed(other) {
			t.Errorf("%s reaches Kafka", describe(other))
		}
	}
	if len(peers) == 0 {
		t.Error("without networkPolicyPeers Strimzi opens the listener to every pod")
	}
}

// allows decides one connection as Kubernetes does: the source's egress policies and the destination's ingress
// policies must each allow it, where a pod no policy selects for a direction is open in that direction.
func (r rendered) allows(t *testing.T, from, to endpoint, port int, protocol string) bool {
	t.Helper()
	return r.direction(t, from, "Egress", "egress", "to", to, port, protocol) &&
		r.direction(t, to, "Ingress", "ingress", "from", from, port, protocol)
}

func (r rendered) direction(t *testing.T, subject endpoint, policyType, rulesKey, peersKey string, other endpoint, port int, protocol string) bool {
	t.Helper()
	if subject.namespace == "" {
		return true
	}
	selected := false
	for _, policy := range r.all("NetworkPolicy") {
		ns := str(policy, "metadata", "namespace")
		if ns != subject.namespace || !labelsMatch(get(policy, "spec", "podSelector", "matchLabels"), subject.labels) {
			continue
		}
		types, _ := get(policy, "spec", "policyTypes").([]any)
		if !contains(types, policyType) {
			continue
		}
		selected = true
		rules, _ := get(policy, "spec", rulesKey).([]any)
		for _, rule := range rules {
			if ruleMatches(t, rule, peersKey, ns, other, port, protocol) {
				return true
			}
		}
	}
	return !selected
}

func ruleMatches(t *testing.T, rule any, peersKey, policyNamespace string, other endpoint, port int, protocol string) bool {
	t.Helper()
	ports, _ := get(rule, "ports").([]any)
	portMatches := len(ports) == 0
	for _, p := range ports {
		want := str(p, "protocol")
		if want == "" {
			want = "TCP"
		}
		number, _ := get(p, "port").(int)
		portMatches = portMatches || (want == protocol && number == port)
	}
	if !portMatches {
		return false
	}
	peers, present := get(rule, peersKey).([]any)
	if !present || len(peers) == 0 {
		return true
	}
	for _, peer := range peers {
		if peerMatches(t, peer, policyNamespace, other) {
			return true
		}
	}
	return false
}

// peerMatches matches pods by namespace and pod selectors, and addresses outside the cluster by ipBlock.
func peerMatches(t *testing.T, peer any, policyNamespace string, e endpoint) bool {
	t.Helper()
	if block := get(peer, "ipBlock"); block != nil {
		if e.namespace != "" {
			return false
		}
		address := netip.MustParseAddr(e.ip)
		if !netip.MustParsePrefix(str(block, "cidr")).Contains(address) {
			return false
		}
		excepts, _ := get(block, "except").([]any)
		for _, except := range excepts {
			if netip.MustParsePrefix(except.(string)).Contains(address) {
				return false
			}
		}
		return true
	}
	if e.namespace == "" {
		return false
	}
	namespaceSelector, podSelector := get(peer, "namespaceSelector"), get(peer, "podSelector")
	if namespaceSelector == nil && e.namespace != policyNamespace {
		return false
	}
	if namespaceSelector != nil {
		if !labelsMatch(get(namespaceSelector, "matchLabels"), map[string]string{"kubernetes.io/metadata.name": e.namespace}) {
			return false
		}
	}
	return podSelector == nil || labelsMatch(get(podSelector, "matchLabels"), e.labels)
}

func labelsMatch(selector any, labels map[string]string) bool {
	want, _ := selector.(map[string]any)
	for key, value := range want {
		if labels[key] != value {
			return false
		}
	}
	return true
}

func contains(values []any, want string) bool {
	for _, value := range values {
		if value == want {
			return true
		}
	}
	return false
}

func describe(e endpoint) string {
	if e.namespace == "" {
		return e.ip
	}
	return fmt.Sprintf("%s%v", e.namespace, e.labels)
}

// External dependencies are reached on the ports their addresses name, not only the defaults; Sentinel names the
// primary's address at run time, so Valkey's default ports stay open too.
func TestExternalDependenciesAreReachedOnTheirOwnPorts(t *testing.T) {
	ports := valuesFile(t, "kafka: {bootstrapServers: 'kafka-1.example.com:19092,kafka-2.example.com:19092'}\n"+
		"valkey: {addresses: ['sentinel.example.com:16380'], sentinelMaster: ogcloud}\n")
	objects := render(t, chartDir+"/ci/external.yaml", ports)
	external := endpoint{ip: "192.0.2.50"}
	for _, f := range []flow{
		tcp(api, external, 19092, true, "the API reaches Kafka at the port bootstrapServers names"),
		tcp(controller, external, 16380, true, "the controller reaches Valkey's Sentinels at their port"),
		tcp(gateway, external, 6379, true, "the gateway reaches the primary Sentinel names"),
		tcp(api, external, 9093, false, "no port is open that no address names"),
	} {
		if got := objects.allows(t, f.from, f.to, f.port, f.protocol); got != f.allowed {
			t.Errorf("external: %s -> %s :%d allowed %v, want %v: %s", describe(f.from), describe(f.to), f.port, got, f.allowed, f.why)
		}
	}
}

func TestAnExternalAddressWithoutAPortIsRefused(t *testing.T) {
	_, err := helmTemplate(t, []string{chartDir + "/ci/external.yaml"}, "--set", "kafka.bootstrapServers=kafka.example.com")
	if err == nil || !strings.Contains(err.Error(), "names no port") {
		t.Errorf("a Kafka address without a port rendered: %v", err)
	}
}
