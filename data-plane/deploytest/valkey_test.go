package deploytest

import (
	"bytes"
	"context"
	"crypto/ecdsa"
	"crypto/elliptic"
	"crypto/rand"
	"crypto/tls"
	"crypto/x509"
	"crypto/x509/pkix"
	"encoding/pem"
	"fmt"
	"io"
	"math/big"
	"net"
	"os"
	"path/filepath"
	"strings"
	"testing"
	"time"

	"github.com/moby/moby/api/types/container"
	"github.com/testcontainers/testcontainers-go"
	tcexec "github.com/testcontainers/testcontainers-go/exec"
	"github.com/testcontainers/testcontainers-go/network"
	"github.com/testcontainers/testcontainers-go/wait"

	"github.com/jevzo/ogcloud/data-plane/internal/testimages"
	"github.com/jevzo/ogcloud/data-plane/internal/testvalkey"
	"github.com/jevzo/ogcloud/data-plane/pkg/valkey"
)

// The chart's Valkey scripts (files/valkey) run here as the StatefulSet runs them: uid 1000, TLS from a CA, the
// StatefulSet's environment, root users from files, app users from the chart's synced ACL (D-0044, D-0048).

const (
	valkeyScripts = chartDir + "/files/valkey"
	hetznerValues = chartDir + "/ci/hetzner.yaml"
	// Docker answers unknown .local names only after 16 s, which stalls Sentinel's lookups of a stopped peer;
	// CoreDNS answers at once, and .test is answered at once here.
	headless      = "ogcloud-valkey-headless.valkey.test"
	adminPassword = "admin-password"
)

// valkeyChart is what a rendered chart hands one Valkey StatefulSet's pods.
type valkeyChart struct {
	env map[string]string
	// root is its root Secret, each key's password being the key in lower case with dashes ("admin-password").
	root map[string]string
	// usersACL is the synced app users' file, empty for none, with each service's password "<service>-password".
	usersACL string
	// names are its certificate's DNS names, plus the test's headless domain.
	names []string
}

func renderValkey(t *testing.T, values, name string) valkeyChart {
	t.Helper()
	objects := render(t, values)
	statefulSet := objects.find(t, "StatefulSet", name)
	chart := valkeyChart{
		env:   podEnv(t, firstContainer(t, statefulSet), nil),
		root:  map[string]string{},
		names: append(certificateNames(t, objects, name+"-tls"), "*."+headless),
	}
	chart.env["HEADLESS_SERVICE"] = headless
	// The chart keeps the hourly default; the test needs to see a reload.
	chart.env["TLS_RELOAD_SECONDS"] = "1"
	volumes, _ := get(statefulSet.podSpec(), "volumes").([]any)
	for _, volume := range volumes {
		secret := str(volume, "secret", "secretName")
		switch str(volume, "name") {
		case "root":
			for _, rootSecret := range bootstrapConfig(t, objects).RootSecrets {
				if rootSecret.Name == secret {
					for key := range rootSecret.Keys {
						chart.root[key] = strings.ToLower(strings.ReplaceAll(key, "_", "-"))
					}
				}
			}
		case "users":
			passwords := map[string]string{}
			for _, service := range []string{"api", "controller", "gateway", "loadbalancer"} {
				passwords["VALKEY_"+strings.ToUpper(service)+"_PASSWORD"] = service + "-password"
			}
			chart.usersACL = syncedTemplate(t, objects, secret, "users.acl", passwords)
		}
	}
	if chart.root["ADMIN_PASSWORD"] != adminPassword {
		t.Fatalf("%s's root Secret has no admin password: %v", name, chart.root)
	}
	return chart
}

func TestValkeyStartsWithTLSUsersAndKeyspaceEvents(t *testing.T) {
	ca := newTestCA(t)
	chart := renderValkey(t, minikubeValues, "ogcloud-valkey")
	node := startValkeyNode(t, ca, chart, "", 0, "/scripts/start-valkey.sh")

	if out := cli(t, node, "ogcloud-api", "api-password", "SET", "k", "v"); out != "OK" {
		t.Errorf("the API's user cannot write: %q", out)
	}
	if out := cli(t, node, "ogcloud-gateway", "gateway-password", "GET", "k"); out != "v" {
		t.Errorf("the gateway's user cannot read: %q", out)
	}
	if out := cli(t, node, "ogcloud-gateway", "gateway-password", "SET", "k", "x"); !strings.Contains(out, "NOPERM") {
		t.Errorf("the gateway's user may write: %q", out)
	}
	if out := cli(t, node, "infisical", "infisical-password", "PING"); out == "PONG" {
		t.Error("Infisical's user is in the app Valkey (D-0151)")
	}
	if out := cli(t, node, "default", "", "PING"); out == "PONG" {
		t.Error("the default user is still on")
	}
	if out := cli(t, node, "ogcloud-admin", adminPassword, "CONFIG", "GET", "notify-keyspace-events"); !hasAll(out, "E", "g", "x") {
		t.Errorf("keyspace events for the indexed sessions (D-0095): %q", out)
	}

	// A rotated password reaches Valkey without a restart.
	node.files.update("/secrets/users/users.acl", strings.Replace(chart.usersACL, ">api-password ", ">rotated ", 1))
	runIn(t, node, "sh", "-c", ". /scripts/common.sh && /scripts/render-acl.sh && cli -h localhost -p 6379 ACL LOAD")
	if out := cli(t, node, "ogcloud-api", "rotated", "GET", "k"); out != "v" {
		t.Errorf("the rotated password was not loaded: %q", out)
	}

	// A certificate cert-manager renewed is served without a restart.
	renewed, key := ca.issue(t, chart.names...)
	node.files.update("/tls/tls.key", key)
	node.files.update("/tls/tls.crt", renewed)
	block, _ := pem.Decode([]byte(renewed))
	eventually(t, 30*time.Second, "the renewed certificate is served", func() bool {
		return bytes.Equal(servedCertificate(t, ca, testvalkey.Address(t, node, "6379/tcp"), "localhost"), block.Bytes)
	})
}

func servedCertificate(t *testing.T, ca testCA, address, serverName string) []byte {
	t.Helper()
	roots := x509.NewCertPool()
	roots.AppendCertsFromPEM([]byte(ca.cert))
	dialer := tls.Dialer{Config: &tls.Config{RootCAs: roots, ServerName: serverName, MinVersion: tls.VersionTLS12}}
	conn, err := dialer.DialContext(t.Context(), "tcp", address)
	if err != nil {
		t.Fatalf("TLS handshake with %s: %v", address, err)
	}
	defer func() { _ = conn.Close() }()
	return conn.(*tls.Conn).ConnectionState().PeerCertificates[0].Raw
}

func TestInfisicalsValkeyHoldsOnlyInfisicalsUser(t *testing.T) {
	chart := renderValkey(t, minikubeValues, "ogcloud-infisical-valkey")
	node := startValkeyNode(t, newTestCA(t), chart, "", 0, "/scripts/start-valkey.sh")

	if out := cli(t, node, "infisical", "infisical-password", "SET", "k", "v"); out != "OK" {
		t.Errorf("Infisical's user cannot write: %q", out)
	}
	if out := cli(t, node, "infisical", "infisical-password", "FLUSHALL"); !strings.Contains(out, "NOPERM") {
		t.Errorf("Infisical's user may flush: %q", out)
	}
	for user, password := range map[string]string{"ogcloud-api": "api-password", "default": ""} {
		if out := cli(t, node, user, password, "PING"); out == "PONG" {
			t.Errorf("%s may use Infisical's Valkey", user)
		}
	}
}

func TestValkeySentinelsFailOverAndTheOldPrimaryRejoins(t *testing.T) {
	ca := newTestCA(t)
	chart := renderValkey(t, hetznerValues, "ogcloud-valkey")
	dockerNetwork := testvalkey.Network(t)
	both := "/scripts/start-valkey.sh & exec /scripts/start-sentinel.sh"
	var nodes []testcontainers.Container
	for i := range 3 {
		nodes = append(nodes, startValkeyNode(t, ca, chart, dockerNetwork, i, "sh", "-c", both))
	}
	primary := func(from testcontainers.Container) string {
		out := cli(t, from, "ogcloud-api", "api-password", "-p", "26379", "SENTINEL", "get-master-addr-by-name", "ogcloud")
		return strings.Split(out, "\n")[0]
	}
	if got := primary(nodes[1]); got != nodeName(0) {
		t.Fatalf("the first primary is %q, want pod 0", got)
	}
	// App users only discover the primary; re-pointing Sentinel would hand clients to another server.
	if out := cli(t, nodes[1], "ogcloud-gateway", "gateway-password", "-p", "26379", "SENTINEL", "set", "ogcloud", "quorum", "1"); !strings.Contains(out, "NOPERM") {
		t.Errorf("the gateway's user may reconfigure Sentinel: %q", out)
	}
	eventually(t, 30*time.Second, "both replicas attached", func() bool {
		return strings.Contains(cli(t, nodes[0], "ogcloud-admin", adminPassword, "INFO", "replication"), "connected_slaves:2")
	})
	// A Sentinel fails over only to replicas and with peers it has already found.
	eventually(t, 60*time.Second, "every Sentinel knows both replicas and its two peers", func() bool {
		for _, node := range nodes {
			fields := sentinelFields(t, node)
			if fields["num-slaves"] != "2" || fields["num-other-sentinels"] != "2" {
				return false
			}
		}
		return true
	})
	// The gateway's own client finds the primary through the Sentinels with its read-only user.
	store := gatewayStore(t, ca, nodes)
	if err := store.Ping(context.Background()); err != nil {
		t.Fatalf("the gateway's client cannot reach the primary through Sentinel: %v", err)
	}

	sentinelView := func(from testcontainers.Container) string {
		return cli(t, from, "ogcloud-admin", adminPassword, "-p", "26379", "SENTINEL", "master", "ogcloud") + "\n" +
			cli(t, from, "ogcloud-admin", adminPassword, "-p", "26379", "SENTINEL", "sentinels", "ogcloud") + "\n" +
			runIn(t, from, "sh", "-c", "tail -n 30 /etc/sentinel/sentinel.conf")
	}
	if err := nodes[0].Stop(context.Background(), nil); err != nil {
		t.Fatal(err)
	}
	var promoted string
	eventually(t, 60*time.Second, "a replica promoted", func() bool {
		promoted = primary(nodes[1])
		return promoted == nodeName(1) || promoted == nodeName(2)
	}, func() string { return sentinelView(nodes[1]) })
	eventually(t, 30*time.Second, "the gateway's client follows the new primary", func() bool {
		return store.Ping(context.Background()) == nil
	})
	if err := nodes[0].Start(context.Background()); err != nil {
		t.Fatal(err)
	}
	eventually(t, 60*time.Second, "the old primary rejoined as a replica", func() bool {
		role := cli(t, nodes[0], "ogcloud-admin", adminPassword, "ROLE")
		return strings.HasPrefix(role, "slave") && strings.Contains(role, promoted)
	}, func() string { return sentinelView(nodes[0]) })
}

func nodeName(i int) string { return fmt.Sprintf("ogcloud-valkey-%d.%s", i, headless) }

// sentinelFields is what node's Sentinel reports about the primary, which it prints as alternating names and values.
func sentinelFields(t *testing.T, node testcontainers.Container) map[string]string {
	t.Helper()
	lines := strings.Split(cli(t, node, "ogcloud-admin", adminPassword, "-p", "26379", "SENTINEL", "master", "ogcloud"), "\n")
	fields := map[string]string{}
	for i := 0; i+1 < len(lines); i += 2 {
		fields[lines[i]] = lines[i+1]
	}
	return fields
}

// gatewayStore connects pkg/valkey as the gateway does, reaching the nodes' names through their mapped ports.
func gatewayStore(t *testing.T, ca testCA, nodes []testcontainers.Container) *valkey.Store {
	t.Helper()
	mapped := map[string]string{}
	var sentinels []string
	for i, node := range nodes {
		for _, port := range []string{"6379", "26379"} {
			mapped[net.JoinHostPort(nodeName(i), port)] = testvalkey.Address(t, node, port+"/tcp")
		}
		sentinels = append(sentinels, net.JoinHostPort(nodeName(i), "26379"))
	}
	dial := func(ctx context.Context, address string, dialer *net.Dialer, config *tls.Config) (net.Conn, error) {
		target, ok := mapped[address]
		if !ok {
			return nil, fmt.Errorf("unknown node %s", address)
		}
		conn, err := dialer.DialContext(ctx, "tcp", target)
		if err != nil || config == nil {
			return conn, err
		}
		config = config.Clone()
		config.ServerName, _, _ = net.SplitHostPort(address)
		return tls.Client(conn, config), nil
	}
	roots := x509.NewCertPool()
	roots.AppendCertsFromPEM([]byte(ca.cert))
	store, err := valkey.Connect(valkey.Settings{
		Addresses: sentinels, SentinelMaster: "ogcloud", Username: "ogcloud-gateway", Password: "gateway-password",
		TLS: &tls.Config{RootCAs: roots, MinVersion: tls.VersionTLS12}, Dial: dial,
	})
	if err != nil {
		t.Fatalf("connect as the gateway: %v", err)
	}
	t.Cleanup(store.Close)
	return store
}

// valkeyNode is one StatefulSet pod, with the Secrets it mounts.
type valkeyNode struct {
	testcontainers.Container
	files mounts
}

// startValkeyNode runs the processes of the StatefulSet's pod index.
func startValkeyNode(t *testing.T, ca testCA, chart valkeyChart, dockerNetwork string, index int, command ...string) valkeyNode {
	t.Helper()
	image, err := testimages.Image("valkey")
	if err != nil {
		t.Fatal(err)
	}
	cert, key := ca.issue(t, chart.names...)
	contents := mounted{
		"/tls/ca.crt":  {content: ca.cert},
		"/tls/tls.crt": {content: cert},
		"/tls/tls.key": {content: key},
	}
	for key, password := range chart.root {
		contents["/secrets/root/"+key] = mountedFile{content: password}
	}
	if chart.usersACL != "" {
		contents["/secrets/users/users.acl"] = mountedFile{content: chart.usersACL}
	}
	scripts, err := filepath.Glob(filepath.Join(valkeyScripts, "*.sh"))
	if err != nil || len(scripts) == 0 {
		t.Fatalf("no Valkey scripts: %v", err)
	}
	for _, script := range scripts {
		content, err := os.ReadFile(script) //nolint:gosec // G304: the chart's own scripts.
		if err != nil {
			t.Fatal(err)
		}
		contents["/scripts/"+filepath.Base(script)] = mountedFile{content: string(content), executable: true}
	}
	files := mount(t, contents)
	hostname := fmt.Sprintf("%s-%d", chart.env["STATEFULSET"], index)
	opts := []testcontainers.ContainerCustomizer{
		testcontainers.WithExposedPorts("6379/tcp", "26379/tcp"),
		testcontainers.WithEntrypoint(command...),
		testcontainers.WithEnv(chart.env),
		testcontainers.WithConfigModifier(func(config *container.Config) { config.Hostname = hostname }),
		files.restricted("/data", "/etc/valkey", "/etc/sentinel", "/tmp"),
		testcontainers.WithWaitStrategy(wait.ForLog("Ready to accept connections").WithStartupTimeout(time.Minute)),
	}
	if dockerNetwork != "" {
		// Reached by its pod name and, like a Service, by the StatefulSet's name.
		aliases := []string{fmt.Sprintf("%s.%s", hostname, headless), chart.env["STATEFULSET"] + "." + namespace + ".svc"}
		opts = append(opts, network.WithNetwork(aliases, &testcontainers.DockerNetwork{Name: dockerNetwork}))
	}
	node, err := testcontainers.Run(context.Background(), image, opts...)
	testcontainers.CleanupContainer(t, node)
	if err != nil {
		t.Fatalf("start Valkey node %d: %v", index, err)
	}
	return valkeyNode{Container: node, files: files}
}

// cli runs valkey-cli inside node over TLS as user; args may start with -p to reach the Sentinel.
func cli(t *testing.T, node testcontainers.Container, user, password string, args ...string) string {
	t.Helper()
	command := []string{"valkey-cli", "--tls", "--cacert", "/tls/ca.crt", "-h", "localhost", "--no-auth-warning", "--user", user}
	if password != "" {
		command = append(command, "--pass", password)
	}
	return strings.TrimSpace(runIn(t, node, append(command, args...)...))
}

func runIn(t *testing.T, node testcontainers.Container, command ...string) string {
	t.Helper()
	_, reader, err := node.Exec(context.Background(), command, tcexec.Multiplexed())
	if err != nil {
		t.Fatalf("exec %v: %v", command, err)
	}
	out, err := io.ReadAll(reader)
	if err != nil {
		t.Fatal(err)
	}
	return string(out)
}

// eventually polls condition until it holds; Sentinel's failover takes real seconds. describe, if given, explains
// a timeout.
func eventually(t *testing.T, limit time.Duration, what string, condition func() bool, describe ...func() string) {
	t.Helper()
	deadline := time.Now().Add(limit)
	for !condition() {
		if time.Now().After(deadline) {
			for _, d := range describe {
				t.Log(d())
			}
			t.Fatalf("not within %s: %s", limit, what)
		}
		time.Sleep(time.Second)
	}
}

func hasAll(s string, parts ...string) bool {
	for _, part := range parts {
		if !strings.Contains(s, part) {
			return false
		}
	}
	return true
}

type testCA struct {
	cert string
	key  *ecdsa.PrivateKey
	x509 *x509.Certificate
}

func newTestCA(t *testing.T) testCA {
	t.Helper()
	key, err := ecdsa.GenerateKey(elliptic.P256(), rand.Reader)
	if err != nil {
		t.Fatal(err)
	}
	template := &x509.Certificate{
		SerialNumber: big.NewInt(1), Subject: pkix.Name{CommonName: "test CA"}, IsCA: true, BasicConstraintsValid: true,
		NotBefore: time.Now().Add(-time.Hour), NotAfter: time.Now().Add(time.Hour), KeyUsage: x509.KeyUsageCertSign,
	}
	der, err := x509.CreateCertificate(rand.Reader, template, template, &key.PublicKey, key)
	if err != nil {
		t.Fatal(err)
	}
	parsed, err := x509.ParseCertificate(der)
	if err != nil {
		t.Fatal(err)
	}
	return testCA{cert: pemBlock("CERTIFICATE", der), key: key, x509: parsed}
}

// issue signs a server certificate for names plus 127.0.0.1.
func (ca testCA) issue(t *testing.T, names ...string) (certificate, key string) {
	t.Helper()
	serverKey, err := ecdsa.GenerateKey(elliptic.P256(), rand.Reader)
	if err != nil {
		t.Fatal(err)
	}
	template := &x509.Certificate{
		SerialNumber: serial(t), Subject: pkix.Name{CommonName: names[0]}, DNSNames: names,
		IPAddresses: []net.IP{net.ParseIP("127.0.0.1")}, NotBefore: time.Now().Add(-time.Hour), NotAfter: time.Now().Add(time.Hour),
		KeyUsage: x509.KeyUsageDigitalSignature, ExtKeyUsage: []x509.ExtKeyUsage{x509.ExtKeyUsageServerAuth, x509.ExtKeyUsageClientAuth},
	}
	der, err := x509.CreateCertificate(rand.Reader, template, ca.x509, &serverKey.PublicKey, ca.key)
	if err != nil {
		t.Fatal(err)
	}
	keyDER, err := x509.MarshalPKCS8PrivateKey(serverKey)
	if err != nil {
		t.Fatal(err)
	}
	return pemBlock("CERTIFICATE", der), pemBlock("PRIVATE KEY", keyDER)
}

func serial(t *testing.T) *big.Int {
	t.Helper()
	n, err := rand.Int(rand.Reader, big.NewInt(1<<62))
	if err != nil {
		t.Fatal(err)
	}
	return n.Add(n, big.NewInt(2))
}

func pemBlock(kind string, der []byte) string {
	var b bytes.Buffer
	_ = pem.Encode(&b, &pem.Block{Type: kind, Bytes: der})
	return b.String()
}
