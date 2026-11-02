// Package testk3s runs k3s from the pinned image (D-0070, D-0113) for tests that need a real API server.
package testk3s

import (
	"context"
	"testing"

	"github.com/testcontainers/testcontainers-go"
	"github.com/testcontainers/testcontainers-go/modules/k3s"
	"k8s.io/client-go/kubernetes"
	"k8s.io/client-go/rest"
	"k8s.io/client-go/tools/clientcmd"

	"github.com/jevzo/ogcloud/data-plane/internal/testimages"
)

// Cluster is a running k3s with an admin client.
type Cluster struct {
	Client kubernetes.Interface
	// The admin REST config; its CAData is the API server's CA.
	Config *rest.Config
}

// Start runs k3s customized by opts, e.g. a network alias that other containers reach it by.
func Start(t *testing.T, opts ...testcontainers.ContainerCustomizer) *Cluster {
	t.Helper()
	ctx := context.Background()
	image, err := testimages.Image("k3s")
	if err != nil {
		t.Fatalf("image: %v", err)
	}
	container, err := k3s.Run(ctx, image, opts...)
	testcontainers.CleanupContainer(t, container)
	if err != nil {
		t.Fatalf("start k3s: %v", err)
	}
	kubeconfig, err := container.GetKubeConfig(ctx)
	if err != nil {
		t.Fatalf("kubeconfig: %v", err)
	}
	config, err := clientcmd.RESTConfigFromKubeConfig(kubeconfig)
	if err != nil {
		t.Fatalf("rest config: %v", err)
	}
	client, err := kubernetes.NewForConfig(config)
	if err != nil {
		t.Fatalf("client: %v", err)
	}
	return &Cluster{Client: client, Config: config}
}
