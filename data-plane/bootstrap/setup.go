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
	project, err := s.ensureProject(ctx, api)
	if err != nil {
		return err
	}
	for _, spec := range s.Config.Infisical.Identities {
		if err := s.ensureIdentity(ctx, api, current.organizationID, project.ID, spec); err != nil {
			return fmt.Errorf("identity %s: %w", spec.Name, err)
		}
	}
	for _, secret := range s.Config.Infisical.Secrets {
		if err := s.ensureSecret(ctx, api, project.ID, secret); err != nil {
			return fmt.Errorf("secret %s%s: %w", secret.Path, secret.Name, err)
		}
	}
	return s.importSeed(ctx, api, project.ID)
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
	if _, err := s.kubernetesAuth(setup.BootstrapIdentity); err != nil {
		return state{}, err
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

func (s *Setup) login(ctx context.Context, identityID string) (*infisical.Client, error) {
	token, err := s.ReadFile(s.Config.Infisical.ServiceAccountTokenFile)
	if err != nil {
		return nil, fmt.Errorf("read the service-account token: %w", err)
	}
	accessToken, err := s.API.KubernetesLogin(ctx, identityID, strings.TrimSpace(string(token)))
	if err != nil {
		return nil, fmt.Errorf("log in as the bootstrap identity: %w", err)
	}
	return s.API.WithToken(accessToken), nil
}

func (s *Setup) ensureProject(ctx context.Context, api *infisical.Client) (infisical.Project, error) {
	setup := s.Config.Infisical
	project, err := api.ProjectBySlug(ctx, setup.Project)
	if infisical.IsNotFound(err) {
		if err := api.CreateProject(ctx, setup.Project, setup.Project); err != nil {
			return infisical.Project{}, fmt.Errorf("create project %s: %w", setup.Project, err)
		}
		project, err = api.ProjectBySlug(ctx, setup.Project)
	}
	if err != nil {
		return infisical.Project{}, fmt.Errorf("project %s: %w", setup.Project, err)
	}
	if !slices.Contains(project.Environments, setup.Environment) {
		return infisical.Project{}, fmt.Errorf("project %s has no environment %s", setup.Project, setup.Environment)
	}
	return project, nil
}

func (s *Setup) ensureIdentity(ctx context.Context, api *infisical.Client, organizationID, projectID string, spec IdentitySpec) error {
	id, err := s.identity(ctx, api, organizationID, spec.Name, organizationNoAccess)
	if err != nil {
		return err
	}
	if err := s.setKubernetesAuth(ctx, api, id, spec); err != nil {
		return err
	}
	return api.SetProjectMember(ctx, projectID, id, spec.Role)
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

// kubernetesAuth reads what Infisical needs to review the identity's service-account tokens. The config
// gives every identity a single namespace (ValidateInfisical), so the two lists name exactly its accounts.
func (s *Setup) kubernetesAuth(spec IdentitySpec) (infisical.KubernetesAuth, error) {
	kube := s.Config.Infisical.Kubernetes
	reviewer, err := s.ReadFile(kube.ReviewerTokenFile)
	if err != nil {
		return infisical.KubernetesAuth{}, fmt.Errorf("read the reviewer token: %w", err)
	}
	var caCert []byte
	if kube.CAFile != "" {
		if caCert, err = s.ReadFile(kube.CAFile); err != nil {
			return infisical.KubernetesAuth{}, fmt.Errorf("read the Kubernetes CA: %w", err)
		}
	}
	auth := infisical.KubernetesAuth{Host: kube.Host, CACert: string(caCert), ReviewerJWT: strings.TrimSpace(string(reviewer))}
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

func sortedKeys(data map[string][]byte) []string {
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
