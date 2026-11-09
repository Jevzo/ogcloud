package bootstrap

import (
	"context"
	"crypto/sha256"
	"encoding/hex"
	"errors"
	"fmt"
	"io"
	"log/slog"
	"slices"
	"strings"
	"time"

	corev1 "k8s.io/api/core/v1"
	apierrors "k8s.io/apimachinery/pkg/api/errors"
	metav1 "k8s.io/apimachinery/pkg/apis/meta/v1"
	"k8s.io/client-go/kubernetes"

	"github.com/jevzo/ogcloud/data-plane/bootstrap/infisical"
)

const (
	stateOrganization = "organizationId"
	stateIdentity     = "bootstrapIdentityId"
	// Set while the instance admin identity outlived the first run; only an instance admin can delete it.
	statePendingRevocation = "unrevokedAdminIdentityId"
	// Holds the seed's hash, so an unchanged seed never overwrites edits made since (D-0044).
	seedHashName = "OGCLOUD_SEED_SHA256"
	// The break-glass Secret's keys (D-0173); its access tokens live only as long as a run needs.
	breakGlassClientID     = "clientId"
	breakGlassClientSecret = "clientSecret"
	breakGlassTokenTTL     = 15 * time.Minute
	// Identities get project roles; in the organization they hold nothing.
	organizationNoAccess = "no-access"
	organizationAdmin    = "admin"
)

// Setup is stage two (D-0044, D-0112): the first run bootstraps the instance and swaps the admin token for
// an organization-scoped identity that logs in with Kubernetes auth; every run then ensures the project, the
// identities, the generated secrets and the seed import.
type Setup struct {
	Kube     kubernetes.Interface
	API      *infisical.Client
	Config   *Config
	ReadFile func(string) ([]byte, error)
	Random   io.Reader
	Logger   *slog.Logger
	// Each step after the one-time bootstrap is tried this often, Pause apart: the admin token lives only in
	// this process, so the first run has to ride out short outages itself.
	Attempts int
	Pause    time.Duration
}

type state struct {
	organizationID    string
	identityID        string
	pendingRevocation string
}

// Run performs one idempotent pass.
func (s *Setup) Run(ctx context.Context) error {
	if err := s.API.Ready(ctx); err != nil {
		return fmt.Errorf("infisical not ready: %w", err)
	}
	current, err := s.loadState(ctx)
	if err != nil {
		return err
	}
	if current.pendingRevocation != "" {
		s.Logger.Error("the instance admin identity was never revoked (D-0112); delete it in Infisical's admin console "+
			"and remove the key from the state ConfigMap", "identityId", current.pendingRevocation, "key", statePendingRevocation)
	}
	if current.identityID == "" {
		if current, err = s.bootstrapInstance(ctx); err != nil {
			return err
		}
	}
	api, err := s.login(ctx, current.identityID)
	if err != nil {
		return err
	}
	setup := s.Config.Infisical
	// Its own login first, so a run after a restore leaves the next one on Kubernetes auth again (D-0173).
	if err := s.setKubernetesAuth(ctx, api, current.identityID, setup.BootstrapIdentity); err != nil {
		return err
	}
	if err := s.ensureBreakGlass(ctx, api, current.identityID); err != nil {
		return err
	}
	projects := map[string]string{}
	for _, slug := range setup.ProjectSlugs() {
		project, err := s.ensureProject(ctx, api, slug)
		if err != nil {
			return err
		}
		projects[slug] = project.ID
	}
	ids := make(map[string]string, len(setup.Identities))
	for _, spec := range setup.Identities {
		id, err := s.ensureIdentity(ctx, api, current.organizationID, projects, spec)
		if err != nil {
			return fmt.Errorf("identity %s: %w", spec.Name, err)
		}
		ids[spec.Name] = id
	}
	if err := s.publishIdentities(ctx, ids); err != nil {
		return err
	}
	for _, secret := range setup.Secrets {
		if err := s.ensureSecret(ctx, api, projects[secret.project(setup)], secret); err != nil {
			return fmt.Errorf("secret %s%s: %w", secret.Path, secret.Name, err)
		}
	}
	return s.importSeed(ctx, api, projects[setup.Project])
}

// bootstrapInstance runs once per installation: the admin token creates the organization-scoped identity
// and then deletes its own identity, so no root-equivalent credential remains (D-0112). The token cannot be
// fetched again, so everything is checked first and every later step is retried in process.
func (s *Setup) bootstrapInstance(ctx context.Context) (state, error) {
	setup := s.Config.Infisical
	seed, err := s.seed(ctx)
	if err != nil {
		return state{}, err
	}
	email, password := string(seed[setup.Seed.AdminEmailKey]), string(seed[setup.Seed.AdminPasswordKey])
	if email == "" || password == "" {
		return state{}, fmt.Errorf("the first run needs %s and %s in the seed Secret %s",
			setup.Seed.AdminEmailKey, setup.Seed.AdminPasswordKey, setup.Seed.Secret)
	}
	if _, err := s.ReadFile(setup.ServiceAccountTokenFile); err != nil {
		return state{}, fmt.Errorf("read the service-account token: %w", err)
	}
	booted, err := s.API.Bootstrap(ctx, email, password, setup.Organization)
	if errors.Is(err, infisical.ErrAlreadyBootstrapped) {
		return state{}, fmt.Errorf("infisical is set up but the state ConfigMap %s is missing; restore it or reinstall Infisical: %w",
			setup.StateConfigMap, err)
	}
	if err != nil {
		return state{}, fmt.Errorf("bootstrap infisical: %w", err)
	}
	admin := s.API.WithToken(booted.AdminToken)
	created := state{organizationID: booted.OrganizationID}
	err = s.retry(ctx, "create the bootstrap identity", func() error {
		id, stepErr := s.identity(ctx, admin, booted.OrganizationID, setup.BootstrapIdentity.Name, organizationAdmin)
		created.identityID = id
		return stepErr
	})
	if err == nil {
		err = s.retry(ctx, "attach kubernetes auth", func() error {
			return s.setKubernetesAuth(ctx, admin, created.identityID, setup.BootstrapIdentity)
		})
	}
	if err == nil {
		// The scoped login must work before the admin token goes.
		err = s.retry(ctx, "log in as the bootstrap identity", func() error {
			_, err := s.login(ctx, created.identityID)
			return err
		})
	}
	if err == nil {
		err = s.retry(ctx, "save the state", func() error { return s.saveState(ctx, created) })
	}
	if err != nil {
		return state{}, fmt.Errorf("infisical was bootstrapped, but %w; its admin token is gone, so finish as the admin user in "+
			"Infisical's UI or reinstall Infisical with an empty database", err)
	}
	// Only an instance admin may delete the instance admin identity: its own token does it.
	err = s.retry(ctx, "revoke the instance admin identity", func() error { return admin.DeleteIdentity(ctx, booted.AdminIdentityID) })
	if err != nil {
		created.pendingRevocation = booted.AdminIdentityID
		return state{}, errors.Join(err, s.saveState(ctx, created))
	}
	s.Logger.Info("bootstrapped infisical", "organizationId", booted.OrganizationID, "identityId", created.identityID)
	return created, nil
}

// retry runs step until it succeeds or the attempts are used up.
func (s *Setup) retry(ctx context.Context, name string, step func() error) error {
	attempts := max(s.Attempts, 1)
	for attempt := 1; ; attempt++ {
		err := step()
		if err == nil {
			return nil
		}
		if attempt == attempts {
			return fmt.Errorf("%s failed %d times: %w", name, attempts, err)
		}
		s.Logger.Warn("bootstrap step failed, retrying", "step", name, "attempt", attempt, "error", err)
		select {
		case <-ctx.Done():
			return errors.Join(fmt.Errorf("%s: %w", name, err), ctx.Err())
		case <-time.After(s.Pause):
		}
	}
}

// login logs in with Kubernetes auth, or with the break-glass credential when that fails and one is stored: after a
// restore onto a new cluster, the identities still hold the lost cluster's CA (D-0173).
func (s *Setup) login(ctx context.Context, identityID string) (*infisical.Client, error) {
	token, err := s.ReadFile(s.Config.Infisical.ServiceAccountTokenFile)
	if err != nil {
		return nil, fmt.Errorf("read the service-account token: %w", err)
	}
	accessToken, err := s.API.KubernetesLogin(ctx, identityID, strings.TrimSpace(string(token)))
	if err == nil {
		return s.API.WithToken(accessToken), nil
	}
	kubernetesErr := fmt.Errorf("log in as the bootstrap identity: %w", err)
	credential, found, readErr := s.breakGlass(ctx)
	if readErr != nil || !found {
		return nil, errors.Join(kubernetesErr, readErr)
	}
	accessToken, err = s.API.UniversalAuthLogin(ctx, credential[breakGlassClientID], credential[breakGlassClientSecret])
	if err != nil {
		return nil, errors.Join(kubernetesErr, fmt.Errorf("log in with the break-glass credential: %w", err))
	}
	s.Logger.Warn("the Kubernetes login failed, so bootstrap logged in with the break-glass credential and re-points "+
		"every identity at this cluster", "error", kubernetesErr)
	return s.API.WithToken(accessToken), nil
}

// breakGlass returns the stored break-glass credential, if any.
func (s *Setup) breakGlass(ctx context.Context) (map[string]string, bool, error) {
	secret, err := s.Kube.CoreV1().Secrets(s.Config.Namespace).Get(ctx, s.Config.Infisical.BreakGlassSecret, metav1.GetOptions{})
	if apierrors.IsNotFound(err) {
		return nil, false, nil
	}
	if err != nil {
		return nil, false, fmt.Errorf("read the break-glass Secret: %w", err)
	}
	credential := map[string]string{}
	for _, key := range []string{breakGlassClientID, breakGlassClientSecret} {
		if len(secret.Data[key]) == 0 {
			return nil, false, nil
		}
		credential[key] = string(secret.Data[key])
	}
	return credential, true, nil
}

// ensureBreakGlass gives the bootstrap identity a Universal Auth client secret once and stores it next to the root
// Secrets (D-0173). A lost Secret gets a new client secret, and the identity's older ones are revoked.
func (s *Setup) ensureBreakGlass(ctx context.Context, api *infisical.Client, identityID string) error {
	if _, found, err := s.breakGlass(ctx); err != nil || found {
		return err
	}
	clientID, err := api.UniversalAuthClientID(ctx, identityID)
	if infisical.IsNotFound(err) {
		clientID, err = api.AttachUniversalAuth(ctx, identityID, breakGlassTokenTTL)
	}
	if err != nil {
		return fmt.Errorf("universal auth for the bootstrap identity: %w", err)
	}
	older, err := api.ClientSecretIDs(ctx, identityID)
	if err != nil {
		return fmt.Errorf("list the break-glass client secrets: %w", err)
	}
	created, err := api.CreateClientSecret(ctx, identityID, "OgCloud bootstrap break-glass login (D-0173)")
	if err != nil {
		return fmt.Errorf("create the break-glass client secret: %w", err)
	}
	secret := &corev1.Secret{
		ObjectMeta: metav1.ObjectMeta{
			Name:        s.Config.Infisical.BreakGlassSecret,
			Labels:      map[string]string{managedByLabel: managedBy},
			Annotations: map[string]string{keepAnnotation: keep},
		},
		StringData: map[string]string{breakGlassClientID: clientID, breakGlassClientSecret: created.Secret},
	}
	secrets := s.Kube.CoreV1().Secrets(s.Config.Namespace)
	_, err = secrets.Create(ctx, secret, metav1.CreateOptions{})
	if apierrors.IsAlreadyExists(err) {
		_, err = secrets.Update(ctx, secret, metav1.UpdateOptions{})
	}
	if err != nil {
		return fmt.Errorf("store the break-glass Secret %s: %w", secret.Name, err)
	}
	for _, id := range older {
		if err := api.RevokeClientSecret(ctx, identityID, id); err != nil {
			return fmt.Errorf("revoke an older break-glass client secret: %w", err)
		}
	}
	s.Logger.Info("stored the break-glass credential; keep it with the exported root key", "secret", secret.Name)
	return nil
}

func (s *Setup) ensureProject(ctx context.Context, api *infisical.Client, slug string) (infisical.Project, error) {
	project, err := api.ProjectBySlug(ctx, slug)
	if infisical.IsNotFound(err) {
		if err := api.CreateProject(ctx, slug, slug); err != nil {
			return infisical.Project{}, fmt.Errorf("create project %s: %w", slug, err)
		}
		project, err = api.ProjectBySlug(ctx, slug)
	}
	if err != nil {
		return infisical.Project{}, fmt.Errorf("project %s: %w", slug, err)
	}
	if environment := s.Config.Infisical.Environment; !slices.Contains(project.Environments, environment) {
		return infisical.Project{}, fmt.Errorf("project %s has no environment %s", slug, environment)
	}
	return project, nil
}

// ensureIdentity makes the identity a member of each project it has a role in; projects maps slugs to ids.
func (s *Setup) ensureIdentity(ctx context.Context, api *infisical.Client, organizationID string, projects map[string]string, spec IdentitySpec) (string, error) {
	id, err := s.identity(ctx, api, organizationID, spec.Name, organizationNoAccess)
	if err != nil {
		return "", err
	}
	if err := s.setKubernetesAuth(ctx, api, id, spec); err != nil {
		return "", err
	}
	for _, slug := range sortedKeys(spec.Roles) {
		if err := api.SetProjectMember(ctx, projects[slug], id, spec.Roles[slug]); err != nil {
			return "", fmt.Errorf("role in project %s: %w", slug, err)
		}
	}
	return id, nil
}

// publishIdentities writes the identities' ids into the configured Secret, replacing what it held.
func (s *Setup) publishIdentities(ctx context.Context, ids map[string]string) error {
	name := s.Config.Infisical.IdentitiesSecret
	if name == "" {
		return nil
	}
	secret := &corev1.Secret{
		ObjectMeta: metav1.ObjectMeta{Name: name, Labels: map[string]string{managedByLabel: managedBy}},
		StringData: ids,
	}
	secrets := s.Kube.CoreV1().Secrets(s.Config.Namespace)
	_, err := secrets.Create(ctx, secret, metav1.CreateOptions{})
	if apierrors.IsAlreadyExists(err) {
		_, err = secrets.Update(ctx, secret, metav1.UpdateOptions{})
	}
	if err != nil {
		return fmt.Errorf("publish the identity ids in Secret %s: %w", name, err)
	}
	return nil
}

// identity finds the identity by name, or creates it; names are not unique in Infisical, so the first wins.
func (s *Setup) identity(ctx context.Context, api *infisical.Client, organizationID, name, role string) (string, error) {
	identities, err := api.Identities(ctx, organizationID)
	if err != nil {
		return "", fmt.Errorf("list identities: %w", err)
	}
	for _, identity := range identities {
		if identity.Name == name {
			return identity.ID, nil
		}
	}
	id, err := api.CreateIdentity(ctx, organizationID, name, role)
	if err != nil {
		return "", fmt.Errorf("create identity %s: %w", name, err)
	}
	return id, nil
}

func (s *Setup) setKubernetesAuth(ctx context.Context, api *infisical.Client, identityID string, spec IdentitySpec) error {
	auth, err := s.kubernetesAuth(spec)
	if err != nil {
		return err
	}
	if err := api.SetKubernetesAuth(ctx, identityID, auth); err != nil {
		return fmt.Errorf("kubernetes auth for %s: %w", spec.Name, err)
	}
	return nil
}

// kubernetesAuth is what Infisical needs to review the identity's service-account tokens: the current cluster's CA
// (D-0173), and the reviewer's token only for tokens of another audience, which cannot review themselves (D-0167).
// The config gives every identity a single namespace (ValidateInfisical), so the two lists name exactly its accounts.
func (s *Setup) kubernetesAuth(spec IdentitySpec) (infisical.KubernetesAuth, error) {
	kube := s.Config.Infisical.Kubernetes
	ca, err := s.ReadFile(kube.CAFile)
	if err != nil {
		return infisical.KubernetesAuth{}, fmt.Errorf("read the API server's CA: %w", err)
	}
	auth := infisical.KubernetesAuth{Host: kube.Host, CACert: string(ca), Audience: spec.Audience}
	if spec.Audience != "" {
		reviewer, err := s.ReadFile(kube.ReviewerTokenFile)
		if err != nil {
			return infisical.KubernetesAuth{}, fmt.Errorf("read the reviewer token: %w", err)
		}
		auth.ReviewerJWT = strings.TrimSpace(string(reviewer))
	}
	for _, account := range spec.ServiceAccounts {
		namespace, name, _ := strings.Cut(account, "/")
		auth.Namespaces = appendUnique(auth.Namespaces, namespace)
		auth.Names = appendUnique(auth.Names, name)
	}
	return auth, nil
}

// ensureSecret generates the secret once; an existing value is never replaced.
func (s *Setup) ensureSecret(ctx context.Context, api *infisical.Client, projectID string, secret GeneratedSecret) error {
	ref := infisical.SecretRef{ProjectID: projectID, Environment: s.Config.Infisical.Environment, Path: secret.Path, Name: secret.Name}
	if _, found, err := api.Secret(ctx, ref); err != nil || found {
		return err
	}
	if err := api.EnsureFolder(ctx, projectID, ref.Environment, secret.Path); err != nil {
		return err
	}
	value, err := secret.Make(s.Random)
	if err != nil {
		return err
	}
	return api.PutSecret(ctx, ref, value, false)
}

// importSeed copies the seed into Infisical when its content changed since the last import, then deletes
// the Secret; the admin credentials it carried for the first run are never imported (D-0114).
func (s *Setup) importSeed(ctx context.Context, api *infisical.Client, projectID string) error {
	setup := s.Config.Infisical
	data, err := s.seed(ctx)
	if err != nil || data == nil {
		return err
	}
	delete(data, setup.Seed.AdminEmailKey)
	delete(data, setup.Seed.AdminPasswordKey)
	hash := seedHash(data)
	hashRef := infisical.SecretRef{ProjectID: projectID, Environment: setup.Environment, Path: setup.Seed.Path, Name: seedHashName}
	stored, hashFound, err := api.Secret(ctx, hashRef)
	if err != nil {
		return fmt.Errorf("read the seed hash: %w", err)
	}
	if stored != hash {
		if err := api.EnsureFolder(ctx, projectID, setup.Environment, setup.Seed.Path); err != nil {
			return err
		}
		for _, key := range sortedKeys(data) {
			ref := infisical.SecretRef{ProjectID: projectID, Environment: setup.Environment, Path: setup.Seed.Path, Name: key}
			_, found, err := api.Secret(ctx, ref)
			if err != nil {
				return fmt.Errorf("seed %s: %w", key, err)
			}
			if err := api.PutSecret(ctx, ref, string(data[key]), found); err != nil {
				return fmt.Errorf("seed %s: %w", key, err)
			}
		}
		if err := api.PutSecret(ctx, hashRef, hash, hashFound); err != nil {
			return fmt.Errorf("store the seed hash: %w", err)
		}
		s.Logger.Info("imported the seed", "keys", len(data))
	}
	err = s.Kube.CoreV1().Secrets(s.Config.Namespace).Delete(ctx, setup.Seed.Secret, metav1.DeleteOptions{})
	if err != nil && !apierrors.IsNotFound(err) {
		return fmt.Errorf("delete the seed Secret: %w", err)
	}
	return nil
}

// seed returns the seed Secret's data, or nil when there is none.
func (s *Setup) seed(ctx context.Context) (map[string][]byte, error) {
	secret, err := s.Kube.CoreV1().Secrets(s.Config.Namespace).Get(ctx, s.Config.Infisical.Seed.Secret, metav1.GetOptions{})
	if apierrors.IsNotFound(err) {
		return nil, nil
	}
	if err != nil {
		return nil, fmt.Errorf("read the seed Secret: %w", err)
	}
	return secret.Data, nil
}

func (s *Setup) loadState(ctx context.Context) (state, error) {
	configMap, err := s.Kube.CoreV1().ConfigMaps(s.Config.Namespace).Get(ctx, s.Config.Infisical.StateConfigMap, metav1.GetOptions{})
	if apierrors.IsNotFound(err) {
		return state{}, nil
	}
	if err != nil {
		return state{}, fmt.Errorf("read the state ConfigMap: %w", err)
	}
	return state{
		organizationID: configMap.Data[stateOrganization], identityID: configMap.Data[stateIdentity],
		pendingRevocation: configMap.Data[statePendingRevocation],
	}, nil
}

func (s *Setup) saveState(ctx context.Context, saved state) error {
	configMap := &corev1.ConfigMap{
		ObjectMeta: metav1.ObjectMeta{
			Name:        s.Config.Infisical.StateConfigMap,
			Labels:      map[string]string{managedByLabel: managedBy},
			Annotations: map[string]string{keepAnnotation: keep},
		},
		Data: map[string]string{stateOrganization: saved.organizationID, stateIdentity: saved.identityID},
	}
	if saved.pendingRevocation != "" {
		configMap.Data[statePendingRevocation] = saved.pendingRevocation
	}
	configMaps := s.Kube.CoreV1().ConfigMaps(s.Config.Namespace)
	_, err := configMaps.Create(ctx, configMap, metav1.CreateOptions{})
	if apierrors.IsAlreadyExists(err) {
		_, err = configMaps.Update(ctx, configMap, metav1.UpdateOptions{})
	}
	if err != nil {
		return fmt.Errorf("save the state ConfigMap: %w", err)
	}
	return nil
}

func seedHash(data map[string][]byte) string {
	digest := sha256.New()
	for _, key := range sortedKeys(data) {
		// A hash's Write never fails.
		_, _ = fmt.Fprintf(digest, "%s=%x\n", key, data[key])
	}
	return hex.EncodeToString(digest.Sum(nil))
}

func sortedKeys[V any](data map[string]V) []string {
	keys := make([]string, 0, len(data))
	for key := range data {
		keys = append(keys, key)
	}
	slices.Sort(keys)
	return keys
}

func appendUnique(values []string, value string) []string {
	if slices.Contains(values, value) {
		return values
	}
	return append(values, value)
}
