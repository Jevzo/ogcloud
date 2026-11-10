package infisical

import (
	"context"
	"net/http"
	"net/http/httptest"
	"testing"
	"time"
)

// Infisical answering 200 without what was asked must not pass as an empty credential (D-0173).
func TestEmptyUniversalAuthAnswersAreErrors(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, _ *http.Request) {
		w.Header().Set("Content-Type", "application/json")
		_, _ = w.Write([]byte(`{}`))
	}))
	t.Cleanup(server.Close)
	client := New(server.URL, nil).WithToken("token")
	ctx := context.Background()
	calls := map[string]func() error{
		"UniversalAuthClientID": func() error { _, err := client.UniversalAuthClientID(ctx, "identity"); return err },
		"AttachUniversalAuth":   func() error { _, err := client.AttachUniversalAuth(ctx, "identity", time.Minute); return err },
		"CreateClientSecret":    func() error { _, err := client.CreateClientSecret(ctx, "identity", "test"); return err },
		"UniversalAuthLogin":    func() error { _, err := client.UniversalAuthLogin(ctx, "id", "secret"); return err },
	}
	for name, call := range calls {
		if err := call(); err == nil {
			t.Errorf("%s accepted an empty answer", name)
		}
	}
}

func TestOnlyAnswersInfisicalGaveItselfAreRefusals(t *testing.T) {
	for status, refused := range map[int]bool{
		http.StatusUnauthorized: true, http.StatusForbidden: true, http.StatusInternalServerError: true,
		http.StatusTooManyRequests: false, http.StatusBadGateway: false, http.StatusServiceUnavailable: false,
		http.StatusGatewayTimeout: false,
	} {
		if got := IsRefused(&APIError{Status: status}); got != refused {
			t.Errorf("IsRefused(%d) = %v, want %v", status, got, refused)
		}
	}
	if IsRefused(context.DeadlineExceeded) {
		t.Error("an unreachable Infisical counts as a refusal")
	}
}
