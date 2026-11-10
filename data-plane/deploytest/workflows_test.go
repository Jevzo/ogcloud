package deploytest

import (
	"os"
	"regexp"
	"slices"
	"testing"

	"go.yaml.in/yaml/v3"
)

const (
	releaseWorkflow = "../../.github/workflows/release.yml"
	publishWorkflow = "../../.github/workflows/publish.yml"
	publishCall     = "./.github/workflows/publish.yml"
)

var secretReference = regexp.MustCompile(`secrets\.([A-Za-z0-9_]+)`)

type workflowFile struct {
	On struct {
		WorkflowCall struct {
			Secrets map[string]any `yaml:"secrets"`
		} `yaml:"workflow_call"`
	} `yaml:"on"`
	Jobs map[string]struct {
		Uses    string            `yaml:"uses"`
		Secrets map[string]string `yaml:"secrets"`
	} `yaml:"jobs"`
}

func readWorkflow(t *testing.T, path string) (string, workflowFile) {
	t.Helper()
	content, err := os.ReadFile(path)
	if err != nil {
		t.Fatal(err)
	}
	var workflow workflowFile
	if err := yaml.Unmarshal(content, &workflow); err != nil {
		t.Fatalf("%s: %v", path, err)
	}
	return string(content), workflow
}

// A reusable workflow sees only the secrets its caller passes, environment secrets included; any other resolves to an
// empty string without an error.
func TestPublishGetsEverySecretItUsesFromEachCaller(t *testing.T) {
	publish, called := readWorkflow(t, publishWorkflow)
	var used []string
	for _, match := range secretReference.FindAllStringSubmatch(publish, -1) {
		if name := match[1]; name != "GITHUB_TOKEN" && !slices.Contains(used, name) {
			used = append(used, name)
		}
	}
	if len(used) == 0 {
		t.Fatal("the publish workflow uses no secrets; the test reads the wrong file")
	}
	for _, name := range used {
		if _, ok := called.On.WorkflowCall.Secrets[name]; !ok {
			t.Errorf("publish.yml uses secrets.%s without declaring it under on.workflow_call.secrets", name)
		}
	}

	_, release := readWorkflow(t, releaseWorkflow)
	callers := 0
	for job, spec := range release.Jobs {
		if spec.Uses != publishCall {
			continue
		}
		callers++
		for _, name := range used {
			if want := "${{ secrets." + name + " }}"; spec.Secrets[name] != want {
				t.Errorf("release.yml job %s passes %s as %q, want %q", job, name, spec.Secrets[name], want)
			}
		}
	}
	if callers == 0 {
		t.Fatal("release.yml calls the publish workflow from no job")
	}
}
