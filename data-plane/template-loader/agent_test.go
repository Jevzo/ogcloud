package templateloader

import (
	"archive/tar"
	"bytes"
	"compress/gzip"
	"context"
	"errors"
	"io"
	"net"
	"net/http"
	"net/http/httptest"
	"os"
	"path/filepath"
	"slices"
	"sync"
	"testing"
	"time"

	"google.golang.org/grpc"
	"google.golang.org/grpc/credentials/insecure"
	"google.golang.org/grpc/test/bufconn"

	nodev1 "github.com/jevzo/ogcloud/data-plane/pkg/gen/proto/ogcloud/node/v1"
)

const patience = 10 * time.Second

// fakeGateway asks the agent for one push per session and records what came back.
type fakeGateway struct {
	nodev1.UnimplementedGatewayServiceServer
	nodev1.UnimplementedTemplateAgentServiceServer
	storage  string
	promoted bool

	mu        sync.Mutex
	hello     *nodev1.Hello
	completed *nodev1.CompleteTemplateUploadRequest
	results   chan *nodev1.DeliveryResult
}

func (g *fakeGateway) Session(stream nodev1.GatewayService_SessionServer) error {
	first, err := stream.Recv()
	if err != nil {
		return err
	}
	g.mu.Lock()
	g.hello = first.GetHello()
	g.mu.Unlock()
	if err := stream.Send(&nodev1.SessionResponse{Message: &nodev1.SessionResponse_Welcome{Welcome: &nodev1.Welcome{}}}); err != nil {
		return err
	}
	if err := stream.Send(&nodev1.SessionResponse{Message: &nodev1.SessionResponse_Delivery{Delivery: &nodev1.Delivery{
		DeliveryId: "d-1", Action: &nodev1.Delivery_PushTemplate{},
	}}}); err != nil {
		return err
	}
	answer, err := stream.Recv()
	if err != nil {
		return err
	}
	g.results <- answer.GetDeliveryResult()
	<-stream.Context().Done()
	return nil
}

func (g *fakeGateway) CreateTemplateUpload(context.Context, *nodev1.CreateTemplateUploadRequest) (*nodev1.CreateTemplateUploadResponse, error) {
	return &nodev1.CreateTemplateUploadResponse{UploadId: "u-1", Url: g.storage + "/upload"}, nil
}

func (g *fakeGateway) CompleteTemplateUpload(_ context.Context, in *nodev1.CompleteTemplateUploadRequest) (*nodev1.CompleteTemplateUploadResponse, error) {
	g.mu.Lock()
	defer g.mu.Unlock()
	g.completed = in
	return &nodev1.CompleteTemplateUploadResponse{TemplateVersionId: "v-2", Promoted: g.promoted}, nil
}

type uploads struct {
	mu    sync.Mutex
	body  []byte
	count int
}

func (u *uploads) last() []byte {
	u.mu.Lock()
	defer u.mu.Unlock()
	return u.body
}

func (u *uploads) total() int {
	u.mu.Lock()
	defer u.mu.Unlock()
	return u.count
}

func startAgent(t *testing.T, promoted, pushOnShutdown bool) (*Agent, *fakeGateway, *uploads, string) {
	t.Helper()
	received := &uploads{}
	storage := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		body, _ := io.ReadAll(r.Body)
		if int64(len(body)) != r.ContentLength {
			http.Error(w, "length mismatch", http.StatusBadRequest)
			return
		}
		received.mu.Lock()
		received.body = body
		received.count++
		received.mu.Unlock()
	}))
	t.Cleanup(storage.Close)
	gateway := &fakeGateway{storage: storage.URL, promoted: promoted, results: make(chan *nodev1.DeliveryResult, 1)}
	server := grpc.NewServer()
	nodev1.RegisterGatewayServiceServer(server, gateway)
	nodev1.RegisterTemplateAgentServiceServer(server, gateway)
	listener := bufconn.Listen(1 << 20)
	go func() { _ = server.Serve(listener) }()
	t.Cleanup(server.Stop)
	conn, err := grpc.NewClient("passthrough:///bufnet",
		grpc.WithContextDialer(func(ctx context.Context, _ string) (net.Conn, error) { return listener.DialContext(ctx) }),
		grpc.WithTransportCredentials(insecure.NewCredentials()))
	if err != nil {
		t.Fatal(err)
	}
	t.Cleanup(func() { _ = conn.Close() })
	root, dir := dataRoot(t)
	files := map[string]string{
		"server.properties": "motd=hi", "world/level.dat": "level", "world/session.lock": "lock",
		"plugins/Essentials.jar": "group", "plugins/ogcloud.jar": "bundle", "logs/latest.log": "log",
		"cache/mojang.jar": "cache", ".ogcloud/bundle.txt": "plugins/ogcloud.jar\n",
	}
	for name, content := range files {
		if err := os.MkdirAll(filepath.Dir(filepath.Join(dir, name)), 0o755); err != nil {
			t.Fatal(err)
		}
		if err := os.WriteFile(filepath.Join(dir, name), []byte(content), 0o600); err != nil {
			t.Fatal(err)
		}
	}
	agent := &Agent{
		Gateway: nodev1.NewGatewayServiceClient(conn), Uploads: nodev1.NewTemplateAgentServiceClient(conn), HTTP: storage.Client(),
		Data: root, TmpDir: t.TempDir(), PushOnShutdown: pushOnShutdown, ShutdownTimeout: patience, Version: "2.0.0", Logger: quiet(),
	}
	return agent, gateway, received, dir
}

// names lists the files in a pushed archive.
func names(t *testing.T, archive []byte) []string {
	t.Helper()
	unzipped, err := gzip.NewReader(bytes.NewReader(archive))
	if err != nil {
		t.Fatal(err)
	}
	entries := tar.NewReader(unzipped)
	var files []string
	for {
		header, err := entries.Next()
		if errors.Is(err, io.EOF) {
			slices.Sort(files)
			return files
		}
		if err != nil {
			t.Fatal(err)
		}
		if header.Typeflag == tar.TypeReg {
			files = append(files, header.Name)
		}
	}
}

func TestAPushDeliveryUploadsTheServerWithoutBundleOrChurn(t *testing.T) {
	agent, gateway, received, dir := startAgent(t, true, false)
	ctx, cancel := context.WithCancel(context.Background())
	defer cancel()
	go func() { _ = agent.Run(ctx) }()

	var result *nodev1.DeliveryResult
	select {
	case result = <-gateway.results:
	case <-time.After(patience):
		t.Fatal("no delivery result")
	}

	if !result.GetSuccess() || result.GetDeliveryId() != "d-1" || result.GetMessage() != "v-2" {
		t.Errorf("result = %v", result)
	}
	want := []string{"plugins/Essentials.jar", "server.properties", "world/level.dat"}
	if got := names(t, received.last()); !slices.Equal(got, want) {
		t.Errorf("pushed %v, want %v", got, want)
	}
	gateway.mu.Lock()
	defer gateway.mu.Unlock()
	if gateway.hello.GetNodeType().String() != "NODE_TYPE_TEMPLATE_AGENT" || gateway.completed.GetSha256() != sum(received.last()) ||
		gateway.completed.GetSizeBytes() != uint64(len(received.last())) {
		t.Errorf("hello %v, completed %v", gateway.hello, gateway.completed)
	}
	if marker, _ := os.ReadFile(filepath.Join(dir, ".ogcloud", "template.sha256")); string(marker) != sum(received.last())+"\n" {
		t.Errorf("marker = %q; a promoted push must mark the volume", marker)
	}
}

func TestStaticServersPushOnShutdownAndUnpromotedPushesLeaveTheMarker(t *testing.T) {
	agent, gateway, received, dir := startAgent(t, false, true)
	ctx, cancel := context.WithCancel(context.Background())
	done := make(chan error, 1)
	go func() { done <- agent.Run(ctx) }()
	<-gateway.results

	cancel()

	if err := <-done; err != nil {
		t.Fatalf("Run: %v", err)
	}
	if received.total() != 2 {
		t.Errorf("%d uploads; the delivery and the shutdown should each push", received.total())
	}
	if _, err := os.Stat(filepath.Join(dir, ".ogcloud", "template.sha256")); !errors.Is(err, os.ErrNotExist) {
		t.Error("an unpromoted push marked the volume")
	}
}
