package service

import (
	"context"
	"errors"
	"net"
	"net/http"
	"strings"
	"testing"

	"github.com/jevzo/ogcloud/data-plane/pkg/telemetry"
)

func TestAdminEndpointsFollowReadinessUntilWorkEnds(t *testing.T) {
	listener := listen(t)
	base := "http://" + listener.Addr().String()
	ctx, cancel := context.WithCancel(context.Background())
	statuses := make(chan []int, 1)

	err := Run(ctx, listener, telemetry.NewRegistry(), func(ctx context.Context, readiness *Readiness) error {
		before := status(ctx, t, base+"/healthz/ready")
		readiness.Set(true)
		statuses <- []int{
			before, status(ctx, t, base+"/healthz/ready"), status(ctx, t, base+"/healthz/live"), status(ctx, t, base+"/metrics"),
		}
		cancel()
		<-ctx.Done()
		return nil
	})

	if err != nil {
		t.Fatalf("Run: %v", err)
	}
	got := <-statuses
	want := []int{http.StatusServiceUnavailable, http.StatusOK, http.StatusOK, http.StatusOK}
	for i := range want {
		if got[i] != want[i] {
			t.Errorf("statuses %v, want %v", got, want)
			break
		}
	}
}

func TestWorkErrorsAreReturned(t *testing.T) {
	listener := listen(t)
	failure := errors.New("kafka unreachable")

	err := Run(context.Background(), listener, telemetry.NewRegistry(), func(context.Context, *Readiness) error {
		return failure
	})

	if !errors.Is(err, failure) {
		t.Errorf("got %v", err)
	}
}

func TestMetricsAreServed(t *testing.T) {
	readiness := &Readiness{}
	server := AdminHandler(telemetry.NewRegistry(), readiness)
	recorder := &recorder{header: http.Header{}}
	request, err := http.NewRequestWithContext(context.Background(), http.MethodGet, "/metrics", nil)
	if err != nil {
		t.Fatalf("request: %v", err)
	}

	server.ServeHTTP(recorder, request)

	if !strings.Contains(recorder.body.String(), "go_goroutines") {
		t.Error("metrics lack go_goroutines")
	}
}

func listen(t *testing.T) net.Listener {
	t.Helper()
	listener, err := (&net.ListenConfig{}).Listen(context.Background(), "tcp", "127.0.0.1:0")
	if err != nil {
		t.Fatalf("listen: %v", err)
	}
	return listener
}

func status(ctx context.Context, t *testing.T, url string) int {
	t.Helper()
	request, err := http.NewRequestWithContext(ctx, http.MethodGet, url, nil)
	if err != nil {
		t.Fatalf("request: %v", err)
	}
	response, err := http.DefaultClient.Do(request)
	if err != nil {
		t.Fatalf("GET %s: %v", url, err)
	}
	defer func() { _ = response.Body.Close() }()
	return response.StatusCode
}

type recorder struct {
	header http.Header
	body   strings.Builder
	code   int
}

func (r *recorder) Header() http.Header         { return r.header }
func (r *recorder) Write(b []byte) (int, error) { return r.body.Write(b) }
func (r *recorder) WriteHeader(code int)        { r.code = code }
