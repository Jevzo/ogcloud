package gateway

import (
	"context"
	"testing"
	"time"

	"google.golang.org/grpc/codes"
	"google.golang.org/grpc/metadata"
	"google.golang.org/grpc/status"
	corev1 "k8s.io/api/core/v1"
	metav1 "k8s.io/apimachinery/pkg/apis/meta/v1"
	"k8s.io/apimachinery/pkg/util/wait"
	"k8s.io/client-go/kubernetes"

	"github.com/jevzo/ogcloud/data-plane/internal/testk3s"
	"github.com/jevzo/ogcloud/data-plane/pkg/kube"
)

const audience = "ogcloud-gateway"

func TestPodTokensAreBoundToTheirLiveGamePod(t *testing.T) {
	ctx, cancel := context.WithCancel(context.Background())
	defer cancel()
	client := testk3s.Start(t).Client
	if _, err := client.CoreV1().Namespaces().Create(ctx, &corev1.Namespace{ObjectMeta: metav1.ObjectMeta{Name: namespace}}, metav1.CreateOptions{}); err != nil {
		t.Fatalf("namespace: %v", err)
	}
	lobby := createPod(t, client, gamePod("lobby-1", "s-lobby", "backend", "dynamic"))
	impostor := gamePod("lobby-2", "s-lobby", "backend", "dynamic")
	impostor.Namespace = "default"
	impostor = createPod(t, client, impostor)
	cache, err := StartPodCache(ctx, client, namespace)
	if err != nil {
		t.Fatalf("StartPodCache: %v", err)
	}
	auth := NewAuthenticator(kube.NewReviewer(client, audience, time.Now), cache, namespace)
	token := testk3s.Token(t, client, namespace, "game", audience, lobby)

	peer, err := auth.Authenticate(withBearer(token))

	if err != nil {
		t.Fatalf("Authenticate: %v", err)
	}
	if peer.ServerID != "s-lobby" || peer.GroupID != "lobby" || peer.Role != "backend" || peer.PodName != "lobby-1" {
		t.Errorf("peer = %+v", peer)
	}
	refused := map[string]struct {
		token string
		want  codes.Code
	}{
		"another audience":  {testk3s.Token(t, client, namespace, "game", "ogcloud-controller", lobby), codes.Unauthenticated},
		"another namespace": {testk3s.Token(t, client, "default", "game", audience, impostor), codes.PermissionDenied},
	}
	for name, tt := range refused {
		if _, err := auth.Authenticate(withBearer(tt.token)); status.Code(err) != tt.want {
			t.Errorf("%s: %v", name, err)
		}
	}

	// The review verdict stays cached, so only the pod binding notices the pod is gone.
	if err := client.CoreV1().Pods(namespace).Delete(ctx, "lobby-1", metav1.DeleteOptions{GracePeriodSeconds: new(int64)}); err != nil {
		t.Fatalf("delete: %v", err)
	}
	err = wait.PollUntilContextTimeout(ctx, 50*time.Millisecond, 30*time.Second, true, func(ctx context.Context) (bool, error) {
		_, err := auth.Authenticate(metadata.NewIncomingContext(ctx, metadata.Pairs("authorization", "Bearer "+token)))
		return status.Code(err) == codes.PermissionDenied, nil
	})
	if err != nil {
		t.Fatalf("the deleted pod's token still works: %v", err)
	}
}

func withBearer(token string) context.Context {
	return metadata.NewIncomingContext(context.Background(), metadata.Pairs("authorization", "Bearer "+token))
}

// createPod creates pod with the game service account; it never runs, which TokenRequest does not need.
func createPod(t *testing.T, client kubernetes.Interface, pod *corev1.Pod) *corev1.Pod {
	t.Helper()
	ctx := context.Background()
	account := &corev1.ServiceAccount{ObjectMeta: metav1.ObjectMeta{Name: "game"}}
	if _, err := client.CoreV1().ServiceAccounts(pod.Namespace).Create(ctx, account, metav1.CreateOptions{}); err != nil {
		t.Fatalf("service account: %v", err)
	}
	pod.UID = ""
	pod.Spec = corev1.PodSpec{
		ServiceAccountName: "game",
		Containers:         []corev1.Container{{Name: "server", Image: "registry.invalid/never-pulled"}},
	}
	created, err := client.CoreV1().Pods(pod.Namespace).Create(ctx, pod, metav1.CreateOptions{})
	if err != nil {
		t.Fatalf("pod: %v", err)
	}
	return created
}
