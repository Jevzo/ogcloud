// Package kube talks to the Kubernetes API for the data plane: it builds clients, checks the projected
// service-account tokens pods present with TokenReview, and sends a service's own token (D-0013).
package kube

import (
	"context"
	"crypto/sha256"
	"encoding/base64"
	"encoding/json"
	"errors"
	"fmt"
	"slices"
	"strings"
	"sync"
	"time"

	authenticationv1 "k8s.io/api/authentication/v1"
	metav1 "k8s.io/apimachinery/pkg/apis/meta/v1"
	"k8s.io/client-go/kubernetes"
	"k8s.io/client-go/rest"
	"k8s.io/client-go/tools/clientcmd"
)

const (
	podNameExtra     = "authentication.kubernetes.io/pod-name"
	podUIDExtra      = "authentication.kubernetes.io/pod-uid"
	serviceAccountID = "system:serviceaccount:"
	// A verdict is reused for at most this long, so a revoked token stops working soon after.
	maxCacheAge = 2 * time.Minute
)

// ErrUnauthenticated means the token is invalid, expired, or for another audience.
var ErrUnauthenticated = errors.New("token not authenticated")

// Identity is who a token belongs to; the pod fields are set for pod-bound tokens only.
type Identity struct {
	Namespace      string
	ServiceAccount string
	PodName        string
	PodUID         string
}

// NewClient connects with kubeconfig, or from inside the cluster when it is empty.
func NewClient(kubeconfig string) (kubernetes.Interface, error) {
	var config *rest.Config
	var err error
	if kubeconfig == "" {
		config, err = rest.InClusterConfig()
	} else {
		config, err = clientcmd.BuildConfigFromFlags("", kubeconfig)
	}
	if err != nil {
		return nil, fmt.Errorf("kubernetes config: %w", err)
	}
	client, err := kubernetes.NewForConfig(config)
	if err != nil {
		return nil, fmt.Errorf("kubernetes client: %w", err)
	}
	return client, nil
}

// Reviewer checks tokens for one audience and caches positive verdicts for at most two minutes and never past
// the token's expiry, keeping TokenReview off the hot path (D-0053).
type Reviewer struct {
	client   kubernetes.Interface
	audience string
	now      func() time.Time

	mu    sync.Mutex
	cache map[[sha256.Size]byte]verdict
}

type verdict struct {
	identity Identity
	until    time.Time
}

// NewReviewer checks tokens for audience; now is the clock.
func NewReviewer(client kubernetes.Interface, audience string, now func() time.Time) *Reviewer {
	return &Reviewer{client: client, audience: audience, now: now, cache: map[[sha256.Size]byte]verdict{}}
}

// Review returns the token's identity, or an error wrapping ErrUnauthenticated.
func (r *Reviewer) Review(ctx context.Context, token string) (Identity, error) {
	key := sha256.Sum256([]byte(token))
	now := r.now()
	r.mu.Lock()
	cached, ok := r.cache[key]
	r.mu.Unlock()
	if ok && now.Before(cached.until) {
		return cached.identity, nil
	}

	review, err := r.client.AuthenticationV1().TokenReviews().Create(ctx, &authenticationv1.TokenReview{
		Spec: authenticationv1.TokenReviewSpec{Token: token, Audiences: []string{r.audience}},
	}, metav1.CreateOptions{})
	if err != nil {
		return Identity{}, fmt.Errorf("token review: %w", err)
	}
	if !review.Status.Authenticated {
		return Identity{}, fmt.Errorf("%w: %s", ErrUnauthenticated, review.Status.Error)
	}
	// An audience-aware API server echoes the audiences it checked; anything else was not checked for ours.
	if !slices.Contains(review.Status.Audiences, r.audience) {
		return Identity{}, fmt.Errorf("%w: the review did not confirm audience %s", ErrUnauthenticated, r.audience)
	}
	identity, err := identityOf(review.Status.User)
	if err != nil {
		return Identity{}, err
	}
	r.remember(key, identity, now, expiryOf(token))
	return identity, nil
}

func (r *Reviewer) remember(key [sha256.Size]byte, identity Identity, now, expiry time.Time) {
	until := now.Add(maxCacheAge)
	if !expiry.IsZero() && expiry.Before(until) {
		until = expiry
	}
	r.mu.Lock()
	defer r.mu.Unlock()
	for cachedKey, cached := range r.cache {
		if !now.Before(cached.until) {
			delete(r.cache, cachedKey)
		}
	}
	r.cache[key] = verdict{identity: identity, until: until}
}

func identityOf(user authenticationv1.UserInfo) (Identity, error) {
	namespace, account, ok := strings.Cut(strings.TrimPrefix(user.Username, serviceAccountID), ":")
	if !strings.HasPrefix(user.Username, serviceAccountID) || !ok {
		return Identity{}, fmt.Errorf("%w: %s is not a service account", ErrUnauthenticated, user.Username)
	}
	identity := Identity{Namespace: namespace, ServiceAccount: account}
	if values := user.Extra[podNameExtra]; len(values) == 1 {
		identity.PodName = values[0]
	}
	if values := user.Extra[podUIDExtra]; len(values) == 1 {
		identity.PodUID = values[0]
	}
	return identity, nil
}

// expiryOf reads the exp claim of a JWT without verifying it; it only bounds how long a verdict that
// TokenReview gave is cached. Zero when the token has none.
func expiryOf(token string) time.Time {
	parts := strings.Split(token, ".")
	if len(parts) != 3 {
		return time.Time{}
	}
	payload, err := base64.RawURLEncoding.DecodeString(parts[1])
	if err != nil {
		return time.Time{}
	}
	var claims struct {
		Exp int64 `json:"exp"`
	}
	if json.Unmarshal(payload, &claims) != nil || claims.Exp == 0 {
		return time.Time{}
	}
	return time.Unix(claims.Exp, 0)
}
