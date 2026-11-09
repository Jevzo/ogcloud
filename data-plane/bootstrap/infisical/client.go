// Package infisical is a small typed client for the parts of Infisical's REST API that bootstrap needs
// (D-0112): instance bootstrap, identities with Kubernetes and Universal Auth, projects, folders and secrets.
package infisical

import (
	"bytes"
	"context"
	"crypto/tls"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"net/http"
	"net/url"
	"strings"
	"time"
)

const requestTimeout = 30 * time.Second

// APIError is a non-2xx answer.
type APIError struct {
	Status  int
	Message string
}

func (e *APIError) Error() string { return fmt.Sprintf("infisical: %d %s", e.Status, e.Message) }

// IsNotFound reports whether err is a 404 from Infisical.
func IsNotFound(err error) bool {
	var apiErr *APIError
	return errors.As(err, &apiErr) && apiErr.Status == http.StatusNotFound
}

// Client calls one Infisical instance, with an access token once logged in.
type Client struct {
	http  *http.Client
	base  string
	token string
}

// New talks to baseURL; tlsConfig may be nil for plain HTTP.
func New(baseURL string, tlsConfig *tls.Config) *Client {
	transport := http.DefaultTransport.(*http.Transport).Clone() //nolint:forcetypeassert // the default is a Transport.
	transport.TLSClientConfig = tlsConfig
	return &Client{http: &http.Client{Transport: transport, Timeout: requestTimeout}, base: strings.TrimRight(baseURL, "/")}
}

// WithToken returns a client that sends token.
func (c *Client) WithToken(token string) *Client {
	copied := *c
	copied.token = token
	return &copied
}

// Ready checks that the API answers.
func (c *Client) Ready(ctx context.Context) error {
	return c.do(ctx, http.MethodGet, "/api/status", nil, nil, nil)
}

// Bootstrapped is what the one-time instance bootstrap returns.
type Bootstrapped struct {
	OrganizationID string
	// The instance admin identity and its root-equivalent token (D-0112).
	AdminIdentityID string
	AdminToken      string
}

// ErrAlreadyBootstrapped means the instance was set up before.
var ErrAlreadyBootstrapped = errors.New("infisical instance already bootstrapped")

// Bootstrap creates the admin user, the organization and the instance admin identity.
func (c *Client) Bootstrap(ctx context.Context, email, password, organization string) (Bootstrapped, error) {
	var response struct {
		Organization struct {
			ID string `json:"id"`
		} `json:"organization"`
		Identity struct {
			ID          string `json:"id"`
			Credentials struct {
				Token string `json:"token"`
			} `json:"credentials"`
		} `json:"identity"`
	}
	err := c.do(ctx, http.MethodPost, "/api/v1/admin/bootstrap", nil,
		map[string]string{"email": email, "password": password, "organization": organization}, &response)
	var apiErr *APIError
	if errors.As(err, &apiErr) && apiErr.Status == http.StatusBadRequest && strings.Contains(apiErr.Message, "already been set up") {
		return Bootstrapped{}, ErrAlreadyBootstrapped
	}
	if err != nil {
		return Bootstrapped{}, err
	}
	booted := Bootstrapped{
		OrganizationID:  response.Organization.ID,
		AdminIdentityID: response.Identity.ID,
		AdminToken:      response.Identity.Credentials.Token,
	}
	if booted.OrganizationID == "" || booted.AdminIdentityID == "" || booted.AdminToken == "" {
		return Bootstrapped{}, errors.New("infisical bootstrap answered without an organization, identity or token")
	}
	return booted, nil
}

// Identity is a machine identity of the organization.
type Identity struct {
	ID   string
	Name string
}

// Identities lists the organization's identities.
func (c *Client) Identities(ctx context.Context, organizationID string) ([]Identity, error) {
	var response struct {
		Identities []struct {
			Identity struct {
				ID   string `json:"id"`
				Name string `json:"name"`
			} `json:"identity"`
		} `json:"identities"`
	}
	if err := c.do(ctx, http.MethodGet, "/api/v1/identities", url.Values{"orgId": {organizationID}}, nil, &response); err != nil {
		return nil, err
	}
	identities := make([]Identity, len(response.Identities))
	for i, entry := range response.Identities {
		identities[i] = Identity{ID: entry.Identity.ID, Name: entry.Identity.Name}
	}
	return identities, nil
}

// CreateIdentity creates an identity with an organization role, e.g. admin or no-access.
func (c *Client) CreateIdentity(ctx context.Context, organizationID, name, role string) (string, error) {
	var response struct {
		Identity struct {
			ID string `json:"id"`
		} `json:"identity"`
	}
	body := map[string]string{"name": name, "organizationId": organizationID, "role": role}
	if err := c.do(ctx, http.MethodPost, "/api/v1/identities", nil, body, &response); err != nil {
		return "", err
	}
	if response.Identity.ID == "" {
		return "", fmt.Errorf("infisical created identity %s without an id", name)
	}
	return response.Identity.ID, nil
}

// DeleteIdentity deletes an identity and with it every token it holds.
func (c *Client) DeleteIdentity(ctx context.Context, identityID string) error {
	return c.do(ctx, http.MethodDelete, "/api/v1/identities/"+url.PathEscape(identityID), nil, nil, nil)
}

// KubernetesAuth lets service accounts log in as an identity, reviewed by Infisical with TokenReview against the API
// server, whose TLS it verifies with CACert (D-0173). Each login's own token, bound to system:auth-delegator, is its
// reviewer unless ReviewerJWT names another (D-0167).
type KubernetesAuth struct {
	Host   string
	CACert string
	// Only for logins with a token of another audience, which cannot review itself.
	ReviewerJWT string
	Namespaces  []string
	Names       []string
	// Empty to accept the API server's own audience.
	Audience string
}

// SetKubernetesAuth attaches Kubernetes auth to the identity, or updates it when attached already.
func (c *Client) SetKubernetesAuth(ctx context.Context, identityID string, auth KubernetesAuth) error {
	path := "/api/v1/auth/kubernetes-auth/identities/" + url.PathEscape(identityID)
	body := map[string]any{
		"kubernetesHost":    auth.Host,
		"caCert":            auth.CACert,
		"allowedNamespaces": strings.Join(auth.Namespaces, ","),
		"allowedNames":      strings.Join(auth.Names, ","),
		"allowedAudience":   auth.Audience,
		// Without it Infisical skips verification whenever no CA is given (D-0173).
		"verifyTlsCertificate": true,
	}
	if auth.ReviewerJWT != "" {
		body["tokenReviewerJwt"] = auth.ReviewerJWT
	}
	err := c.do(ctx, http.MethodGet, path, nil, nil, nil)
	if IsNotFound(err) {
		return c.do(ctx, http.MethodPost, path, nil, body, nil)
	}
	if err != nil {
		return err
	}
	// Infisical clears a stored reviewer token only on null.
	if auth.ReviewerJWT == "" {
		body["tokenReviewerJwt"] = nil
	}
	return c.do(ctx, http.MethodPatch, path, nil, body, nil)
}

// KubernetesLogin exchanges a service-account token for an access token of the identity.
func (c *Client) KubernetesLogin(ctx context.Context, identityID, serviceAccountToken string) (string, error) {
	var response struct {
		AccessToken string `json:"accessToken"`
	}
	body := map[string]string{"identityId": identityID, "jwt": serviceAccountToken}
	if err := c.do(ctx, http.MethodPost, "/api/v1/auth/kubernetes-auth/login", nil, body, &response); err != nil {
		return "", err
	}
	if response.AccessToken == "" {
		return "", errors.New("infisical's kubernetes login answered without an access token")
	}
	return response.AccessToken, nil
}

// ClientSecret is a Universal Auth client secret; Secret is shown only when it is created.
type ClientSecret struct {
	ID     string
	Secret string
}

// UniversalAuthClientID returns the identity's Universal Auth client id, or an error IsNotFound recognises when the
// identity has none.
func (c *Client) UniversalAuthClientID(ctx context.Context, identityID string) (string, error) {
	var response universalAuthResponse
	if err := c.do(ctx, http.MethodGet, universalAuthPath(identityID), nil, nil, &response); err != nil {
		return "", err
	}
	return response.UniversalAuth.ClientID, nil
}

// AttachUniversalAuth gives the identity Universal Auth with access tokens of tokenTTL and returns its client id.
func (c *Client) AttachUniversalAuth(ctx context.Context, identityID string, tokenTTL time.Duration) (string, error) {
	seconds := int(tokenTTL.Seconds())
	body := map[string]any{"accessTokenTTL": seconds, "accessTokenMaxTTL": seconds}
	var response universalAuthResponse
	if err := c.do(ctx, http.MethodPost, universalAuthPath(identityID), nil, body, &response); err != nil {
		return "", err
	}
	return response.UniversalAuth.ClientID, nil
}

// ClientSecretIDs lists the ids of the identity's client secrets that are not revoked.
func (c *Client) ClientSecretIDs(ctx context.Context, identityID string) ([]string, error) {
	var response struct {
		ClientSecrets []struct {
			ID      string `json:"id"`
			Revoked bool   `json:"isClientSecretRevoked"`
		} `json:"clientSecretData"`
	}
	if err := c.do(ctx, http.MethodGet, universalAuthPath(identityID)+"/client-secrets", nil, nil, &response); err != nil {
		return nil, err
	}
	ids := make([]string, 0, len(response.ClientSecrets))
	for _, secret := range response.ClientSecrets {
		if !secret.Revoked {
			ids = append(ids, secret.ID)
		}
	}
	return ids, nil
}

// CreateClientSecret creates a client secret that never expires and has no use limit.
func (c *Client) CreateClientSecret(ctx context.Context, identityID, description string) (ClientSecret, error) {
	var response struct {
		ClientSecret string `json:"clientSecret"`
		Data         struct {
			ID string `json:"id"`
		} `json:"clientSecretData"`
	}
	body := map[string]any{"description": description, "numUsesLimit": 0, "ttl": 0}
	if err := c.do(ctx, http.MethodPost, universalAuthPath(identityID)+"/client-secrets", nil, body, &response); err != nil {
		return ClientSecret{}, err
	}
	return ClientSecret{ID: response.Data.ID, Secret: response.ClientSecret}, nil
}

// RevokeClientSecret revokes one of the identity's client secrets.
func (c *Client) RevokeClientSecret(ctx context.Context, identityID, secretID string) error {
	path := universalAuthPath(identityID) + "/client-secrets/" + url.PathEscape(secretID) + "/revoke"
	return c.do(ctx, http.MethodPost, path, nil, nil, nil)
}

// UniversalAuthLogin exchanges a client id and secret for an access token of their identity.
func (c *Client) UniversalAuthLogin(ctx context.Context, clientID, clientSecret string) (string, error) {
	var response struct {
		AccessToken string `json:"accessToken"`
	}
	body := map[string]string{"clientId": clientID, "clientSecret": clientSecret}
	if err := c.do(ctx, http.MethodPost, "/api/v1/auth/universal-auth/login", nil, body, &response); err != nil {
		return "", err
	}
	if response.AccessToken == "" {
		return "", errors.New("infisical's universal auth login answered without an access token")
	}
	return response.AccessToken, nil
}

type universalAuthResponse struct {
	UniversalAuth struct {
		ClientID string `json:"clientId"`
	} `json:"identityUniversalAuth"`
}

func universalAuthPath(identityID string) string {
	return "/api/v1/auth/universal-auth/identities/" + url.PathEscape(identityID)
}

// Project is a secret-manager project with its environment slugs.
type Project struct {
	ID           string
	Environments []string
}

// ProjectBySlug returns the project, or an error IsNotFound recognises.
func (c *Client) ProjectBySlug(ctx context.Context, slug string) (Project, error) {
	var response struct {
		ID           string `json:"id"`
		Environments []struct {
			Slug string `json:"slug"`
		} `json:"environments"`
	}
	if err := c.do(ctx, http.MethodGet, "/api/v1/projects/slug/"+url.PathEscape(slug), nil, nil, &response); err != nil {
		return Project{}, err
	}
	project := Project{ID: response.ID}
	for _, environment := range response.Environments {
		project.Environments = append(project.Environments, environment.Slug)
	}
	return project, nil
}

// CreateProject creates a secret-manager project with the default environments.
func (c *Client) CreateProject(ctx context.Context, name, slug string) error {
	body := map[string]string{"projectName": name, "slug": slug, "type": "secret-manager"}
	return c.do(ctx, http.MethodPost, "/api/v1/projects", nil, body, nil)
}

// ProjectMemberRole returns the identity's role in the project, or an error IsNotFound recognises.
func (c *Client) ProjectMemberRole(ctx context.Context, projectID, identityID string) (string, error) {
	var response struct {
		IdentityMembership struct {
			Roles []struct {
				Role           string `json:"role"`
				CustomRoleSlug string `json:"customRoleSlug"`
			} `json:"roles"`
		} `json:"identityMembership"`
	}
	if err := c.do(ctx, http.MethodGet, memberPath(projectID, identityID), nil, nil, &response); err != nil {
		return "", err
	}
	roles := response.IdentityMembership.Roles
	if len(roles) != 1 {
		return "", fmt.Errorf("identity %s holds %d roles in project %s", identityID, len(roles), projectID)
	}
	if roles[0].CustomRoleSlug != "" {
		return roles[0].CustomRoleSlug, nil
	}
	return roles[0].Role, nil
}

// SetProjectMember makes the identity a project member with exactly role, adding or changing it as needed.
func (c *Client) SetProjectMember(ctx context.Context, projectID, identityID, role string) error {
	current, err := c.ProjectMemberRole(ctx, projectID, identityID)
	switch {
	case IsNotFound(err):
		return c.do(ctx, http.MethodPost, memberPath(projectID, identityID), nil, map[string]string{"role": role}, nil)
	case err != nil:
		return err
	case current == role:
		return nil
	}
	body := map[string]any{"roles": []map[string]any{{"role": role, "isTemporary": false}}}
	return c.do(ctx, http.MethodPatch, memberPath(projectID, identityID), nil, body, nil)
}

func memberPath(projectID, identityID string) string {
	return "/api/v1/projects/" + url.PathEscape(projectID) + "/memberships/identities/" + url.PathEscape(identityID)
}

// EnsureFolder creates every missing folder of path, e.g. /api/signing.
func (c *Client) EnsureFolder(ctx context.Context, projectID, environment, path string) error {
	parent := "/"
	for name := range strings.SplitSeq(strings.Trim(path, "/"), "/") {
		if name == "" {
			continue
		}
		var response struct {
			Folders []struct {
				Name string `json:"name"`
			} `json:"folders"`
		}
		query := url.Values{"projectId": {projectID}, "environment": {environment}, "path": {parent}}
		if err := c.do(ctx, http.MethodGet, "/api/v2/folders", query, nil, &response); err != nil {
			return err
		}
		exists := false
		for _, folder := range response.Folders {
			exists = exists || folder.Name == name
		}
		if !exists {
			body := map[string]string{"projectId": projectID, "environment": environment, "name": name, "path": parent}
			if err := c.do(ctx, http.MethodPost, "/api/v2/folders", nil, body, nil); err != nil {
				return err
			}
		}
		parent = strings.TrimRight(parent, "/") + "/" + name
	}
	return nil
}

// SecretRef names a secret in a project environment.
type SecretRef struct {
	ProjectID   string
	Environment string
	Path        string
	Name        string
}

// Secret returns the value; found is false when there is none.
func (c *Client) Secret(ctx context.Context, ref SecretRef) (value string, found bool, err error) {
	var response struct {
		Secret struct {
			Value string `json:"secretValue"`
		} `json:"secret"`
	}
	query := url.Values{
		"projectId": {ref.ProjectID}, "environment": {ref.Environment}, "secretPath": {ref.Path}, "viewSecretValue": {"true"},
	}
	err = c.do(ctx, http.MethodGet, "/api/v4/secrets/"+url.PathEscape(ref.Name), query, nil, &response)
	if IsNotFound(err) {
		return "", false, nil
	}
	if err != nil {
		return "", false, err
	}
	return response.Secret.Value, true, nil
}

// PutSecret creates the secret, or replaces its value when exists is true.
func (c *Client) PutSecret(ctx context.Context, ref SecretRef, value string, exists bool) error {
	method := http.MethodPost
	if exists {
		method = http.MethodPatch
	}
	body := map[string]string{"projectId": ref.ProjectID, "environment": ref.Environment, "secretPath": ref.Path, "secretValue": value}
	return c.do(ctx, method, "/api/v4/secrets/"+url.PathEscape(ref.Name), nil, body, nil)
}

func (c *Client) do(ctx context.Context, method, path string, query url.Values, body, out any) error {
	target := c.base + path
	if len(query) > 0 {
		target += "?" + query.Encode()
	}
	var reader io.Reader
	if body != nil {
		encoded, err := json.Marshal(body)
		if err != nil {
			return fmt.Errorf("encode %s %s: %w", method, path, err)
		}
		reader = bytes.NewReader(encoded)
	}
	request, err := http.NewRequestWithContext(ctx, method, target, reader)
	if err != nil {
		return fmt.Errorf("request %s %s: %w", method, path, err)
	}
	if body != nil {
		request.Header.Set("Content-Type", "application/json")
	}
	if c.token != "" {
		request.Header.Set("Authorization", "Bearer "+c.token)
	}
	response, err := c.http.Do(request)
	if err != nil {
		return fmt.Errorf("%s %s: %w", method, path, err)
	}
	defer func() { _ = response.Body.Close() }()
	payload, err := io.ReadAll(response.Body)
	if err != nil {
		return fmt.Errorf("read %s %s: %w", method, path, err)
	}
	if response.StatusCode >= http.StatusMultipleChoices {
		var problem struct {
			Message string `json:"message"`
		}
		if json.Unmarshal(payload, &problem) != nil || problem.Message == "" {
			problem.Message = response.Status
		}
		return fmt.Errorf("%s %s: %w", method, path, &APIError{Status: response.StatusCode, Message: problem.Message})
	}
	if out == nil {
		return nil
	}
	if err := json.Unmarshal(payload, out); err != nil {
		return fmt.Errorf("decode %s %s: %w", method, path, err)
	}
	return nil
}
