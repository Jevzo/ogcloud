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

var infisicalKey = regexp.MustCompile(`^\{\{ \.([A-Z0-9_]+)\.Value \}\}$`)

func TestEverySyncedSecretKeyIsOneBootstrapGenerates(t *testing.T) {
	objects := render(t, valueSets(t)[0])
	var generated []string
	for _, secret := range bootstrapConfig(t, objects).Infisical.Secrets {
		generated = append(generated, secret.Name)
	}
	syncs := objects.all("InfisicalStaticSecret")
	if len(syncs) == 0 {
		t.Fatal("nothing is synced from Infisical")
	}
	for _, sync := range syncs {
		targets, _ := get(sync, "spec", "targets").([]any)
		for _, target := range targets {
			data, _ := get(target, "template", "data").(map[string]any)
			for key, value := range data {
				match := infisicalKey.FindStringSubmatch(value.(string))
				if match == nil {
					continue
				}
				if !slices.Contains(generated, match[1]) {
					t.Errorf("%s key %s reads %s, which bootstrap never generates", sync.name(), key, match[1])
				}
			}
		}
	}
}

// secretsTheChartAccountsFor are the Secrets something in the release creates: Infisical syncs, cert-manager
// certificates, the bootstrap root stage and plain Secrets such as the reviewer's token.
func secretsTheChartAccountsFor(t *testing.T, objects rendered) []string {
	t.Helper()
	var names []string
	for _, root := range bootstrapConfig(t, objects).RootSecrets {
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

func TestPodsMountOnlySecretsTheReleaseCreates(t *testing.T) {
	for _, values := range valueSets(t) {
		t.Run(values, func(t *testing.T) {
			objects := render(t, values)
			known := secretsTheChartAccountsFor(t, objects)
			for _, workload := range objects.workloads() {
				volumes, _ := get(workload.podSpec(), "volumes").([]any)
				for _, volume := range volumes {
					name := str(volume, "secret", "secretName")
					if name != "" && !slices.Contains(known, name) {
						t.Errorf("%s %s mounts Secret %s, which nothing in the release creates", workload.kind(), workload.name(), name)
					}
				}
			}
		})
	}
}
