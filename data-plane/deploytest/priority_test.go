package deploytest

import (
	"context"
	"os"
	"path/filepath"
	"strings"
	"testing"
	"time"

	schedulingv1 "k8s.io/api/scheduling/v1"
	metav1 "k8s.io/apimachinery/pkg/apis/meta/v1"
	"k8s.io/apimachinery/pkg/apis/meta/v1/unstructured"
	"k8s.io/apimachinery/pkg/runtime/schema"
	"k8s.io/apimachinery/pkg/util/wait"
	"k8s.io/client-go/dynamic"

	"github.com/jevzo/ogcloud/data-plane/internal/testk3s"
)

// On an upgrade bootstrap's root stage outranks the game servers (D-0056): on a node they fill, a hook without
// priority is never scheduled and the upgrade retries forever. Until the release has created its PriorityClasses, as
// on a first install or one retried before getting that far, it goes without, since a pod naming a missing class is
// refused.
func TestBootstrapRootTakesThePlatformPriorityOnceItExists(t *testing.T) {
	cluster := testk3s.Start(t)
	kubeconfig := filepath.Join(t.TempDir(), "kubeconfig")
	if err := os.WriteFile(kubeconfig, cluster.Kubeconfig, 0o600); err != nil {
		t.Fatal(err)
	}
	standInCRDs(t, cluster, render(t, hetznerValues))
	priority := func() string {
		t.Helper()
		// A server-side dry run lets the chart's lookups see the cluster.
		output, err := helmTemplate(t, []string{hetznerValues}, "--dry-run=server", "--kubeconfig", kubeconfig)
		if err != nil {
			t.Fatalf("helm template: %v", err)
		}
		return str(decodeRendered(t, output).find(t, "Job", "ogcloud-bootstrap-root").podSpec(), "priorityClassName")
	}

	if class := priority(); class != "" {
		t.Errorf("before the release's PriorityClasses exist the root stage has priority %q, want none", class)
	}
	// As the release created it, so the dry run treats it as the release's own.
	platform := &schedulingv1.PriorityClass{ObjectMeta: metav1.ObjectMeta{
		Name:        "ogcloud-platform",
		Labels:      map[string]string{"app.kubernetes.io/managed-by": "Helm"},
		Annotations: map[string]string{"meta.helm.sh/release-name": "ogcloud", "meta.helm.sh/release-namespace": namespace},
	}, Value: 1000000}
	if _, err := cluster.Client.SchedulingV1().PriorityClasses().Create(t.Context(), platform, metav1.CreateOptions{}); err != nil {
		t.Fatal(err)
	}
	if class := priority(); class != "ogcloud-platform" {
		t.Errorf("once ogcloud-platform exists the root stage has priority %q, want it", class)
	}
}

// standInCRDs registers an open schema for every kind the rendering uses that the cluster does not serve, as the
// operators' charts would, so helm can map each object to the API.
func standInCRDs(t *testing.T, cluster *testk3s.Cluster, objects rendered) {
	t.Helper()
	client, err := dynamic.NewForConfig(cluster.Config)
	if err != nil {
		t.Fatal(err)
	}
	crds := client.Resource(schema.GroupVersionResource{
		Group: "apiextensions.k8s.io", Version: "v1", Resource: "customresourcedefinitions",
	})
	created := map[schema.GroupVersionKind]bool{}
	for _, o := range objects {
		version, err := schema.ParseGroupVersion(str(o, "apiVersion"))
		if err != nil {
			t.Fatal(err)
		}
		gvk := version.WithKind(o.kind())
		if gvk.Group == "" || created[gvk] || served(cluster, gvk) {
			continue
		}
		created[gvk] = true
		kind := gvk.Kind
		plural := strings.ToLower(kind) + "s"
		scope := "Cluster"
		if str(o, "metadata", "namespace") != "" {
			scope = "Namespaced"
		}
		crd := &unstructured.Unstructured{Object: map[string]any{
			"apiVersion": "apiextensions.k8s.io/v1",
			"kind":       "CustomResourceDefinition",
			"metadata":   map[string]any{"name": plural + "." + gvk.Group, "annotations": approval(gvk.Group)},
			"spec": map[string]any{
				"group": gvk.Group,
				"names": map[string]any{"kind": kind, "plural": plural, "singular": strings.ToLower(kind)},
				"scope": scope,
				"versions": []any{map[string]any{
					"name": gvk.Version, "served": true, "storage": true,
					"schema": map[string]any{"openAPIV3Schema": map[string]any{
						"type": "object", "x-kubernetes-preserve-unknown-fields": true,
					}},
				}},
			},
		}}
		if _, err := crds.Create(t.Context(), crd, metav1.CreateOptions{}); err != nil {
			t.Fatalf("stand-in CRD for %s: %v", gvk, err)
		}
	}
	// The API server serves a new CRD a moment after accepting it.
	for gvk := range created {
		err := wait.PollUntilContextTimeout(t.Context(), 100*time.Millisecond, 30*time.Second, true,
			func(context.Context) (bool, error) { return served(cluster, gvk), nil })
		if err != nil {
			t.Fatalf("%s is not served: %v", gvk, err)
		}
	}
}

// approval marks a stand-in in a Kubernetes-owned group (Gateway API) as unapproved, as the API server requires.
func approval(group string) map[string]any {
	if group != "k8s.io" && !strings.HasSuffix(group, ".k8s.io") {
		return nil
	}
	return map[string]any{"api-approved.kubernetes.io": "unapproved, a test stand-in"}
}

// served tells whether the cluster serves the kind; discovery errors read as not served.
func served(cluster *testk3s.Cluster, gvk schema.GroupVersionKind) bool {
	resources, err := cluster.Client.Discovery().ServerResourcesForGroupVersion(gvk.GroupVersion().String())
	if err != nil {
		return false
	}
	for _, resource := range resources.APIResources {
		if resource.Kind == gvk.Kind {
			return true
		}
	}
	return false
}
