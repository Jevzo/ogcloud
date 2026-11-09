// Package bootstrap creates the secrets root at install time and sets up Infisical (D-0044). It is a generic
// engine: the chart supplies a config naming every Secret, identity and generated value (D-0114).
package bootstrap

import (
	"errors"
	"fmt"
	"os"
	"slices"
	"strings"

	corev1 "k8s.io/api/core/v1"

	"github.com/jevzo/ogcloud/data-plane/pkg/strictyaml"
)

// Config is the chart-supplied description of what to bootstrap.
type Config struct {
	// Namespace of the root Secrets, the state ConfigMap and the seed Secret.
	Namespace   string         `yaml:"namespace"`
	RootSecrets []RootSecret   `yaml:"rootSecrets"`
	Infisical   InfisicalSetup `yaml:"infisical"`
}

// RootSecret is a Kubernetes Secret created before Infisical exists, e.g. its own encryption key.
type RootSecret struct {
	Name string `yaml:"name"`
	// Optional Secret type, e.g. kubernetes.io/basic-auth for CloudNativePG; Opaque when empty.
	Type corev1.SecretType    `yaml:"type"`
	Keys map[string]Generator `yaml:"keys"`
}

// Generator makes a value: a fixed Value, or random data of the given kind.
type Generator struct {
	// hex, base64, base64url, alnum or rsa; empty with Value.
	Generate string `yaml:"generate"`
	Bytes    int    `yaml:"bytes"`
	Length   int    `yaml:"length"`
	Bits     int    `yaml:"bits"`
	Value    string `yaml:"value"`
}

// InfisicalSetup is the projects, their identities and the secrets to generate in them.
type InfisicalSetup struct {
	URL string `yaml:"url"`
	// Optional CA for Infisical's internal TLS (D-0041).
	CAFile       string `yaml:"caFile"`
	Organization string `yaml:"organization"`
	// The default project, by slug: the seed lands in it, and so does every secret that names no other.
	Project string `yaml:"project"`
	// Further projects, e.g. one that holds only the forwarding tokens (D-0152).
	Projects    []string `yaml:"projects"`
	Environment string   `yaml:"environment"`
	// Holds non-secret ids between runs, such as the bootstrap identity's.
	StateConfigMap string `yaml:"stateConfigMap"`
	// Optional Secret that receives each identity's id under its name, for the Secrets Operator's
	// InfisicalAuth resources, which read identity ids only from Secrets.
	IdentitiesSecret string `yaml:"identitiesSecret"`
	// The bootstrap Job's own projected token, for Kubernetes auth.
	ServiceAccountTokenFile string            `yaml:"serviceAccountTokenFile"`
	Kubernetes              KubernetesAuth    `yaml:"kubernetes"`
	BootstrapIdentity       IdentitySpec      `yaml:"bootstrapIdentity"`
	Identities              []IdentitySpec    `yaml:"identities"`
	Secrets                 []GeneratedSecret `yaml:"secrets"`
	Seed                    Seed              `yaml:"seed"`
}

// KubernetesAuth is how Infisical reviews service-account tokens.
type KubernetesAuth struct {
	Host   string `yaml:"host"`
	CAFile string `yaml:"caFile"`
	// A long-lived token of a reviewer service account bound to system:auth-delegator.
	ReviewerTokenFile string `yaml:"reviewerTokenFile"`
}

// IdentitySpec is a machine identity that logs in with Kubernetes service accounts.
type IdentitySpec struct {
	Name string `yaml:"name"`
	// namespace/name of each service account allowed to log in.
	ServiceAccounts []string `yaml:"serviceAccounts"`
	// Role by project slug, e.g. viewer; the bootstrap identity is an organization admin instead.
	Roles map[string]string `yaml:"roles"`
	// Optional token audience Infisical requires, so the token it sees is good for nothing else.
	Audience string `yaml:"audience"`
}

// GeneratedSecret is created once in Infisical and never overwritten.
type GeneratedSecret struct {
	// Project slug; empty for the default project.
	Project   string `yaml:"project"`
	Path      string `yaml:"path"`
	Name      string `yaml:"name"`
	Generator `yaml:",inline"`
}

// ProjectSlugs lists the default project and the further ones, each once.
func (s InfisicalSetup) ProjectSlugs() []string {
	slugs := []string{s.Project}
	for _, slug := range s.Projects {
		slugs = appendUnique(slugs, slug)
	}
	return slugs
}

// project is the secret's project slug.
func (s GeneratedSecret) project(setup InfisicalSetup) string {
	if s.Project == "" {
		return setup.Project
	}
	return s.Project
}

// Seed is the operator's Secret, imported when its content changed and then deleted.
type Seed struct {
	Secret string `yaml:"secret"`
	// Folder the seed's keys land in.
	Path             string `yaml:"path"`
	AdminEmailKey    string `yaml:"adminEmailKey"`
	AdminPasswordKey string `yaml:"adminPasswordKey"`
}

// LoadConfig reads and validates the config file.
func LoadConfig(path string) (*Config, error) {
	data, err := os.ReadFile(path) //nolint:gosec // G304: the path is the Job's own mounted config.
	if err != nil {
		return nil, fmt.Errorf("read config %s: %w", path, err)
	}
	var config Config
	if err := strictyaml.Decode(data, &config); err != nil {
		return nil, fmt.Errorf("config %s: %w", path, err)
	}
	if err := config.Validate(); err != nil {
		return nil, fmt.Errorf("config %s: %w", path, err)
	}
	return &config, nil
}

// Validate checks every generator and required field, so a bad config fails before anything is written.
func (c *Config) Validate() error {
	var problems []error
	if c.Namespace == "" {
		problems = append(problems, errors.New("namespace is required"))
	}
	for _, secret := range c.RootSecrets {
		if secret.Name == "" || len(secret.Keys) == 0 {
			problems = append(problems, fmt.Errorf("root secret %q needs a name and keys", secret.Name))
		}
		for key, generator := range secret.Keys {
			if err := generator.Validate(); err != nil {
				problems = append(problems, fmt.Errorf("root secret %s key %s: %w", secret.Name, key, err))
			}
		}
	}
	for _, secret := range c.Infisical.Secrets {
		if !strings.HasPrefix(secret.Path, "/") || secret.Name == "" {
			problems = append(problems, fmt.Errorf("secret %q at %q: needs a name and an absolute path", secret.Name, secret.Path))
		}
		if err := secret.Validate(); err != nil {
			problems = append(problems, fmt.Errorf("secret %s%s: %w", secret.Path, secret.Name, err))
		}
	}
	return errors.Join(problems...)
}

// ValidateInfisical checks what the infisical stage needs on top of Validate. Its first run spends
// Infisical's one-time admin token, so everything must be right before it starts (D-0112).
func (c *Config) ValidateInfisical() error {
	setup := c.Infisical
	var problems []error
	for field, value := range map[string]string{
		"infisical.url": setup.URL, "infisical.organization": setup.Organization, "infisical.project": setup.Project,
		"infisical.environment": setup.Environment, "infisical.stateConfigMap": setup.StateConfigMap,
		"infisical.serviceAccountTokenFile": setup.ServiceAccountTokenFile, "infisical.kubernetes.host": setup.Kubernetes.Host,
		"infisical.kubernetes.reviewerTokenFile": setup.Kubernetes.ReviewerTokenFile, "infisical.seed.secret": setup.Seed.Secret,
		"infisical.seed.adminEmailKey": setup.Seed.AdminEmailKey, "infisical.seed.adminPasswordKey": setup.Seed.AdminPasswordKey,
	} {
		if value == "" {
			problems = append(problems, fmt.Errorf("%s is required", field))
		}
	}
	if !strings.HasPrefix(setup.Seed.Path, "/") {
		problems = append(problems, errors.New("infisical.seed.path must be absolute"))
	}
	projects := setup.ProjectSlugs()
	for _, secret := range setup.Secrets {
		if !slices.Contains(projects, secret.project(setup)) {
			problems = append(problems, fmt.Errorf("secret %s%s: project %s is not configured", secret.Path, secret.Name, secret.Project))
		}
	}
	names := map[string]bool{}
	for i, identity := range append([]IdentitySpec{setup.BootstrapIdentity}, setup.Identities...) {
		bootstrapIdentity := i == 0
		if err := identity.validate(bootstrapIdentity, projects); err != nil {
			problems = append(problems, err)
		}
		// Identities are found by name, so a clash would point one at another's service accounts.
		if names[identity.Name] {
			problems = append(problems, fmt.Errorf("identity name %q is used twice", identity.Name))
		}
		names[identity.Name] = true
	}
	return errors.Join(problems...)
}

// validate checks an identity. Infisical allows every listed namespace with every listed name, so one
// identity may only name service accounts of a single namespace.
func (spec IdentitySpec) validate(bootstrapIdentity bool, projects []string) error {
	var problems []error
	if spec.Name == "" {
		problems = append(problems, errors.New("every identity needs a name"))
	}
	if !bootstrapIdentity && len(spec.Roles) == 0 {
		problems = append(problems, fmt.Errorf("identity %s needs a project role", spec.Name))
	}
	for project, role := range spec.Roles {
		if !slices.Contains(projects, project) || role == "" {
			problems = append(problems, fmt.Errorf("identity %s: role %q in project %q, which is not configured", spec.Name, role, project))
		}
	}
	if len(spec.ServiceAccounts) == 0 {
		problems = append(problems, fmt.Errorf("identity %s needs service accounts; an empty list would admit any", spec.Name))
	}
	namespaces := map[string]bool{}
	for _, account := range spec.ServiceAccounts {
		namespace, name, ok := strings.Cut(account, "/")
		if !ok || namespace == "" || name == "" {
			problems = append(problems, fmt.Errorf("identity %s: service account %q is not namespace/name", spec.Name, account))
			continue
		}
		namespaces[namespace] = true
	}
	if len(namespaces) > 1 {
		problems = append(problems, fmt.Errorf("identity %s: service accounts must share one namespace", spec.Name))
	}
	return errors.Join(problems...)
}

// Validate checks that exactly one of a fixed value or a known generator is set, with a usable size.
func (g Generator) Validate() error {
	switch g.Generate {
	case "":
		if g.Value == "" {
			return errors.New("needs a value or a generator")
		}
	case "hex", "base64", "base64url":
		if g.Bytes < minBytes {
			return fmt.Errorf("%s needs at least %d bytes", g.Generate, minBytes)
		}
	case "alnum":
		if g.Length < minBytes {
			return fmt.Errorf("alnum needs a length of at least %d", minBytes)
		}
	case "rsa":
		if g.Bits < minRSABits {
			return fmt.Errorf("rsa needs at least %d bits", minRSABits)
		}
	default:
		return fmt.Errorf("unknown generator %q", g.Generate)
	}
	if g.Generate != "" && g.Value != "" {
		return errors.New("has both a value and a generator")
	}
	return nil
}
