package tlsfiles

import (
	"context"
	"crypto/ecdsa"
	"crypto/elliptic"
	"crypto/rand"
	"crypto/tls"
	"crypto/x509"
	"crypto/x509/pkix"
	"encoding/pem"
	"io"
	"log/slog"
	"math/big"
	"net"
	"os"
	"path/filepath"
	"testing"
	"time"
)

func TestServersPickUpRenewedCertificates(t *testing.T) {
	dir := t.TempDir()
	ca := newAuthority(t)
	writeFile(t, filepath.Join(dir, "ca.crt"), ca.certPEM)
	ca.issue(t, dir, "first")

	server, err := Server(filepath.Join(dir, "tls.crt"), filepath.Join(dir, "tls.key"), slog.New(slog.NewTextHandler(io.Discard, nil)))
	if err != nil {
		t.Fatalf("Server: %v", err)
	}
	client, err := Client(filepath.Join(dir, "ca.crt"), "gateway.ogcloud.svc")
	if err != nil {
		t.Fatalf("Client: %v", err)
	}

	if got := handshake(t, server, client); got != "first" {
		t.Errorf("served %q, want first", got)
	}
	ca.issue(t, dir, "second")
	// A renewal keeps the file names; a later modification time is what tells them apart.
	later := time.Now().Add(time.Minute)
	for _, name := range []string{"tls.crt", "tls.key"} {
		if err := os.Chtimes(filepath.Join(dir, name), later, later); err != nil {
			t.Fatalf("chtimes: %v", err)
		}
	}
	if got := handshake(t, server, client); got != "second" {
		t.Errorf("served %q after renewal, want second", got)
	}
}

func TestMissingOrForeignFilesAreRefused(t *testing.T) {
	dir := t.TempDir()
	if _, err := Server(filepath.Join(dir, "tls.crt"), filepath.Join(dir, "tls.key"), slog.New(slog.NewTextHandler(io.Discard, nil))); err == nil {
		t.Error("missing key pair accepted")
	}
	writeFile(t, filepath.Join(dir, "ca.crt"), []byte("not a certificate"))
	if _, err := Client(filepath.Join(dir, "ca.crt"), "x"); err == nil {
		t.Error("empty CA accepted")
	}
}

type authority struct {
	cert    *x509.Certificate
	key     *ecdsa.PrivateKey
	certPEM []byte
}

func newAuthority(t *testing.T) *authority {
	t.Helper()
	key := newKey(t)
	template := &x509.Certificate{
		SerialNumber:          big.NewInt(1),
		Subject:               pkix.Name{CommonName: "ogcloud internal CA"},
		NotBefore:             time.Now().Add(-time.Hour),
		NotAfter:              time.Now().Add(time.Hour),
		IsCA:                  true,
		BasicConstraintsValid: true,
		KeyUsage:              x509.KeyUsageCertSign,
	}
	der, err := x509.CreateCertificate(rand.Reader, template, template, &key.PublicKey, key)
	if err != nil {
		t.Fatalf("create CA: %v", err)
	}
	cert, err := x509.ParseCertificate(der)
	if err != nil {
		t.Fatalf("parse CA: %v", err)
	}
	return &authority{cert: cert, key: key, certPEM: pem.EncodeToMemory(&pem.Block{Type: "CERTIFICATE", Bytes: der})}
}

// issue writes tls.crt and tls.key for the gateway, with organization as a marker the test can read back.
func (a *authority) issue(t *testing.T, dir, organization string) {
	t.Helper()
	key := newKey(t)
	template := &x509.Certificate{
		SerialNumber: big.NewInt(time.Now().UnixNano()),
		Subject:      pkix.Name{CommonName: "gateway", Organization: []string{organization}},
		DNSNames:     []string{"gateway.ogcloud.svc"},
		NotBefore:    time.Now().Add(-time.Hour),
		NotAfter:     time.Now().Add(time.Hour),
		KeyUsage:     x509.KeyUsageDigitalSignature,
		ExtKeyUsage:  []x509.ExtKeyUsage{x509.ExtKeyUsageServerAuth},
	}
	der, err := x509.CreateCertificate(rand.Reader, template, a.cert, &key.PublicKey, a.key)
	if err != nil {
		t.Fatalf("issue: %v", err)
	}
	keyDER, err := x509.MarshalECPrivateKey(key)
	if err != nil {
		t.Fatalf("marshal key: %v", err)
	}
	writeFile(t, filepath.Join(dir, "tls.crt"), pem.EncodeToMemory(&pem.Block{Type: "CERTIFICATE", Bytes: der}))
	writeFile(t, filepath.Join(dir, "tls.key"), pem.EncodeToMemory(&pem.Block{Type: "EC PRIVATE KEY", Bytes: keyDER}))
}

func newKey(t *testing.T) *ecdsa.PrivateKey {
	t.Helper()
	key, err := ecdsa.GenerateKey(elliptic.P256(), rand.Reader)
	if err != nil {
		t.Fatalf("key: %v", err)
	}
	return key
}

func writeFile(t *testing.T, path string, content []byte) {
	t.Helper()
	if err := os.WriteFile(path, content, 0o600); err != nil {
		t.Fatalf("write %s: %v", path, err)
	}
}

// handshake connects client to a TLS listener serving server and returns the organization it was shown.
func handshake(t *testing.T, server, client *tls.Config) string {
	t.Helper()
	plain, err := (&net.ListenConfig{}).Listen(context.Background(), "tcp", "127.0.0.1:0")
	if err != nil {
		t.Fatalf("listen: %v", err)
	}
	listener := tls.NewListener(plain, server)
	defer func() { _ = listener.Close() }()
	go func() {
		conn, err := listener.Accept()
		if err != nil {
			return
		}
		_ = conn.(*tls.Conn).HandshakeContext(context.Background())
		_ = conn.Close()
	}()
	dialer := &tls.Dialer{Config: client}
	conn, err := dialer.DialContext(context.Background(), "tcp", listener.Addr().String())
	if err != nil {
		t.Fatalf("dial: %v", err)
	}
	defer func() { _ = conn.Close() }()
	return conn.(*tls.Conn).ConnectionState().PeerCertificates[0].Subject.Organization[0]
}
