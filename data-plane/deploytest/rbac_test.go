package deploytest

import (
	"context"
	"encoding/json"
	"testing"

	authorizationv1 "k8s.io/api/authorization/v1"
	corev1 "k8s.io/api/core/v1"
	rbacv1 "k8s.io/api/rbac/v1"
	metav1 "k8s.io/apimachinery/pkg/apis/meta/v1"
	"k8s.io/client-go/kubernetes"

	"github.com/jevzo/ogcloud/data-plane/internal/testk3s"
)

const serversNamespace = "ogcloud-servers"

// access is one request a service account makes, or must not be able to make.
type access struct {
	account, verb, group, resource, namespace, name string
	allowed                                         bool
	why                                             string
}

// The rendered RBAC, applied to a real API server, which decides each request as it would in a cluster: exactly what
// the controller, the gateway and bootstrap touch, and no more (D-0013, D-0154).
func TestEachServiceAccountMayDoWhatItsServiceDoesAndNoMore(t *testing.T) {
	cluster := testk3s.Start(t)
	applyRBAC(t, cluster.Client, render(t, hetznerValues))
	for _, a := range []access{
		{"ogcloud-controller", "create", "", "pods", serversNamespace, "", true, "the provisioner creates game pods (D-0075)"},
		{"ogcloud-controller", "patch", "", "pods", serversNamespace, "", true, "the safe-to-evict annotation (D-0047)"},
		{"ogcloud-controller", "delete", "", "persistentvolumeclaims", serversNamespace, "", true, "purged STATIC volumes (D-0043)"},
		{"ogcloud-controller", "get", "", "secrets", serversNamespace, "ogcloud-forwarding", true, "the rotation waits for the synced tokens (D-0078)"},
		{"ogcloud-controller", "get", "", "secrets", serversNamespace, "another", false, "no other Secret of the game pods"},
		{"ogcloud-controller", "list", "", "secrets", serversNamespace, "", false, "listing would show every Secret"},
		{"ogcloud-controller", "create", "", "pods", namespace, "", false, "game pods run only in their namespace (D-0056)"},
		{"ogcloud-controller", "get", "", "secrets", namespace, "ogcloud-api-secrets", false, "the platform's Secrets are not the controller's"},
		{"ogcloud-controller", "patch", "coordination.k8s.io", "leases", namespace, "", true, "leader election (D-0007)"},
		{"ogcloud-controller", "list", "", "nodes", "", "", true, "cordoned nodes drain their servers"},
		{"ogcloud-controller", "create", "authentication.k8s.io", "tokenreviews", "", "", true, "internal gRPC callers (D-0013)"},
		{"ogcloud-controller", "delete", "", "nodes", "", "", false, "nodes are read only"},
		{"ogcloud-gateway", "create", "authentication.k8s.io", "tokenreviews", "", "", true, "nodes' tokens (D-0013)"},
		{"ogcloud-gateway", "watch", "", "pods", serversNamespace, "", true, "the pod informer binds sessions to pods"},
		{"ogcloud-gateway", "create", "", "pods", serversNamespace, "", false, "the gateway starts nothing"},
		{"ogcloud-gateway", "get", "", "secrets", serversNamespace, "ogcloud-forwarding", false, "the gateway holds no forwarding token"},
		{"ogcloud-bootstrap", "create", "", "secrets", namespace, "", true, "the root Secrets (D-0044)"},
		{"ogcloud-bootstrap", "update", "", "secrets", namespace, "ogcloud-infisical-root", true, "missing root keys are added"},
		{"ogcloud-bootstrap", "delete", "", "secrets", namespace, "ogcloud-seed", true, "the seed goes after the import (D-0114)"},
		{"ogcloud-bootstrap", "delete", "", "secrets", namespace, "ogcloud-infisical-root", false, "root Secrets are never deleted"},
		{"ogcloud-bootstrap", "get", "", "secrets", namespace, "ogcloud-api-secrets", false, "the services' synced Secrets are not bootstrap's"},
		{"ogcloud-bootstrap", "update", "", "configmaps", namespace, "ogcloud-infisical-state", true, "bootstrap's state"},
		{"ogcloud-bootstrap", "get", "", "configmaps", namespace, "ogcloud-api", false, "no other ConfigMap"},
	} {
		if got := allowed(t, cluster.Client, a); got != a.allowed {
			t.Errorf("%s may %s %s/%s %q in %q: %v, want %v: %s", a.account, a.verb, a.group, a.resource, a.name,
				a.namespace, got, a.allowed, a.why)
		}
	}
}

// applyRBAC creates the namespaces and every ServiceAccount, Role, ClusterRole and binding the chart renders.
func applyRBAC(t *testing.T, client kubernetes.Interface, objects rendered) {
	t.Helper()
	ctx := context.Background()
	for _, name := range []string{namespace, serversNamespace} {
		if _, err := client.CoreV1().Namespaces().Create(ctx, &corev1.Namespace{ObjectMeta: metav1.ObjectMeta{Name: name}}, metav1.CreateOptions{}); err != nil {
			t.Fatalf("namespace %s: %v", name, err)
		}
	}
	for _, o := range objects {
		var err error
		switch o.kind() {
		case "ServiceAccount":
			account := typed[corev1.ServiceAccount](t, o)
			_, err = client.CoreV1().ServiceAccounts(account.Namespace).Create(ctx, account, metav1.CreateOptions{})
		case "Role":
			role := typed[rbacv1.Role](t, o)
			_, err = client.RbacV1().Roles(role.Namespace).Create(ctx, role, metav1.CreateOptions{})
		case "RoleBinding":
			binding := typed[rbacv1.RoleBinding](t, o)
			_, err = client.RbacV1().RoleBindings(binding.Namespace).Create(ctx, binding, metav1.CreateOptions{})
		case "ClusterRole":
			_, err = client.RbacV1().ClusterRoles().Create(ctx, typed[rbacv1.ClusterRole](t, o), metav1.CreateOptions{})
		case "ClusterRoleBinding":
			_, err = client.RbacV1().ClusterRoleBindings().Create(ctx, typed[rbacv1.ClusterRoleBinding](t, o), metav1.CreateOptions{})
		}
		if err != nil {
			t.Fatalf("%s %s: %v", o.kind(), o.name(), err)
		}
	}
}

func typed[T any](t *testing.T, o object) *T {
	t.Helper()
	data, err := json.Marshal(map[string]any(o))
	if err != nil {
		t.Fatal(err)
	}
	var value T
	if err := json.Unmarshal(data, &value); err != nil {
		t.Fatalf("%s %s: %v", o.kind(), o.name(), err)
	}
	return &value
}

func allowed(t *testing.T, client kubernetes.Interface, a access) bool {
	t.Helper()
	review := &authorizationv1.SubjectAccessReview{Spec: authorizationv1.SubjectAccessReviewSpec{
		User:   "system:serviceaccount:" + namespace + ":" + a.account,
		Groups: []string{"system:serviceaccounts", "system:serviceaccounts:" + namespace, "system:authenticated"},
		ResourceAttributes: &authorizationv1.ResourceAttributes{
			Verb: a.verb, Group: a.group, Resource: a.resource, Namespace: a.namespace, Name: a.name,
		},
	}}
	answer, err := client.AuthorizationV1().SubjectAccessReviews().Create(context.Background(), review, metav1.CreateOptions{})
	if err != nil {
		t.Fatalf("review %+v: %v", a, err)
	}
	return answer.Status.Allowed
}
