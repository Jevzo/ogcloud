//go:build contracttest

package main

import (
	"context"
	"crypto/ecdsa"
	"crypto/elliptic"
	"crypto/rand"
	"crypto/tls"
	"crypto/x509"
	"crypto/x509/pkix"
	"encoding/json"
	"encoding/pem"
	"fmt"
	"log/slog"
	"math/big"
	"net"
	"os"
	"time"

	corev1 "k8s.io/api/core/v1"
	metav1 "k8s.io/apimachinery/pkg/apis/meta/v1"
	"k8s.io/apimachinery/pkg/types"

	"github.com/jevzo/ogcloud/data-plane/pkg/config"
	"github.com/jevzo/ogcloud/data-plane/pkg/kube"
)

// security of the contract-test build (T-0510), only in the never-published gateway-contracttest image: pods from
// a JSON file (a token names its pod, still checked by the real authenticator), plaintext deps, a self-signed cert.
type security struct {
	caOut, podsFile string
}

func loadSecurity(env *config.Source) (security, error) {
	return security{caOut: env.Required("OGCLOUD_CONTRACT_CA_OUT"), podsFile: env.Required("OGCLOUD_CONTRACT_PODS")}, nil
}

const certificateLifetime = 24 * time.Hour

// serverTLS signs a certificate for the names the test reaches the gateway under, and writes it where the test
// reads the CA from.
func serverTLS(s security, logger *slog.Logger) (*tls.Config, error) {
	key, err := ecdsa.GenerateKey(elliptic.P256(), rand.Reader)
	if err != nil {
		return nil, fmt.Errorf("generate key: %w", err)
	}
	template := &x509.Certificate{
		SerialNumber:          big.NewInt(1),
		Subject:               pkix.Name{CommonName: "ogcloud gateway contract test"},
		DNSNames:              []string{"localhost", "gateway"},
		IPAddresses:           []net.IP{net.IPv4(127, 0, 0, 1)},
		NotBefore:             time.Now().Add(-time.Minute),
		NotAfter:              time.Now().Add(certificateLifetime),
		KeyUsage:              x509.KeyUsageDigitalSignature | x509.KeyUsageCertSign,
		ExtKeyUsage:           []x509.ExtKeyUsage{x509.ExtKeyUsageServerAuth},
		BasicConstraintsValid: true,
		IsCA:                  true,
	}
	der, err := x509.CreateCertificate(rand.Reader, template, template, &key.PublicKey, key)
	if err != nil {
		return nil, fmt.Errorf("create certificate: %w", err)
	}
	if err := os.WriteFile(s.caOut, pem.EncodeToMemory(&pem.Block{Type: "CERTIFICATE", Bytes: der}), 0o600); err != nil {
		return nil, fmt.Errorf("write %s: %w", s.caOut, err)
	}
	logger.Warn("contract-test build: self-signed certificate, pods from a file, no TokenReview")
	return &tls.Config{
		MinVersion:   tls.VersionTLS13,
		Certificates: []tls.Certificate{{Certificate: [][]byte{der}, PrivateKey: key}},
	}, nil
}

type fakePod struct {
	Name   string            `json:"name"`
	UID    string            `json:"uid"`
	Labels map[string]string `json:"labels"`
}

type filePods map[string]*corev1.Pod

func (p filePods) Pod(name string) (*corev1.Pod, bool) {
	pod, ok := p[name]
	return pod, ok
}

// tokenIsPodName accepts a token naming a known pod, as if TokenReview had bound it to that pod.
type tokenIsPodName struct {
	namespace string
	pods      filePods
}

func (r tokenIsPodName) Review(_ context.Context, token string) (kube.Identity, error) {
	pod, ok := r.pods[token]
	if !ok {
		return kube.Identity{}, fmt.Errorf("unknown pod %q: %w", token, kube.ErrUnauthenticated)
	}
	return kube.Identity{Namespace: r.namespace, PodName: pod.Name, PodUID: string(pod.UID)}, nil
}

func identities(_ context.Context, s settings, _ *slog.Logger) (tokenReviewer, podLookup, error) {
	data, err := os.ReadFile(s.security.podsFile)
	if err != nil {
		return nil, nil, fmt.Errorf("read pods: %w", err)
	}
	var listed []fakePod
	if err := json.Unmarshal(data, &listed); err != nil {
		return nil, nil, fmt.Errorf("decode pods: %w", err)
	}
	pods := filePods{}
	for _, p := range listed {
		pods[p.Name] = &corev1.Pod{ObjectMeta: metav1.ObjectMeta{Name: p.Name, Namespace: s.namespace, UID: types.UID(p.UID), Labels: p.Labels}}
	}
	return tokenIsPodName{namespace: s.namespace, pods: pods}, pods, nil
}

// The dependencies speak plaintext in the contract test.
func (security) clientTLS() *tls.Config { return nil }
