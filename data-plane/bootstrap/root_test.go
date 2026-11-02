package bootstrap

import (
	"context"
	"crypto/rand"
	"encoding/hex"
	"testing"

	corev1 "k8s.io/api/core/v1"
	metav1 "k8s.io/apimachinery/pkg/apis/meta/v1"
	"k8s.io/client-go/kubernetes/fake"
)

func rootConfig() *Config {
	return &Config{
		Namespace: "ogcloud",
		RootSecrets: []RootSecret{{
			Name: "ogcloud-infisical-root",
			Keys: map[string]Generator{
				"ENCRYPTION_KEY": {Generate: "hex", Bytes: 16},
				"AUTH_SECRET":    {Generate: "base64", Bytes: 32},
				"DB_USER":        {Value: "infisical"},
			},
		}},
	}
}

func TestRootSecretsAreCreatedOnceAndKeptOnUninstall(t *testing.T) {
	ctx := context.Background()
	client := fake.NewClientset()

	if err := EnsureRootSecrets(ctx, client, rootConfig(), rand.Reader); err != nil {
		t.Fatalf("EnsureRootSecrets: %v", err)
	}
	first := secret(t, client)
	if err := EnsureRootSecrets(ctx, client, rootConfig(), rand.Reader); err != nil {
		t.Fatalf("rerun: %v", err)
	}
	second := secret(t, client)

	if key, err := hex.DecodeString(string(first.Data["ENCRYPTION_KEY"])); err != nil || len(key) != 16 {
		t.Errorf("ENCRYPTION_KEY = %q", first.Data["ENCRYPTION_KEY"])
	}
	if string(first.Data["DB_USER"]) != "infisical" {
		t.Errorf("DB_USER = %q", first.Data["DB_USER"])
	}
	for key := range first.Data {
		if string(first.Data[key]) != string(second.Data[key]) {
			t.Errorf("rerun changed %s", key)
		}
	}
	if first.Annotations[keepAnnotation] != keep {
		t.Error("the Secret is not kept on uninstall")
	}
}

func TestMissingKeysAreAddedWithoutTouchingOthers(t *testing.T) {
	client := fake.NewClientset(&corev1.Secret{
		ObjectMeta: metav1.ObjectMeta{Name: "ogcloud-infisical-root", Namespace: "ogcloud"},
		Data:       map[string][]byte{"ENCRYPTION_KEY": []byte("chosen-by-operator")},
	})

	if err := EnsureRootSecrets(context.Background(), client, rootConfig(), rand.Reader); err != nil {
		t.Fatalf("EnsureRootSecrets: %v", err)
	}

	data := secret(t, client).Data
	if string(data["ENCRYPTION_KEY"]) != "chosen-by-operator" {
		t.Errorf("an existing value was overwritten: %q", data["ENCRYPTION_KEY"])
	}
	if len(data["AUTH_SECRET"]) == 0 || string(data["DB_USER"]) != "infisical" {
		t.Errorf("missing keys were not added: %v", data)
	}
}

func secret(t *testing.T, client *fake.Clientset) *corev1.Secret {
	t.Helper()
	found, err := client.CoreV1().Secrets("ogcloud").Get(context.Background(), "ogcloud-infisical-root", metav1.GetOptions{})
	if err != nil {
		t.Fatalf("get: %v", err)
	}
	return found
}
