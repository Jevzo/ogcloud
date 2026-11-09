package deploytest

import (
	"os"
	"path/filepath"
	"regexp"
	"slices"
	"strings"
	"testing"

	"github.com/jevzo/ogcloud/data-plane/bootstrap"
)

// bootstrapConfig loads the rendered bootstrap ConfigMap through the bootstrap engine's own validation.
func bootstrapConfig(t *testing.T, objects rendered) *bootstrap.Config {
	t.Helper()
	data := str(objects.find(t, "ConfigMap", "ogcloud-bootstrap"), "data", "bootstrap.yaml")
	path := filepath.Join(t.TempDir(), "bootstrap.yaml")
	if err := os.WriteFile(path, []byte(data), 0o600); err != nil {
		t.Fatal(err)
	}
	config, err := bootstrap.LoadConfig(path)
	if err != nil {
		t.Fatalf("the chart's bootstrap config: %v", err)
	}
	if err := config.ValidateInfisical(); err != nil {
		t.Fatalf("the chart's bootstrap config for the infisical stage: %v", err)
	}
	return config
}

func TestTheBootstrapConfigPassesBootstrapsOwnValidation(t *testing.T) {
	for _, values := range valueSets(t) {
		t.Run(values, func(t *testing.T) { bootstrapConfig(t, render(t, values)) })
	}
}

var infisicalKey = regexp.MustCompile(`\{\{ \.([A-Z0-9_]+)\.Value \}\}`)

// Keys the operator brings in the bootstrap seed, which bootstrap imports into the main project (D-0114).
var seededKeys = []string{"CLOUDFLARE_API_TOKEN"}

func TestEverySyncedSecretKeyIsOneBootstrapGenerates(t *testing.T) {
	for _, values := range valueSets(t) {
		t.Run(values, func(t *testing.T) { syncedKeysAreGenerated(t, render(t, values)) })
	}
}

func syncedKeysAreGenerated(t *testing.T, objects rendered) {
	t.Helper()
	setup := bootstrapConfig(t, objects).Infisical
	// By project slug: each sync reads one project (D-0152).
	generated := map[string][]string{}
	for _, secret := range setup.Secrets {
		project := secret.Project
		if project == "" {
			project = setup.Project
		}
		generated[project] = append(generated[project], secret.Name)
	}
	generated[setup.Project] = append(generated[setup.Project], seededKeys...)
	syncs := objects.all("InfisicalStaticSecret")
	if len(syncs) == 0 {
		t.Fatal("nothing is synced from Infisical")
	}
	for _, sync := range syncs {
		sources, _ := get(sync, "spec", "sources").([]any)
		if len(sources) != 1 {
			t.Fatalf("%s reads %d sources, want 1", sync.name(), len(sources))
		}
		project := str(sources[0], "projectSlug")
		targets, _ := get(sync, "spec", "targets").([]any)
		for _, target := range targets {
			data, _ := get(target, "template", "data").(map[string]any)
			for key, value := range data {
				for _, match := range infisicalKey.FindAllStringSubmatch(value.(string), -1) {
					if !slices.Contains(generated[project], match[1]) {
						t.Errorf("%s key %s reads %s, which bootstrap neither generates nor seeds in project %s", sync.name(), key, match[1], project)
					}
				}
			}
		}
	}
}

// secretsTheChartAccountsFor are the Secrets something in the release creates: Infisical syncs, cert-manager
// certificates, the bootstrap root stage and plain Secrets such as the reviewer's token.
func secretsTheChartAccountsFor(t *testing.T, objects rendered) []string {
	t.Helper()
	config := bootstrapConfig(t, objects)
	names := []string{config.Infisical.IdentitiesSecret}
	for _, root := range config.RootSecrets {
		names = append(names, root.Name)
	}
	for _, sync := range objects.all("InfisicalStaticSecret") {
		targets, _ := get(sync, "spec", "targets").([]any)
		for _, target := range targets {
			names = append(names, str(target, "name"))
		}
	}
	for _, certificate := range objects.all("Certificate") {
		names = append(names, str(certificate, "spec", "secretName"))
	}
	for _, secret := range objects.all("Secret") {
		names = append(names, secret.name())
	}
	return names
}

func TestKafkaUsersTakeTheirPasswordsFromSyncedSecrets(t *testing.T) {
	objects := render(t, minikubeValues)
	known := secretsTheChartAccountsFor(t, objects)
	users := objects.all("KafkaUser")
	if len(users) == 0 {
		t.Fatal("no KafkaUsers rendered")
	}
	for _, user := range users {
		secret := str(user, "spec", "authentication", "password", "valueFrom", "secretKeyRef", "name")
		if !slices.Contains(known, secret) {
			t.Errorf("KafkaUser %s reads its password from %s, which nothing syncs", user.name(), secret)
		}
	}
}

func TestPostgresReadsOnlySecretsTheReleaseCreates(t *testing.T) {
	objects := render(t, minikubeValues)
	known := secretsTheChartAccountsFor(t, objects)
	cluster := objects.find(t, "Cluster", "ogcloud-postgres")
	used := []string{
		str(cluster, "spec", "bootstrap", "initdb", "secret", "name"),
		str(cluster, "spec", "certificates", "serverTLSSecret"),
	}
	roles, _ := get(cluster, "spec", "managed", "roles").([]any)
	for _, role := range roles {
		used = append(used, str(role, "passwordSecret", "name"))
	}
	for _, secret := range used {
		if !slices.Contains(known, secret) {
			t.Errorf("the PostgreSQL cluster reads Secret %q, which nothing in the release creates", secret)
		}
	}
}

func TestAnExternalKafkaGetsNoStrimziResources(t *testing.T) {
	for _, o := range render(t, chartDir+"/ci/external.yaml") {
		if strings.HasPrefix(str(o, "apiVersion"), "kafka.strimzi.io/") {
			t.Errorf("%s %s rendered for an external Kafka", o.kind(), o.name())
		}
	}
}

func TestPodsReadOnlySecretsTheReleaseCreates(t *testing.T) {
	for _, values := range valueSets(t) {
		t.Run(values, func(t *testing.T) {
			objects := render(t, values)
			known := secretsTheChartAccountsFor(t, objects)
			check := func(workload object, name string) {
				if name != "" && !slices.Contains(known, name) {
					t.Errorf("%s %s reads Secret %s, which nothing in the release creates", workload.kind(), workload.name(), name)
				}
			}
			for _, workload := range objects.workloads() {
				volumes, _ := get(workload.podSpec(), "volumes").([]any)
				for _, volume := range volumes {
					check(workload, str(volume, "secret", "secretName"))
					sources, _ := get(volume, "projected", "sources").([]any)
					for _, source := range sources {
						check(workload, str(source, "secret", "name"))
					}
				}
				containers, _ := get(workload.podSpec(), "containers").([]any)
				for _, container := range containers {
					env, _ := get(container, "env").([]any)
					for _, variable := range env {
						check(workload, str(variable, "valueFrom", "secretKeyRef", "name"))
					}
				}
			}
		})
	}
}

// The Secrets Operator syncs role passwords after CloudNativePG first tried them; it retries only for Secrets
// labelled cnpg.io/reload, so without the label the roles never get their passwords.
func TestManagedRolePasswordsReloadInCloudNativePG(t *testing.T) {
	objects := render(t, minikubeValues)
	roles, _ := get(objects.find(t, "Cluster", "ogcloud-postgres"), "spec", "managed", "roles").([]any)
	if len(roles) == 0 {
		t.Fatal("PostgreSQL manages no roles")
	}
	for _, role := range roles {
		secret := str(role, "passwordSecret", "name")
		targets, _ := get(objects.find(t, "InfisicalStaticSecret", secret), "spec", "targets").([]any)
		for _, target := range targets {
			if str(target, "metadata", "labels", "cnpg.io/reload") != "true" {
				t.Errorf("role %s reads Secret %s, which is not labelled cnpg.io/reload", str(role, "name"), secret)
			}
		}
	}
}
