package kube

import (
	"context"
	"encoding/base64"
	"errors"
	"fmt"
	"testing"
	"time"

	authenticationv1 "k8s.io/api/authentication/v1"
	"k8s.io/apimachinery/pkg/runtime"
	"k8s.io/client-go/kubernetes/fake"
	k8stesting "k8s.io/client-go/testing"
)

var start = time.Date(2026, 11, 2, 12, 0, 0, 0, time.UTC)

// reviewing answers TokenReviews with status and counts them.
func reviewing(status authenticationv1.TokenReviewStatus, calls *int) *fake.Clientset {
	client := fake.NewClientset()
	client.PrependReactor("create", "tokenreviews", func(k8stesting.Action) (bool, runtime.Object, error) {
		*calls++
		return true, &authenticationv1.TokenReview{Status: status}, nil
	})
	return client
}

func podToken(expiry time.Time) string {
	payload := base64.RawURLEncoding.EncodeToString([]byte(fmt.Sprintf(`{"exp":%d}`, expiry.Unix())))
	return "header." + payload + ".signature"
}

func TestPodTokensAreReviewedOnceUntilTheyExpire(t *testing.T) {
	calls := 0
	now := start
	reviewer := NewReviewer(reviewing(authenticationv1.TokenReviewStatus{
		Authenticated: true, Audiences: []string{"ogcloud-gateway"},
		User: authenticationv1.UserInfo{
			Username: "system:serviceaccount:ogcloud-servers:game",
			Extra: map[string]authenticationv1.ExtraValue{
				podNameExtra: {"lobby-0192"}, podUIDExtra: {"uid-1"},
			},
		},
	}, &calls), "ogcloud-gateway", func() time.Time { return now })
	token := podToken(start.Add(time.Minute))

	identity, err := reviewer.Review(context.Background(), token)
	if err != nil {
		t.Fatalf("Review: %v", err)
	}
	want := Identity{Namespace: "ogcloud-servers", ServiceAccount: "game", PodName: "lobby-0192", PodUID: "uid-1"}
	if identity != want {
		t.Errorf("identity = %+v", identity)
	}
	if _, err := reviewer.Review(context.Background(), token); err != nil || calls != 1 {
		t.Errorf("cached review: calls %d, err %v", calls, err)
	}
	now = start.Add(time.Minute)
	if _, err := reviewer.Review(context.Background(), token); err != nil || calls != 2 {
		t.Errorf("after expiry: calls %d, err %v", calls, err)
	}
}

func TestVerdictsAreCachedForAtMostTwoMinutes(t *testing.T) {
	calls := 0
	now := start
	reviewer := NewReviewer(reviewing(authenticationv1.TokenReviewStatus{
		Authenticated: true, Audiences: []string{"ogcloud-gateway"},
		User: authenticationv1.UserInfo{Username: "system:serviceaccount:ogcloud:controller"},
	}, &calls), "ogcloud-gateway", func() time.Time { return now })
	token := podToken(start.Add(time.Hour))

	for _, at := range []time.Duration{0, time.Minute, 2 * time.Minute} {
		now = start.Add(at)
		if _, err := reviewer.Review(context.Background(), token); err != nil {
			t.Fatalf("Review: %v", err)
		}
	}
	if calls != 2 {
		t.Errorf("calls = %d, want 2", calls)
	}
}

func TestRefusedTokensAndNonServiceAccountsAreUnauthenticated(t *testing.T) {
	for name, status := range map[string]authenticationv1.TokenReviewStatus{
		"refused":              {Authenticated: false, Error: "invalid bearer token"},
		"user":                 {Authenticated: true, Audiences: []string{"ogcloud-gateway"}, User: authenticationv1.UserInfo{Username: "admin"}},
		"unconfirmed audience": {Authenticated: true, User: authenticationv1.UserInfo{Username: "system:serviceaccount:a:b"}},
		"other audience": {
			Authenticated: true, Audiences: []string{"kubernetes"}, User: authenticationv1.UserInfo{Username: "system:serviceaccount:a:b"},
		},
	} {
		t.Run(name, func(t *testing.T) {
			calls := 0
			reviewer := NewReviewer(reviewing(status, &calls), "ogcloud-gateway", func() time.Time { return start })
			for range 2 {
				if _, err := reviewer.Review(context.Background(), "token"); !errors.Is(err, ErrUnauthenticated) {
					t.Errorf("got %v", err)
				}
			}
			if calls != 2 {
				t.Errorf("a refusal was cached: calls %d", calls)
			}
		})
	}
}
