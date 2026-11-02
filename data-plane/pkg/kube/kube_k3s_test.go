package kube

import (
	"context"
	"errors"
	"testing"
	"time"

	corev1 "k8s.io/api/core/v1"
	metav1 "k8s.io/apimachinery/pkg/apis/meta/v1"

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

	identity, err := reviewer.Review(ctx, testk3s.Token(t, client, namespace, "game", "ogcloud-gateway", pod))

	if err != nil {
		t.Fatalf("Review: %v", err)
	}
	if identity.PodName != "lobby-1" || identity.PodUID != string(pod.UID) || identity.ServiceAccount != "game" {
		t.Errorf("identity = %+v", identity)
	}
	if _, err := reviewer.Review(ctx, testk3s.Token(t, client, namespace, "game", "ogcloud-controller", pod)); !errors.Is(err, ErrUnauthenticated) {
		t.Errorf("a token for another audience passed: %v", err)
	}
}
