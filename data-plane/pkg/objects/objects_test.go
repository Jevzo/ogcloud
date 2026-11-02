package objects

import (
	"bytes"
	"context"
	"io"
	"net/http"
	"testing"
	"time"

	"github.com/jevzo/ogcloud/data-plane/internal/tests3"
)

func TestPresignedURLsUploadAndDownloadWithoutCredentials(t *testing.T) {
	ctx := context.Background()
	endpoint := tests3.Start(t)
	presigner := NewPresigner(Settings{
		Endpoint: endpoint, Region: tests3.Region, Bucket: tests3.Bucket,
		AccessKey: tests3.AccessKey, SecretKey: tests3.SecretKey, PathStyle: true,
	}, time.Minute)
	key := "templates/lobby/0192d2c1-0000-7000-8000-000000000001.tar.gz"

	upload, err := presigner.Put(ctx, key)
	if err != nil {
		t.Fatalf("Put: %v", err)
	}
	if status := send(t, http.MethodPut, upload, []byte("archive")); status != http.StatusOK {
		t.Fatalf("upload status %d", status)
	}
	download, err := presigner.Get(ctx, key)
	if err != nil {
		t.Fatalf("Get: %v", err)
	}
	request, err := http.NewRequestWithContext(ctx, http.MethodGet, download, nil)
	if err != nil {
		t.Fatalf("request: %v", err)
	}
	response, err := http.DefaultClient.Do(request)
	if err != nil {
		t.Fatalf("download: %v", err)
	}
	defer func() { _ = response.Body.Close() }()
	body, err := io.ReadAll(response.Body)
	if err != nil {
		t.Fatalf("read: %v", err)
	}
	if string(body) != "archive" {
		t.Errorf("downloaded %q", body)
	}
	if status := send(t, http.MethodPut, download, []byte("tampered")); status == http.StatusOK {
		t.Error("a download URL accepted an upload")
	}
}

func send(t *testing.T, method, url string, body []byte) int {
	t.Helper()
	request, err := http.NewRequestWithContext(context.Background(), method, url, bytes.NewReader(body))
	if err != nil {
		t.Fatalf("request: %v", err)
	}
	response, err := http.DefaultClient.Do(request)
	if err != nil {
		t.Fatalf("%s: %v", method, err)
	}
	_ = response.Body.Close()
	return response.StatusCode
}
