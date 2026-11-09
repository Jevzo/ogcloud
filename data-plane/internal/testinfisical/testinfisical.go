// Package testinfisical runs Infisical on PostgreSQL and Valkey (D-0044), next to a k3s it can reach for
// Kubernetes auth, all on one Docker network.
package testinfisical

import (
	"context"
	"crypto/rand"
	"encoding/base64"
	"encoding/hex"
	"fmt"
	"testing"

	"github.com/testcontainers/testcontainers-go"
	"github.com/testcontainers/testcontainers-go/network"
	"github.com/testcontainers/testcontainers-go/wait"

	"github.com/jevzo/ogcloud/data-plane/internal/testimages"
	"github.com/jevzo/ogcloud/data-plane/internal/testk3s"
	"github.com/jevzo/ogcloud/data-plane/internal/testvalkey"
)

const (
	apiPort  = "8080/tcp"
	database = "infisical"
	// KubernetesHost is where Infisical reaches the k3s API inside the network.
	KubernetesHost = "https://k3s:6443"
)

// Environment is Infisical's URL on the host plus the cluster it reviews tokens with.
type Environment struct {
	URL     string
	Cluster *testk3s.Cluster
}

// Start runs PostgreSQL, Valkey, k3s and Infisical.
func Start(t *testing.T) *Environment {
	t.Helper()
	ctx := context.Background()
	shared := testvalkey.Network(t)
	on := func(alias string) testcontainers.CustomizeRequestOption {
		return network.WithNetwork([]string{alias}, &testcontainers.DockerNetwork{Name: shared})
	}
	run(t, "postgres", on("postgres"),
		// A throwaway database: user, password and name are all the same.
		testcontainers.WithEnv(map[string]string{"POSTGRES_USER": database, "POSTGRES_PASSWORD": database, "POSTGRES_DB": database}),
		testcontainers.WithWaitStrategy(wait.ForLog("database system is ready to accept connections").WithOccurrence(2)),
	)
	testvalkey.Run(t, on("valkey"))
	cluster := testk3s.Start(t, on("k3s"), testcontainers.WithCmdArgs("--tls-san=k3s"))
	infisical := run(t, "infisical", on("infisical"),
		testcontainers.WithEnv(map[string]string{
			"ENCRYPTION_KEY":    randomHex(t),
			"AUTH_SECRET":       randomBase64(t),
			"DB_CONNECTION_URI": fmt.Sprintf("postgres://%s:%s@postgres:5432/%s", database, database, database),
			"REDIS_URL":         "redis://valkey:6379",
			"SITE_URL":          "http://localhost:8080",
			"TELEMETRY_ENABLED": "false",
			// The Kubernetes API sits on a private address, in the cluster as here.
			"ALLOW_INTERNAL_IP_CONNECTIONS": "true",
		}),
		testcontainers.WithExposedPorts(apiPort),
		testcontainers.WithWaitStrategy(wait.ForHTTP("/api/status").WithPort(apiPort)),
	)
	url, err := infisical.PortEndpoint(ctx, apiPort, "http")
	if err != nil {
		t.Fatalf("infisical endpoint: %v", err)
	}
	return &Environment{URL: url, Cluster: cluster}
}

func run(t *testing.T, name string, opts ...testcontainers.ContainerCustomizer) testcontainers.Container {
	t.Helper()
	image, err := testimages.Image(name)
	if err != nil {
		t.Fatalf("image: %v", err)
	}
	container, err := testcontainers.Run(context.Background(), image, opts...)
	testcontainers.CleanupContainer(t, container)
	if err != nil {
		t.Fatalf("start %s: %v", name, err)
	}
	return container
}

func randomHex(t *testing.T) string {
	t.Helper()
	return hex.EncodeToString(randomBytes(t, 16))
}

func randomBase64(t *testing.T) string {
	t.Helper()
	return base64.StdEncoding.EncodeToString(randomBytes(t, 32))
}

func randomBytes(t *testing.T, n int) []byte {
	t.Helper()
	raw := make([]byte, n)
	if _, err := rand.Read(raw); err != nil {
		t.Fatalf("random: %v", err)
	}
	return raw
}
