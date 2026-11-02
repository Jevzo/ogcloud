package bootstrap

import (
	"crypto/rand"
	"crypto/rsa"
	"crypto/x509"
	"encoding/base64"
	"encoding/hex"
	"encoding/pem"
	"fmt"
	"io"
	"math/big"
)

const (
	// 16 random bytes is the floor for anything secret.
	minBytes   = 16
	minRSABits = 2048
	alphabet   = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
)

// Make returns the generator's value, drawing randomness from random.
func (g Generator) Make(random io.Reader) (string, error) {
	if g.Generate == "" {
		return g.Value, nil
	}
	if g.Generate == "rsa" {
		return rsaKey(random, g.Bits)
	}
	if g.Generate == "alnum" {
		return alnum(random, g.Length)
	}
	raw := make([]byte, g.Bytes)
	if _, err := io.ReadFull(random, raw); err != nil {
		return "", fmt.Errorf("random bytes: %w", err)
	}
	switch g.Generate {
	case "hex":
		return hex.EncodeToString(raw), nil
	case "base64":
		return base64.StdEncoding.EncodeToString(raw), nil
	default:
		return base64.RawURLEncoding.EncodeToString(raw), nil
	}
}

func alnum(random io.Reader, length int) (string, error) {
	out := make([]byte, length)
	limit := big.NewInt(int64(len(alphabet)))
	for i := range out {
		n, err := rand.Int(random, limit)
		if err != nil {
			return "", fmt.Errorf("random index: %w", err)
		}
		out[i] = alphabet[n.Int64()]
	}
	return string(out), nil
}

// rsaKey returns a PKCS#8 PEM private key, as the API reads its signing keys (D-0090).
func rsaKey(random io.Reader, bits int) (string, error) {
	key, err := rsa.GenerateKey(random, bits)
	if err != nil {
		return "", fmt.Errorf("generate RSA key: %w", err)
	}
	der, err := x509.MarshalPKCS8PrivateKey(key)
	if err != nil {
		return "", fmt.Errorf("encode RSA key: %w", err)
	}
	return string(pem.EncodeToMemory(&pem.Block{Type: "PRIVATE KEY", Bytes: der})), nil
}
