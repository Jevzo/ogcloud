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

	for _, want := range []string{"at least 16 bytes", "both a value and a generator", `unknown generator "magic"`, "absolute path"} {
		if err == nil || !strings.Contains(err.Error(), want) {
			t.Errorf("missing %q in %v", want, err)
		}
	}
}

func TestTheInfisicalStageNeedsEverythingBeforeSpendingTheAdminToken(t *testing.T) {
	viewer := map[string]string{"ogcloud": "viewer"}
	config := Config{Namespace: "ogcloud", Infisical: InfisicalSetup{
		Project:           "ogcloud",
		BootstrapIdentity: IdentitySpec{Name: "bootstrap", ServiceAccounts: []string{"ogcloud/bootstrap"}},
		Identities: []IdentitySpec{
			{Name: "bootstrap", Roles: viewer, ServiceAccounts: []string{"ogcloud/api"}},
			{Name: "operator", Roles: viewer, ServiceAccounts: []string{"no-slash"}},
			{Name: "spread", Roles: viewer, ServiceAccounts: []string{"ogcloud/api", "ogcloud-servers/game"}},
			{Name: "anyone", Roles: viewer},
			{Name: "roleless", ServiceAccounts: []string{"ogcloud/x"}},
			{Name: "stray", Roles: map[string]string{"elsewhere": "member"}, ServiceAccounts: []string{"ogcloud/y"}},
		},
		Secrets: []GeneratedSecret{{Project: "elsewhere", Path: "/", Name: "LOST", Generator: Generator{Value: "x"}}},
		Seed:    Seed{Path: "relative"},
	}}

	err := config.ValidateInfisical()

	for _, want := range []string{
		"infisical.url is required", "infisical.kubernetes.reviewerTokenFile is required", "infisical.seed.adminPasswordKey is required",
		"seed.path must be absolute", `identity name "bootstrap" is used twice`, "not namespace/name", "share one namespace",
		"an empty list would admit any", "roleless needs a project role", `stray: role "member" in project "elsewhere"`,
		"secret /LOST: project elsewhere is not configured",
	} {
		if err == nil || !strings.Contains(err.Error(), want) {
			t.Errorf("missing %q in %v", want, err)
		}
	}
}

func TestGeneratorsRefuseToMakeWeakValues(t *testing.T) {
	if value, err := (Generator{Generate: "hex"}).Make(rand.Reader); err == nil {
		t.Errorf("made %q from a generator without a size", value)
	}
}
