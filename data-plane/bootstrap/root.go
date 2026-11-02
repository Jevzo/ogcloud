package bootstrap

import (
	"context"
	"fmt"
	"io"
	"maps"

	corev1 "k8s.io/api/core/v1"
	apierrors "k8s.io/apimachinery/pkg/api/errors"
	metav1 "k8s.io/apimachinery/pkg/apis/meta/v1"
	"k8s.io/client-go/kubernetes"
)

const (
	managedByLabel = "app.kubernetes.io/managed-by"
	managedBy      = "ogcloud-bootstrap"
	// Helm leaves these Secrets in place on uninstall, so a reinstall finds Infisical's data readable.
	keepAnnotation = "helm.sh/resource-policy"
	keep           = "keep"
)

// EnsureRootSecrets creates the configured Secrets and adds keys they lack. It never changes a value that
// exists, so reruns on upgrade are safe (D-0044).
func EnsureRootSecrets(ctx context.Context, client kubernetes.Interface, config *Config, random io.Reader) error {
	secrets := client.CoreV1().Secrets(config.Namespace)
	for _, spec := range config.RootSecrets {
		existing, err := secrets.Get(ctx, spec.Name, metav1.GetOptions{})
		if apierrors.IsNotFound(err) {
			data, err := generateKeys(spec.Keys, nil, random)
			if err != nil {
				return fmt.Errorf("secret %s: %w", spec.Name, err)
			}
			if _, err := secrets.Create(ctx, &corev1.Secret{
				ObjectMeta: metav1.ObjectMeta{
					Name:        spec.Name,
					Labels:      map[string]string{managedByLabel: managedBy},
					Annotations: map[string]string{keepAnnotation: keep},
				},
				Data: data,
			}, metav1.CreateOptions{}); err != nil {
				return fmt.Errorf("create secret %s: %w", spec.Name, err)
			}
			continue
		}
		if err != nil {
			return fmt.Errorf("read secret %s: %w", spec.Name, err)
		}
		missing, err := generateKeys(spec.Keys, existing.Data, random)
		if err != nil {
			return fmt.Errorf("secret %s: %w", spec.Name, err)
		}
		if len(missing) == 0 {
			continue
		}
		updated := existing.DeepCopy()
		if updated.Data == nil {
			updated.Data = map[string][]byte{}
		}
		maps.Copy(updated.Data, missing)
		if _, err := secrets.Update(ctx, updated, metav1.UpdateOptions{}); err != nil {
			return fmt.Errorf("add keys to secret %s: %w", spec.Name, err)
		}
	}
	return nil
}

// generateKeys makes a value for every key not in existing.
func generateKeys(keys map[string]Generator, existing map[string][]byte, random io.Reader) (map[string][]byte, error) {
	data := map[string][]byte{}
	for key, generator := range keys {
		if _, ok := existing[key]; ok {
			continue
		}
		value, err := generator.Make(random)
		if err != nil {
			return nil, fmt.Errorf("key %s: %w", key, err)
		}
		data[key] = []byte(value)
	}
	return data, nil
}
