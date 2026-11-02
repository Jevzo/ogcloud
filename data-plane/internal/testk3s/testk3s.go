// Package testk3s runs k3s from the pinned image (D-0070, D-0113) for tests that need a real API server.
package testk3s

import (
	"context"
	"testing"
	"time"

	"github.com/testcontainers/testcontainers-go"
	"github.com/testcontainers/testcontainers-go/modules/k3s"
	authenticationv1 "k8s.io/api/authentication/v1"
	corev1 "k8s.io/api/core/v1"
	metav1 "k8s.io/apimachinery/pkg/apis/meta/v1"
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

// Token requests a service-account token as the kubelet projects one (D-0013): for audience (the API
// server's own when empty) and bound to pod when it is not nil.
func Token(t *testing.T, client kubernetes.Interface, namespace, account, audience string, pod *corev1.Pod) string {
	t.Helper()
	expiry := int64(time.Hour / time.Second)
	spec := authenticationv1.TokenRequestSpec{ExpirationSeconds: &expiry}
	if audience != "" {
		spec.Audiences = []string{audience}
	}
	if pod != nil {
		spec.BoundObjectRef = &authenticationv1.BoundObjectReference{Kind: "Pod", APIVersion: "v1", Name: pod.Name, UID: pod.UID}
	}
	request, err := client.CoreV1().ServiceAccounts(namespace).CreateToken(context.Background(), account,
		&authenticationv1.TokenRequest{Spec: spec}, metav1.CreateOptions{})
	if err != nil {
		t.Fatalf("token for %s: %v", account, err)
	}
	return request.Status.Token
}
