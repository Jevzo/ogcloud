package telemetry

import (
	"context"
	"testing"
)

func TestTheRegistryCarriesRuntimeMetrics(t *testing.T) {
	families, err := NewRegistry().Gather()
	if err != nil {
		t.Fatalf("Gather: %v", err)
	}
	found := false
	for _, family := range families {
		if family.GetName() == "go_goroutines" {
			found = true
		}
	}
	if !found {
		t.Error("go_goroutines is missing")
	}
}

func TestTracingStaysOffWithoutAnEndpoint(t *testing.T) {
	shutdown, err := StartTracing(context.Background(), "gateway", "")
	if err != nil {
		t.Fatalf("StartTracing: %v", err)
	}
	if err := shutdown(context.Background()); err != nil {
		t.Errorf("shutdown: %v", err)
	}
}

func TestTracingStartsWithAnEndpoint(t *testing.T) {
	shutdown, err := StartTracing(context.Background(), "gateway", "http://127.0.0.1:4318")
	if err != nil {
		t.Fatalf("StartTracing: %v", err)
	}
	// Nothing was traced, so stopping sends nothing.
	if err := shutdown(context.Background()); err != nil {
		t.Errorf("shutdown: %v", err)
	}
}
