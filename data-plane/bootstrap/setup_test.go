package bootstrap

import (
	"context"
	"crypto/rand"
	"errors"
	"io"
	"log/slog"
	"net/http"
	"net/http/httptest"
	"net/http/httputil"
	"net/url"
	"os"
	"path/filepath"
	"strings"
	"sync/atomic"
	"testing"

	corev1 "k8s.io/api/core/v1"
	rbacv1 "k8s.io/api/rbac/v1"
	metav1 "k8s.io/apimachinery/pkg/apis/meta/v1"
	"k8s.io/client-go/kubernetes"

	"github.com/jevzo/ogcloud/data-plane/bootstrap/infisical"
	"github.com/jevzo/ogcloud/data-plane/internal/testinfisical"
	"github.com/jevzo/ogcloud/data-plane/internal/testk3s"
)

const (
	namespace = "ogcloud"
	seedName  = "ogcloud-seed"
	stateName = "ogcloud-infisical-state"
)

// fixture is a cluster and an Infisical wired for Kubernetes auth, with token files on disk.
type fixture struct {
	kube   kubernetes.Interface
	config *Config
	api    *infisical.Client
	dir    string
	// Kubernetes logins still to fail with 503, through a proxy in front of Infisical.
	failLogins atomic.Int32
}

func TestSetupBootstrapsOnceRevokesTheAdminAndNeverOverwrites(t *testing.T) {
	ctx := context.Background()
	f := newFixture(t)
	f.putSeed(t, map[string]string{
		"INFISICAL_ADMIN_EMAIL": "owner@example.com", "INFISICAL_ADMIN_PASSWORD": "a-very-long-admin-passw0rd!",
		"CLOUDFLARE_API_TOKEN": "cf-1",
	})

	// The first scoped login fails once; the run rides it out instead of losing the admin token.
	f.failLogins.Store(1)
	f.run(t)

	api := f.loginAs(t, f.state(t)[stateIdentity], "ogcloud-bootstrap")
	project, err := api.ProjectBySlug(ctx, "ogcloud")
	if err != nil {
		t.Fatalf("project: %v", err)
	}
	setupToken := f.value(t, api, project.ID, "/api", "OGCLOUD_AUTH_SETUP_TOKEN")
	if len(setupToken) != 43 {
		t.Errorf("setup token %q is not 32 base64url bytes", setupToken)
	}
	if key := f.value(t, api, project.ID, "/api/signing", "KEY_1"); !strings.HasPrefix(key, "-----BEGIN PRIVATE KEY-----") {
		t.Errorf("signing key = %q", key)
	}
	if got := f.value(t, api, project.ID, "/seed", "CLOUDFLARE_API_TOKEN"); got != "cf-1" {
		t.Errorf("seeded token = %q", got)
	}
	for _, credential := range []string{"INFISICAL_ADMIN_EMAIL", "INFISICAL_ADMIN_PASSWORD"} {
		ref := infisical.SecretRef{ProjectID: project.ID, Environment: "prod", Path: "/seed", Name: credential}
		if _, found, err := api.Secret(ctx, ref); err != nil || found {
			t.Errorf("the admin credential %s was stored (D-0114): %v", credential, err)
		}
	}
	f.assertSeedGone(t)
	identities, err := api.Identities(ctx, f.state(t)[stateOrganization])
	if err != nil {
		t.Fatalf("identities: %v", err)
	}
	for _, identity := range identities {
		if identity.Name == "Instance Admin Identity" {
			t.Error("the instance admin identity survived (D-0112)")
		}
	}
	operatorID := f.identityID(t, api, "ogcloud-secrets-operator")
	operator := f.loginAs(t, operatorID, "infisical-secrets-operator")
	if got := f.value(t, operator, project.ID, "/seed", "CLOUDFLARE_API_TOKEN"); got != "cf-1" {
		t.Errorf("the operator read %q", got)
	}
	if _, err := f.api.KubernetesLogin(ctx, operatorID, testk3s.Token(t, f.kube, namespace, "infisical-reviewer", "", nil)); err == nil {
		t.Error("another service account logged in as the operator")
	}

	// An edit in Infisical survives a rerun with the same seed; a changed seed is imported.
	ref := infisical.SecretRef{ProjectID: project.ID, Environment: "prod", Path: "/seed", Name: "CLOUDFLARE_API_TOKEN"}
	must(t, api.PutSecret(ctx, ref, "edited-in-ui", true))
	f.putSeed(t, map[string]string{"CLOUDFLARE_API_TOKEN": "cf-1"})
	f.run(t)
	if got := f.value(t, api, project.ID, "/seed", "CLOUDFLARE_API_TOKEN"); got != "edited-in-ui" {
		t.Errorf("an unchanged seed overwrote an edit: %q", got)
	}
	f.assertSeedGone(t)

	// A changed role in the config reaches the identity.
	f.config.Infisical.Identities[0].Role = "member"
	f.run(t)
	if role, err := api.ProjectMemberRole(ctx, project.ID, operatorID); err != nil || role != "member" {
		t.Errorf("operator role = %q, %v", role, err)
	}
	f.putSeed(t, map[string]string{"CLOUDFLARE_API_TOKEN": "cf-2"})
	f.run(t)
	if got := f.value(t, api, project.ID, "/seed", "CLOUDFLARE_API_TOKEN"); got != "cf-2" {
		t.Errorf("a changed seed was not imported: %q", got)
	}
	if got := f.value(t, api, project.ID, "/api", "OGCLOUD_AUTH_SETUP_TOKEN"); got != setupToken {
		t.Error("a rerun replaced a generated secret")
	}

	// Without its state, bootstrap refuses to guess.
	must(t, f.kube.CoreV1().ConfigMaps(namespace).Delete(ctx, stateName, metav1.DeleteOptions{}))
	f.putSeed(t, map[string]string{"INFISICAL_ADMIN_EMAIL": "x@example.com", "INFISICAL_ADMIN_PASSWORD": "another-long-passw0rd!"})
	if err := f.setup().Run(ctx); !errors.Is(err, infisical.ErrAlreadyBootstrapped) {
		t.Errorf("got %v", err)
	}
}

func newFixture(t *testing.T) *fixture {
	t.Helper()
	ctx := context.Background()
	env := testinfisical.Start(t)
	kube := env.Cluster.Client
	dir := t.TempDir()
	must(t, create(ctx, kube.CoreV1().Namespaces(), &corev1.Namespace{ObjectMeta: metav1.ObjectMeta{Name: namespace}}))
	for _, account := range []string{"infisical-reviewer", "ogcloud-bootstrap", "infisical-secrets-operator"} {
		must(t, create(ctx, kube.CoreV1().ServiceAccounts(namespace), &corev1.ServiceAccount{ObjectMeta: metav1.ObjectMeta{Name: account}}))
	}
	must(t, create(ctx, kube.RbacV1().ClusterRoleBindings(), &rbacv1.ClusterRoleBinding{
		ObjectMeta: metav1.ObjectMeta{Name: "infisical-reviewer"},
		RoleRef:    rbacv1.RoleRef{APIGroup: "rbac.authorization.k8s.io", Kind: "ClusterRole", Name: "system:auth-delegator"},
		Subjects:   []rbacv1.Subject{{Kind: "ServiceAccount", Name: "infisical-reviewer", Namespace: namespace}},
	}))
	f := &fixture{kube: kube, dir: dir}
	target, err := url.Parse(env.URL)
	must(t, err)
	upstream := httputil.NewSingleHostReverseProxy(target)
	proxy := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if strings.HasSuffix(r.URL.Path, "/kubernetes-auth/login") && f.failLogins.Add(-1) >= 0 {
			http.Error(w, `{"message":"try again"}`, http.StatusServiceUnavailable)
			return
		}
		upstream.ServeHTTP(w, r)
	}))
	t.Cleanup(proxy.Close)
	f.api = infisical.New(proxy.URL, nil)
	writeFile(t, filepath.Join(dir, "reviewer"), testk3s.Token(t, f.kube, namespace, "infisical-reviewer", "", nil))
	writeFile(t, filepath.Join(dir, "bootstrap"), testk3s.Token(t, f.kube, namespace, "ogcloud-bootstrap", "", nil))
	writeFile(t, filepath.Join(dir, "ca.crt"), string(env.Cluster.Config.CAData))
	f.config = &Config{
		Namespace: namespace,
		Infisical: InfisicalSetup{
			URL: env.URL, Organization: "OgCloud", Project: "ogcloud", Environment: "prod",
			StateConfigMap:          stateName,
			ServiceAccountTokenFile: filepath.Join(dir, "bootstrap"),
			Kubernetes: KubernetesAuth{
				Host: testinfisical.KubernetesHost, CAFile: filepath.Join(dir, "ca.crt"), ReviewerTokenFile: filepath.Join(dir, "reviewer"),
			},
			BootstrapIdentity: IdentitySpec{Name: "ogcloud-bootstrap", ServiceAccounts: []string{"ogcloud/ogcloud-bootstrap"}},
			Identities: []IdentitySpec{
				{Name: "ogcloud-secrets-operator", ServiceAccounts: []string{"ogcloud/infisical-secrets-operator"}, Role: "viewer"},
			},
			Secrets: []GeneratedSecret{
				{Path: "/api", Name: "OGCLOUD_AUTH_SETUP_TOKEN", Generator: Generator{Generate: "base64url", Bytes: 32}},
				{Path: "/api/signing", Name: "KEY_1", Generator: Generator{Generate: "rsa", Bits: 2048}},
			},
			Seed: Seed{Secret: seedName, Path: "/seed", AdminEmailKey: "INFISICAL_ADMIN_EMAIL", AdminPasswordKey: "INFISICAL_ADMIN_PASSWORD"},
		},
	}
	must(t, f.config.Validate())
	must(t, f.config.ValidateInfisical())
	return f
}

func (f *fixture) setup() *Setup {
	return &Setup{
		Kube: f.kube, API: f.api, Config: f.config, ReadFile: os.ReadFile, Random: rand.Reader,
		Logger: slog.New(slog.NewTextHandler(io.Discard, nil)), Attempts: 3,
	}
}

func (f *fixture) run(t *testing.T) {
	t.Helper()
	if err := f.setup().Run(context.Background()); err != nil {
		t.Fatalf("Run: %v", err)
	}
}

func (f *fixture) loginAs(t *testing.T, identityID, account string) *infisical.Client {
	t.Helper()
	token, err := f.api.KubernetesLogin(context.Background(), identityID, testk3s.Token(t, f.kube, namespace, account, "", nil))
	if err != nil {
		t.Fatalf("login as %s: %v", account, err)
	}
	return f.api.WithToken(token)
}

func (f *fixture) identityID(t *testing.T, api *infisical.Client, name string) string {
	t.Helper()
	identities, err := api.Identities(context.Background(), f.state(t)[stateOrganization])
	must(t, err)
	for _, identity := range identities {
		if identity.Name == name {
			return identity.ID
		}
	}
	t.Fatalf("no identity %s", name)
	return ""
}

func (f *fixture) value(t *testing.T, api *infisical.Client, projectID, path, name string) string {
	t.Helper()
	value, found, err := api.Secret(context.Background(), infisical.SecretRef{ProjectID: projectID, Environment: "prod", Path: path, Name: name})
	if err != nil || !found {
		t.Fatalf("secret %s%s: found %v, %v", path, name, found, err)
	}
	return value
}

func (f *fixture) state(t *testing.T) map[string]string {
	t.Helper()
	configMap, err := f.kube.CoreV1().ConfigMaps(namespace).Get(context.Background(), stateName, metav1.GetOptions{})
	must(t, err)
	return configMap.Data
}

func (f *fixture) putSeed(t *testing.T, data map[string]string) {
	t.Helper()
	must(t, create(context.Background(), f.kube.CoreV1().Secrets(namespace),
		&corev1.Secret{ObjectMeta: metav1.ObjectMeta{Name: seedName}, StringData: data}))
}

func (f *fixture) assertSeedGone(t *testing.T) {
	t.Helper()
	if _, err := f.kube.CoreV1().Secrets(namespace).Get(context.Background(), seedName, metav1.GetOptions{}); err == nil {
		t.Error("the seed Secret was not deleted")
	}
}

// create is any typed client's Create.
func create[T any](ctx context.Context, client interface {
	Create(context.Context, T, metav1.CreateOptions) (T, error)
}, object T,
) error {
	_, err := client.Create(ctx, object, metav1.CreateOptions{})
	return err
}

func writeFile(t *testing.T, path, content string) {
	t.Helper()
	must(t, os.WriteFile(path, []byte(content), 0o600))
}

func must(t *testing.T, err error) {
	t.Helper()
	if err != nil {
		t.Fatal(err)
	}
}
