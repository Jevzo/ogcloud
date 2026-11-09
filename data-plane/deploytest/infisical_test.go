package deploytest

import (
	"bytes"
	"context"
	"crypto/rand"
	"crypto/tls"
	"crypto/x509"
	"encoding/base64"
	"encoding/hex"
	"encoding/pem"
	"io"
	"net"
	"net/http"
	"strings"
	"testing"
	"time"

	"github.com/moby/moby/api/types/container"
	"github.com/testcontainers/testcontainers-go"
	"github.com/testcontainers/testcontainers-go/network"
	"github.com/testcontainers/testcontainers-go/wait"

	"github.com/jevzo/ogcloud/data-plane/internal/testimages"
	"github.com/jevzo/ogcloud/data-plane/internal/testvalkey"
)

// Infisical runs here as the chart's Deployment runs it (D-0150): its container and the ghostunnel sidecar in one
// network namespace, read-only as uid 1000, against PostgreSQL over TLS and its own Valkey (D-0151).

const postgresPassword = "postgres-password"

// secretVolumePublish lays out /tls as kubelet updates a Secret volume: the files of each version in their own
// directory, ..data pointing at the current one, and the keys as links through ..data.
const secretVolumePublish = `#!/bin/sh
set -eu
mkdir /tls/..$1
cp "/secret/$2" /tls/..$1/tls.crt
cp "/secret/$3" /tls/..$1/tls.key
ln -sfn ..$1 /tls/..data
ln -sfn ..data/tls.crt /tls/tls.crt
ln -sfn ..data/tls.key /tls/tls.key
`

func TestInfisicalServesOnlyThroughItsTLSSidecar(t *testing.T) {
	ca := newTestCA(t)
	objects := render(t, minikubeValues)
	dockerNetwork := testvalkey.Network(t)
	pod := objects.find(t, "Deployment", "ogcloud-infisical")
	containers, _ := get(pod.podSpec(), "containers").([]any)
	if len(containers) != 2 {
		t.Fatalf("Infisical's pod has %d containers, want Infisical and its TLS sidecar", len(containers))
	}
	app, sidecar := containers[0], containers[1]
	valkeyChart := renderValkey(t, minikubeValues, "ogcloud-infisical-valkey")
	env := podEnv(t, app, map[string]map[string]string{
		"ogcloud-infisical-root": {
			"ENCRYPTION_KEY": hex.EncodeToString(randomBytes(t, 16)),
			"AUTH_SECRET":    base64.StdEncoding.EncodeToString(randomBytes(t, 32)),
		},
		"ogcloud-postgres-infisical":    {"username": "infisical", "password": postgresPassword},
		"ogcloud-infisical-valkey-root": {"INFISICAL_PASSWORD": valkeyChart.root["INFISICAL_PASSWORD"]},
	})
	startPostgres(t, ca, objects, dockerNetwork, env["DB_HOST"])
	startValkeyNode(t, ca, valkeyChart, dockerNetwork, 0, "/scripts/start-valkey.sh")

	ctx := context.Background()
	infisical, err := testcontainers.Run(ctx, str(app, "image"),
		testcontainers.WithEntrypoint(append(stringList(get(app, "command")), stringList(get(app, "args"))...)...),
		testcontainers.WithEnv(env),
		// kube-root-ca.crt stands in for the cluster's API CA, which Infisical trusts next to the internal one (D-0167).
		mount(t, mounted{
			"/etc/ogcloud/ca/ca.crt": {content: ca.cert}, "/etc/kubernetes-ca/ca.crt": {content: newTestCA(t).cert},
		}).restricted("/tmp"),
		network.WithNetwork(nil, &testcontainers.DockerNetwork{Name: dockerNetwork}),
		// The sidecar shares this container's network, so its port is published here, next to the one that must
		// stay closed.
		testcontainers.WithExposedPorts("8443/tcp", "8080/tcp"),
	)
	testcontainers.CleanupContainer(t, infisical)
	if err != nil {
		t.Fatalf("start Infisical: %v", err)
	}
	names := certificateNames(t, objects, "ogcloud-infisical-tls")
	cert, key := ca.issue(t, names...)
	renewed, renewedKey := ca.issue(t, names...)
	// The chart reloads hourly; the test needs to see a reload.
	args := stringList(get(sidecar, "args"))
	for i, arg := range args {
		if strings.HasPrefix(arg, "--timed-reload=") {
			args[i] = "--timed-reload=1s"
		}
	}
	tunnel, err := testcontainers.Run(ctx, str(sidecar, "image"),
		// /tls is laid out as kubelet lays out a Secret volume, on tmpfs too: ghostunnel's Landlock sandbox does
		// not match files on Docker Desktop's shared folders.
		testcontainers.WithEntrypoint(append([]string{"/bin/sh", "-c",
			`/secret/publish.sh 1 tls.crt tls.key && exec /usr/bin/ghostunnel "$@"`, "sh"}, args...)...),
		mount(t, mounted{
			"/secret/publish.sh": {content: secretVolumePublish, executable: true},
			"/secret/tls.crt":    {content: cert}, "/secret/tls.key": {content: key},
			"/secret/next.crt": {content: renewed}, "/secret/next.key": {content: renewedKey},
		}).restricted("/tls"),
		testcontainers.WithHostConfigModifier(func(host *container.HostConfig) {
			host.NetworkMode = container.NetworkMode("container:" + infisical.GetContainerID())
		}),
	)
	testcontainers.CleanupContainer(t, tunnel)
	if err != nil {
		t.Fatalf("start the TLS sidecar: %v", err)
	}

	roots := x509.NewCertPool()
	roots.AppendCertsFromPEM([]byte(ca.cert))
	client := http.Client{Timeout: 5 * time.Second, Transport: &http.Transport{
		TLSClientConfig: &tls.Config{RootCAs: roots, ServerName: "ogcloud-infisical.ogcloud.svc", MinVersion: tls.VersionTLS12},
	}}
	status := "https://" + testvalkey.Address(t, infisical, "8443/tcp") + "/api/status"
	eventually(t, 3*time.Minute, "Infisical answers through its sidecar", func() bool {
		response, err := client.Get(status) //nolint:noctx // A poll with the client's timeout.
		if err != nil {
			return false
		}
		_ = response.Body.Close()
		return response.StatusCode == http.StatusOK
	}, func() string {
		return tail(containerLogs(t, infisical), 15) + "\n--- sidecar:\n" + tail(containerLogs(t, tunnel), 15)
	})

	// cert-manager renews the certificate; kubelet swaps the Secret's files, and the sidecar serves the new one.
	runIn(t, tunnel, "/secret/publish.sh", "2", "next.crt", "next.key")
	block, _ := pem.Decode([]byte(renewed))
	eventually(t, 30*time.Second, "the sidecar serves the renewed certificate", func() bool {
		served := servedCertificate(t, ca, testvalkey.Address(t, infisical, "8443/tcp"), "ogcloud-infisical.ogcloud.svc")
		return bytes.Equal(served, block.Bytes)
	}, func() string { return tail(containerLogs(t, tunnel), 10) })

	// Infisical listens on 127.0.0.1 only, so its plain port is closed to everything outside the pod (D-0149).
	dialer := net.Dialer{Timeout: 2 * time.Second}
	conn, err := dialer.DialContext(ctx, "tcp", testvalkey.Address(t, infisical, "8080/tcp"))
	if err == nil {
		_ = conn.SetReadDeadline(time.Now().Add(2 * time.Second))
		_, err = conn.Read(make([]byte, 1))
		_ = conn.Close()
	}
	if err == nil {
		t.Error("Infisical's plain port answers outside its pod")
	}
}

// startPostgres runs PostgreSQL with TLS from ca under the names of the chart's database certificate, reached as
// host, with Infisical's database and user as CloudNativePG's initdb creates them.
func startPostgres(t *testing.T, ca testCA, objects rendered, dockerNetwork, host string) {
	t.Helper()
	image, err := testimages.Image("postgres")
	if err != nil {
		t.Fatal(err)
	}
	cert, key := ca.issue(t, certificateNames(t, objects, "ogcloud-postgres-tls")...)
	postgres, err := testcontainers.Run(context.Background(), image,
		testcontainers.WithFiles(
			testcontainers.ContainerFile{Reader: strings.NewReader(cert), ContainerFilePath: "/tls/tls.crt", FileMode: 0o644},
			testcontainers.ContainerFile{Reader: strings.NewReader(key), ContainerFilePath: "/tls/tls.key", FileMode: 0o600},
		),
		// PostgreSQL reads only a key its own user owns.
		testcontainers.WithEntrypoint("sh", "-c", "install -o postgres -m 600 /tls/tls.key /tmp/tls.key && "+
			"exec docker-entrypoint.sh postgres -c ssl=on -c ssl_cert_file=/tls/tls.crt -c ssl_key_file=/tmp/tls.key"),
		testcontainers.WithEnv(map[string]string{"POSTGRES_USER": "infisical", "POSTGRES_PASSWORD": postgresPassword, "POSTGRES_DB": "infisical"}),
		network.WithNetwork([]string{host}, &testcontainers.DockerNetwork{Name: dockerNetwork}),
		testcontainers.WithWaitStrategy(wait.ForLog("database system is ready to accept connections").WithOccurrence(2)),
	)
	testcontainers.CleanupContainer(t, postgres)
	if err != nil {
		t.Fatalf("start PostgreSQL: %v", err)
	}
}

func randomBytes(t *testing.T, n int) []byte {
	t.Helper()
	raw := make([]byte, n)
	if _, err := rand.Read(raw); err != nil {
		t.Fatal(err)
	}
	return raw
}

func containerLogs(t *testing.T, c testcontainers.Container) string {
	t.Helper()
	logs, err := c.Logs(context.Background())
	if err != nil {
		return err.Error()
	}
	defer func() { _ = logs.Close() }()
	out, _ := io.ReadAll(logs)
	return string(out)
}

func tail(text string, lines int) string {
	all := strings.Split(strings.TrimRight(text, "\n"), "\n")
	return strings.Join(all[max(0, len(all)-lines):], "\n")
}
