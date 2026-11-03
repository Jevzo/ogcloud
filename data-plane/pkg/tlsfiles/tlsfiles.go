// Package tlsfiles builds TLS configurations from the files cert-manager mounts (D-0041). Server
// certificates are reloaded when cert-manager renews them in place, without a restart.
package tlsfiles

import (
	"crypto/tls"
	"crypto/x509"
	"errors"
	"fmt"
	"log/slog"
	"os"
	"sync"
	"time"
)

// Server returns a configuration that serves the certificate in certFile and keyFile, rereading both when
// either file changes. Failed reloads are logged, while the previous certificate keeps serving.
func Server(certFile, keyFile string, logger *slog.Logger) (*tls.Config, error) {
	reloader := &reloader{certFile: certFile, keyFile: keyFile, logger: logger}
	if _, err := reloader.certificate(); err != nil {
		return nil, err
	}
	return &tls.Config{
		MinVersion: tls.VersionTLS13,
		GetCertificate: func(*tls.ClientHelloInfo) (*tls.Certificate, error) {
			return reloader.certificate()
		},
	}, nil
}

// Client returns a configuration that trusts only the CA in caFile, e.g. the internal CA for Kafka, Valkey
// and the controller.
func Client(caFile, serverName string) (*tls.Config, error) {
	pem, err := os.ReadFile(caFile) //nolint:gosec // G304: the path is the operator's mount, from config.
	if err != nil {
		return nil, fmt.Errorf("read CA %s: %w", caFile, err)
	}
	pool := x509.NewCertPool()
	if !pool.AppendCertsFromPEM(pem) {
		return nil, fmt.Errorf("no certificate in CA file %s", caFile)
	}
	return &tls.Config{MinVersion: tls.VersionTLS13, RootCAs: pool, ServerName: serverName}, nil
}

type reloader struct {
	certFile, keyFile string
	logger            *slog.Logger

	mu       sync.Mutex
	current  *tls.Certificate
	modified [2]time.Time
}

// certificate returns the cached certificate, reloading it first if a file's modification time changed.
func (r *reloader) certificate() (*tls.Certificate, error) {
	modified, err := r.modTimes()
	if err != nil {
		return nil, err
	}
	r.mu.Lock()
	defer r.mu.Unlock()
	if r.current != nil && modified == r.modified {
		return r.current, nil
	}
	pair, err := tls.LoadX509KeyPair(r.certFile, r.keyFile)
	if err != nil {
		// Mid-rotation the files may not match yet; keep serving the previous certificate.
		if r.current != nil {
			r.logger.Warn("certificate reload failed, serving the previous one", "certFile", r.certFile, "error", err)
			return r.current, nil
		}
		return nil, fmt.Errorf("load key pair %s, %s: %w", r.certFile, r.keyFile, err)
	}
	r.current, r.modified = &pair, modified
	return r.current, nil
}

func (r *reloader) modTimes() ([2]time.Time, error) {
	var times [2]time.Time
	for i, path := range []string{r.certFile, r.keyFile} {
		info, err := os.Stat(path)
		if err != nil {
			return times, errors.Join(fmt.Errorf("stat %s", path), err)
		}
		times[i] = info.ModTime()
	}
	return times, nil
}
