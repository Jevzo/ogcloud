package bootstrap

import (
	"context"
	"crypto/rand"
	"encoding/json"
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
	namespace      = "ogcloud"
	seedName       = "ogcloud-seed"
	stateName      = "ogcloud-infisical-state"
	identitiesName = "ogcloud-infisical-identities"
	breakGlassName = "ogcloud-infisical-break-glass"
	// Holds only the forwarding tokens (D-0152).
	forwardingProject  = "ogcloud-forwarding"
	controllerAudience = "ogcloud-infisical"
)

// fixture is a cluster and an Infisical wired for Kubernetes auth, with token files on disk.
type fixture struct {
	kube   kubernetes.Interface
	config *Config
	api    *infisical.Client
	dir    string
	// Kubernetes logins still to fail, through a proxy in front of Infisical, with failStatus or else 503.
	failLogins atomic.Int32
	failStatus atomic.Int32
	// Set when a secret is written to Infisical while the identities Secret already exists.
	writtenAfterPublish atomic.Bool
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
	if _, err := f.api.KubernetesLogin(ctx, operatorID, testk3s.Token(t, f.kube, namespace, "ogcloud-controller", "", nil)); err == nil {
		t.Error("another service account logged in as the operator")
	}
	// The controller rotates the forwarding tokens in their own project and reads nothing else (D-0152), with a
	// token only Infisical accepts.
	controllerID := f.identityID(t, api, "ogcloud-controller")
	if _, err := f.api.KubernetesLogin(ctx, controllerID, testk3s.Token(t, f.kube, namespace, "ogcloud-controller", "", nil)); err == nil {
		t.Error("the controller logged in with a token for the API server")
	}
	token, err := f.api.KubernetesLogin(ctx, controllerID, testk3s.Token(t, f.kube, namespace, "ogcloud-controller", controllerAudience, nil))
	if err != nil {
		t.Fatalf("the controller cannot log in with its Infisical token: %v", err)
	}
	controller := f.api.WithToken(token)
	forwarding, err := controller.ProjectBySlug(ctx, forwardingProject)
	if err != nil {
		t.Fatalf("the controller cannot find its project: %v", err)
	}
	tokens := infisical.SecretRef{ProjectID: forwarding.ID, Environment: "prod", Path: "/", Name: "TOKENS"}
	if current := f.value(t, controller, forwarding.ID, "/", "TOKENS"); len(current) != 48 {
		t.Errorf("forwarding token = %q", current)
	}
	must(t, controller.PutSecret(ctx, tokens, "old\nnew", true))
	if got := f.value(t, operator, forwarding.ID, "/", "TOKENS"); got != "old\nnew" {
		t.Errorf("the operator syncs %q", got)
	}
	if _, _, err := controller.Secret(ctx, infisical.SecretRef{ProjectID: project.ID, Environment: "prod", Path: "/api", Name: "OGCLOUD_AUTH_SETUP_TOKEN"}); err == nil {
		t.Error("the controller reads the main project")
	}
	published, err := f.kube.CoreV1().Secrets(namespace).Get(ctx, identitiesName, metav1.GetOptions{})
	if err != nil || string(published.Data["ogcloud-secrets-operator"]) != operatorID {
		t.Errorf("the operator's identity id was not published for InfisicalAuth: %v", err)
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
	f.config.Infisical.Identities[0].Roles["ogcloud"] = "member"
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

	// A project the config no longer names for an identity drops it (D-0152).
	delete(f.config.Infisical.Identities[0].Roles, forwardingProject)
	f.run(t)
	forwardingID, err := api.ProjectBySlug(ctx, forwardingProject)
	must(t, err)
	if role, err := api.ProjectMemberRole(ctx, forwardingID.ID, operatorID); !infisical.IsNotFound(err) {
		t.Errorf("the operator kept its role %q in %s: %v", role, forwardingProject, err)
	}

	// A key only the seed brings fails the run until the seed carries it.
	f.config.Infisical.Seed.RequiredKeys = []string{"CLOUDFLARE_API_TOKEN", "BACKUP_S3_ACCESS_KEY"}
	if err := f.setup().Run(ctx); err == nil || !strings.Contains(err.Error(), "BACKUP_S3_ACCESS_KEY") ||
		strings.Contains(err.Error(), "CLOUDFLARE_API_TOKEN") {
		t.Errorf("a run without a required seed key: %v", err)
	}
	f.putSeed(t, map[string]string{"CLOUDFLARE_API_TOKEN": "cf-2", "BACKUP_S3_ACCESS_KEY": "backup-key"})
	f.run(t)

	// Infisical verifies the API server against the stored CA (D-0173); without one it would verify nothing.
	for _, id := range []string{f.state(t)[stateIdentity], operatorID, controllerID} {
		if !f.verifiesTLS(t, id) {
			t.Errorf("Infisical does not verify the API server for identity %s", id)
		}
	}

	// After a restore onto a new cluster the identities hold the lost cluster's CA, so the Kubernetes logins fail
	// until the break-glass credential re-points them.
	breakGlass, err := f.kube.CoreV1().Secrets(namespace).Get(ctx, breakGlassName, metav1.GetOptions{})
	if err != nil || len(breakGlass.Data["clientId"]) == 0 || len(breakGlass.Data["clientSecret"]) == 0 {
		t.Fatalf("no break-glass credential was stored: %v", err)
	}
	f.failStatus.Store(http.StatusUnauthorized)
	f.failLogins.Store(1)
	f.run(t)
	f.loginAs(t, f.state(t)[stateIdentity], "ogcloud-bootstrap")
	kept, err := f.kube.CoreV1().Secrets(namespace).Get(ctx, breakGlassName, metav1.GetOptions{})
	if err != nil || string(kept.Data["clientSecret"]) != string(breakGlass.Data["clientSecret"]) {
		t.Errorf("a run replaced the break-glass credential: %v", err)
	}
	// Re-pointed identities that still cannot log in fail the run, though it re-points them all.
	f.failLogins.Store(1 << 20)
	if err := f.setup().Run(ctx); err == nil || !strings.Contains(err.Error(), "still fails") {
		t.Errorf("a run whose re-pointed login still fails: %v", err)
	}
	// A busy or unreachable Infisical is no reason to break the glass.
	f.failStatus.Store(http.StatusServiceUnavailable)
	if err := f.setup().Run(ctx); err == nil || strings.Contains(err.Error(), "re-pointed") {
		t.Errorf("a run while Infisical's logins are unavailable: %v", err)
	}
	f.failLogins.Store(0)

	// A break-glass secret revoked in the UI is noticed and replaced before a restore needs it.
	bootstrapID := f.state(t)[stateIdentity]
	ids, err := api.ClientSecretIDs(ctx, bootstrapID)
	must(t, err)
	for _, id := range ids {
		must(t, api.RevokeClientSecret(ctx, bootstrapID, id))
	}
	f.run(t)
	replaced, err := f.kube.CoreV1().Secrets(namespace).Get(ctx, breakGlassName, metav1.GetOptions{})
	must(t, err)
	if string(replaced.Data["clientSecret"]) == string(breakGlass.Data["clientSecret"]) {
		t.Error("a revoked break-glass credential was kept")
	}
	if _, err := f.api.UniversalAuthLogin(ctx, string(replaced.Data["clientId"]), string(replaced.Data["clientSecret"])); err != nil {
		t.Errorf("the replaced break-glass credential does not log in: %v", err)
	}

	// Without its state, bootstrap refuses to guess.
	must(t, f.kube.CoreV1().ConfigMaps(namespace).Delete(ctx, stateName, metav1.DeleteOptions{}))
	f.putSeed(t, map[string]string{"INFISICAL_ADMIN_EMAIL": "x@example.com", "INFISICAL_ADMIN_PASSWORD": "another-long-passw0rd!"})
	if err := f.setup().Run(ctx); !errors.Is(err, infisical.ErrAlreadyBootstrapped) {
		t.Errorf("got %v", err)
	}
}

// The Secrets Operator logs in with the published identities and renders every Secret from what the project holds
// at that moment; a workload that reads its Secret once, like SeaweedFS its S3 users, keeps a half-written one.
func TestSetupPublishesTheIdentitiesOnlyOnceTheProjectHoldsEverySecret(t *testing.T) {
	f := newFixture(t)
	f.putSeed(t, map[string]string{
		"INFISICAL_ADMIN_EMAIL": "owner@example.com", "INFISICAL_ADMIN_PASSWORD": "a-very-long-admin-passw0rd!",
		"CLOUDFLARE_API_TOKEN": "cf-1",
	})

	f.run(t)

	if _, err := f.kube.CoreV1().Secrets(namespace).Get(context.Background(), identitiesName, metav1.GetOptions{}); err != nil {
		t.Fatalf("the identities were never published: %v", err)
	}
	if f.writtenAfterPublish.Load() {
		t.Error("a secret was written to Infisical after the identities were published")
	}
}

func newFixture(t *testing.T) *fixture {
	t.Helper()
	ctx := context.Background()
	env := testinfisical.Start(t)
	kube := env.Cluster.Client
	dir := t.TempDir()
	must(t, create(ctx, kube.CoreV1().Namespaces(), &corev1.Namespace{ObjectMeta: metav1.ObjectMeta{Name: namespace}}))
	// Accounts that log in with API tokens review themselves; the controller's token has another audience, so the
	// reviewer account reviews it (D-0167). The chart binds them the same way.
	for _, account := range []string{"infisical-reviewer", "ogcloud-bootstrap", "infisical-secrets-operator", "ogcloud-controller"} {
		must(t, create(ctx, kube.CoreV1().ServiceAccounts(namespace), &corev1.ServiceAccount{ObjectMeta: metav1.ObjectMeta{Name: account}}))
	}
	reviewers := make([]rbacv1.Subject, 0, 3)
	for _, account := range []string{"infisical-reviewer", "ogcloud-bootstrap", "infisical-secrets-operator"} {
		reviewers = append(reviewers, rbacv1.Subject{Kind: "ServiceAccount", Name: account, Namespace: namespace})
	}
	must(t, create(ctx, kube.RbacV1().ClusterRoleBindings(), &rbacv1.ClusterRoleBinding{
		ObjectMeta: metav1.ObjectMeta{Name: "infisical-token-review"},
		RoleRef:    rbacv1.RoleRef{APIGroup: "rbac.authorization.k8s.io", Kind: "ClusterRole", Name: "system:auth-delegator"},
		Subjects:   reviewers,
	}))
	f := &fixture{kube: kube, dir: dir}
	target, err := url.Parse(env.URL)
	must(t, err)
	upstream := httputil.NewSingleHostReverseProxy(target)
	proxy := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if strings.HasSuffix(r.URL.Path, "/kubernetes-auth/login") && f.failLogins.Add(-1) >= 0 {
			status := int(f.failStatus.Load())
			if status == 0 {
				status = http.StatusServiceUnavailable
			}
			http.Error(w, `{"message":"login failed"}`, status)
			return
		}
		if r.Method != http.MethodGet && strings.HasPrefix(r.URL.Path, "/api/v4/secrets/") {
			if _, err := kube.CoreV1().Secrets(namespace).Get(r.Context(), identitiesName, metav1.GetOptions{}); err == nil {
				f.writtenAfterPublish.Store(true)
			}
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
			URL: env.URL, Organization: "OgCloud", Project: "ogcloud", Projects: []string{forwardingProject}, Environment: "prod",
			StateConfigMap:          stateName,
			IdentitiesSecret:        identitiesName,
			ServiceAccountTokenFile: filepath.Join(dir, "bootstrap"),
			BreakGlassSecret:        breakGlassName,
			Kubernetes: KubernetesAuth{
				Host: testinfisical.KubernetesHost, CAFile: filepath.Join(dir, "ca.crt"), ReviewerTokenFile: filepath.Join(dir, "reviewer"),
			},
			BootstrapIdentity: IdentitySpec{Name: "ogcloud-bootstrap", ServiceAccounts: []string{"ogcloud/ogcloud-bootstrap"}},
			Identities: []IdentitySpec{
				{
					Name: "ogcloud-secrets-operator", ServiceAccounts: []string{"ogcloud/infisical-secrets-operator"},
					Roles: map[string]string{"ogcloud": "viewer", forwardingProject: "viewer"},
				},
				{
					Name: "ogcloud-controller", ServiceAccounts: []string{"ogcloud/ogcloud-controller"},
					Roles: map[string]string{forwardingProject: "member"}, Audience: controllerAudience,
				},
			},
			Secrets: []GeneratedSecret{
				{Path: "/api", Name: "OGCLOUD_AUTH_SETUP_TOKEN", Generator: Generator{Generate: "base64url", Bytes: 32}},
				{Path: "/api/signing", Name: "KEY_1", Generator: Generator{Generate: "rsa", Bits: 2048}},
				{Project: forwardingProject, Path: "/", Name: "TOKENS", Generator: Generator{Generate: "alnum", Length: 48}},
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

// verifiesTLS reads the identity's Kubernetes auth as the bootstrap identity, the organization's admin; the typed
// client has no use for the setting outside this test.
func (f *fixture) verifiesTLS(t *testing.T, identityID string) bool {
	t.Helper()
	ctx := context.Background()
	token, err := f.api.KubernetesLogin(ctx, f.state(t)[stateIdentity], testk3s.Token(t, f.kube, namespace, "ogcloud-bootstrap", "", nil))
	must(t, err)
	request, err := http.NewRequestWithContext(ctx, http.MethodGet,
		f.config.Infisical.URL+"/api/v1/auth/kubernetes-auth/identities/"+identityID, nil)
	must(t, err)
	request.Header.Set("Authorization", "Bearer "+token)
	response, err := http.DefaultClient.Do(request)
	must(t, err)
	defer func() { _ = response.Body.Close() }()
	var auth struct {
		Kubernetes struct {
			VerifyTLS bool `json:"verifyTlsCertificate"`
		} `json:"identityKubernetesAuth"`
	}
	if response.StatusCode != http.StatusOK {
		t.Fatalf("read the kubernetes auth of %s: %s", identityID, response.Status)
	}
	must(t, json.NewDecoder(response.Body).Decode(&auth))
	return auth.Kubernetes.VerifyTLS
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
