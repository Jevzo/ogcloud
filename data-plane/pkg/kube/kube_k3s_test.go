package kube

import (
	"context"
	"errors"
	"testing"
	"time"

	authenticationv1 "k8s.io/api/authentication/v1"
	corev1 "k8s.io/api/core/v1"
	metav1 "k8s.io/apimachinery/pkg/apis/meta/v1"
	"k8s.io/client-go/kubernetes"

	"github.com/jevzo/ogcloud/data-plane/internal/testk3s"
)

const namespace = "default"

func TestTokenReviewNamesThePodAProjectedTokenBelongsTo(t *testing.T) {
	ctx := context.Background()
	client := testk3s.Start(t).Client
	if _, err := client.CoreV1().ServiceAccounts(namespace).Create(ctx,
		&corev1.ServiceAccount{ObjectMeta: metav1.ObjectMeta{Name: "game"}}, metav1.CreateOptions{}); err != nil {
		t.Fatalf("service account: %v", err)
	}
	pod, err := client.CoreV1().Pods(namespace).Create(ctx, &corev1.Pod{
		ObjectMeta: metav1.ObjectMeta{Name: "lobby-1"},
		Spec: corev1.PodSpec{
			ServiceAccountName: "game",
			Containers:         []corev1.Container{{Name: "server", Image: "registry.invalid/never-pulled"}},
		},
	}, metav1.CreateOptions{})
	if err != nil {
		t.Fatalf("pod: %v", err)
	}
	reviewer := NewReviewer(client, "ogcloud-gateway", time.Now)

	identity, err := reviewer.Review(ctx, token(t, client, pod, "ogcloud-gateway"))

	if err != nil {
		t.Fatalf("Review: %v", err)
	}
	if identity.PodName != "lobby-1" || identity.PodUID != string(pod.UID) || identity.ServiceAccount != "game" {
		t.Errorf("identity = %+v", identity)
	}
	if _, err := reviewer.Review(ctx, token(t, client, pod, "ogcloud-controller")); !errors.Is(err, ErrUnauthenticated) {
		t.Errorf("a token for another audience passed: %v", err)
	}
}

// token requests a projected token bound to pod, as the kubelet mounts one (D-0013).
func token(t *testing.T, client kubernetes.Interface, pod *corev1.Pod, audience string) string {
	t.Helper()
	expiry := int64(time.Hour / time.Second)
	request, err := client.CoreV1().ServiceAccounts(namespace).CreateToken(context.Background(), "game", &authenticationv1.TokenRequest{
		Spec: authenticationv1.TokenRequestSpec{
			Audiences:         []string{audience},
			ExpirationSeconds: &expiry,
			BoundObjectRef:    &authenticationv1.BoundObjectReference{Kind: "Pod", APIVersion: "v1", Name: pod.Name, UID: pod.UID},
		},
	}, metav1.CreateOptions{})
	if err != nil {
		t.Fatalf("token request: %v", err)
	}
	return request.Status.Token
}
