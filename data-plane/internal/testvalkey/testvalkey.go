// Package testvalkey runs Valkey from the pinned image (D-0113) for tests.
package testvalkey

import (
	"context"
	"fmt"
	"testing"

	"github.com/testcontainers/testcontainers-go"
	"github.com/testcontainers/testcontainers-go/network"
	"github.com/testcontainers/testcontainers-go/wait"

	"github.com/jevzo/ogcloud/data-plane/internal/testimages"
)

// Port is Valkey's port inside the container.
const Port = "6379/tcp"

// Start runs valkey-server with args, e.g. ACL users, and returns its address on the host.
func Start(t *testing.T, args ...string) string {
	t.Helper()
	return Address(t, Run(t, testcontainers.WithCmd(append([]string{"valkey-server"}, args...)...)))
}

// Run starts a Valkey container customized by opts and exposing Port.
func Run(t *testing.T, opts ...testcontainers.ContainerCustomizer) testcontainers.Container {
	t.Helper()
	image, err := testimages.Image("valkey")
	if err != nil {
		t.Fatalf("image: %v", err)
	}
	opts = append([]testcontainers.ContainerCustomizer{
		testcontainers.WithExposedPorts(Port),
		testcontainers.WithWaitStrategy(wait.ForListeningPort(Port)),
	}, opts...)
	container, err := testcontainers.Run(context.Background(), image, opts...)
	testcontainers.CleanupContainer(t, container)
	if err != nil {
		t.Fatalf("start Valkey: %v", err)
	}
	return container
}

// Address is where the host reaches port of container.
func Address(t *testing.T, container testcontainers.Container, port ...string) string {
	t.Helper()
	target := Port
	if len(port) > 0 {
		target = port[0]
	}
	host, err := container.Host(context.Background())
	if err != nil {
		t.Fatalf("host: %v", err)
	}
	mapped, err := container.MappedPort(context.Background(), target)
	if err != nil {
		t.Fatalf("mapped port: %v", err)
	}
	return fmt.Sprintf("%s:%s", host, mapped.Port())
}

// Network creates a Docker network removed with the test.
func Network(t *testing.T) string {
	t.Helper()
	created, err := network.New(context.Background())
	if err != nil {
		t.Fatalf("network: %v", err)
	}
	t.Cleanup(func() { _ = created.Remove(context.Background()) })
	return created.Name
}
