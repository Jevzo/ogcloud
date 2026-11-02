package gateway

import (
	"context"
	"testing"

	"k8s.io/client-go/kubernetes/fake"

	"github.com/jevzo/ogcloud/data-plane/pkg/podlabels"
)

func TestThePodCacheHoldsOnlyTheControllersPods(t *testing.T) {
	ctx, cancel := context.WithCancel(context.Background())
	defer cancel()
	foreign := gamePod("rogue", "s-x", "backend", "dynamic")
	foreign.Labels[podlabels.ManagedBy] = "someone"
	client := fake.NewClientset(gamePod("lobby-1", "s-lobby", "backend", "dynamic"), foreign)

	cache, err := StartPodCache(ctx, client, namespace)
	if err != nil {
		t.Fatalf("StartPodCache: %v", err)
	}

	if pod, ok := cache.Pod("lobby-1"); !ok || pod.Labels[podlabels.ServerId] != "s-lobby" {
		t.Errorf("lobby-1 = %v, %v", pod, ok)
	}
	if _, ok := cache.Pod("rogue"); ok {
		t.Error("a pod the controller does not manage was cached")
	}
}
