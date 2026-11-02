package bootstrap

import (
	"crypto/rand"
	"crypto/x509"
	"encoding/pem"
	"os"
	"path/filepath"
	"strings"
	"testing"
)

func TestGeneratorsProduceTheirKind(t *testing.T) {
	tests := []struct {
		generator Generator
		check     func(string) bool
	}{
		{Generator{Generate: "hex", Bytes: 16}, func(v string) bool { return len(v) == 32 }},
		{Generator{Generate: "base64url", Bytes: 32}, func(v string) bool { return len(v) == 43 && !strings.ContainsAny(v, "+/=") }},
		{Generator{Generate: "alnum", Length: 24}, func(v string) bool { return len(v) == 24 && strings.Trim(v, alphabet) == "" }},
		{Generator{Value: "fixed"}, func(v string) bool { return v == "fixed" }},
		{Generator{Generate: "rsa", Bits: 2048}, func(v string) bool {
			block, _ := pem.Decode([]byte(v))
			if block == nil {
				return false
			}
			_, err := x509.ParsePKCS8PrivateKey(block.Bytes)
			return err == nil
		}},
	}
	for _, tt := range tests {
		value, err := tt.generator.Make(rand.Reader)
		if err != nil || !tt.check(value) {
			t.Errorf("%+v made %q (%v)", tt.generator, value, err)
		}
	}
}

func TestBadConfigsAreRefusedBeforeAnythingIsWritten(t *testing.T) {
	path := filepath.Join(t.TempDir(), "bootstrap.yaml")
	config := `namespace: ogcloud
rootSecrets:
  - name: root
    keys:
      WEAK: {generate: hex, bytes: 4}
      BOTH: {generate: hex, bytes: 16, value: x}
      ODD: {generate: magic}
infisical:
  identities:
    - name: operator
      serviceAccounts: [no-slash]
  secrets:
    - {path: relative, name: X, generate: alnum, length: 32}
`
	if err := os.WriteFile(path, []byte(config), 0o600); err != nil {
		t.Fatalf("write: %v", err)
	}

	_, err := LoadConfig(path)

	for _, want := range []string{"at least 16 bytes", "both a value and a generator", `unknown generator "magic"`, "not namespace/name", "absolute path"} {
		if err == nil || !strings.Contains(err.Error(), want) {
			t.Errorf("missing %q in %v", want, err)
		}
	}
}
