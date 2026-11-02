package kube

import (
	"context"
	"fmt"
	"strings"
	"sync"
	"time"
)

// tokenReread is how long a read token is reused; the kubelet rotates the file well before expiry.
const tokenReread = time.Minute

// TokenFile is a gRPC credential that sends a projected service-account token from its file as a
// bearer token (D-0013), for calls to the controller.
type TokenFile struct {
	path string
	read func(string) ([]byte, error)
	now  func() time.Time

	mu     sync.Mutex
	token  string
	readAt time.Time
}

// NewTokenFile reads path with read, e.g. os.ReadFile.
func NewTokenFile(path string, read func(string) ([]byte, error), now func() time.Time) *TokenFile {
	return &TokenFile{path: path, read: read, now: now}
}

// GetRequestMetadata implements credentials.PerRPCCredentials.
func (f *TokenFile) GetRequestMetadata(context.Context, ...string) (map[string]string, error) {
	token, err := f.current()
	if err != nil {
		return nil, err
	}
	return map[string]string{"authorization": "Bearer " + token}, nil
}

// RequireTransportSecurity keeps the token off plaintext connections.
func (f *TokenFile) RequireTransportSecurity() bool { return true }

func (f *TokenFile) current() (string, error) {
	f.mu.Lock()
	defer f.mu.Unlock()
	now := f.now()
	if f.token != "" && now.Sub(f.readAt) < tokenReread {
		return f.token, nil
	}
	content, err := f.read(f.path)
	if err != nil {
		return "", fmt.Errorf("read token %s: %w", f.path, err)
	}
	token := strings.TrimSpace(string(content))
	if token == "" {
		return "", fmt.Errorf("token file %s is empty", f.path)
	}
	f.token, f.readAt = token, now
	return token, nil
}
