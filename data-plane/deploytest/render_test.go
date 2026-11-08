package deploytest

import (
	"bytes"
	"errors"
	"io"
	"os/exec"
	"path/filepath"
	"strings"
	"testing"

	"go.yaml.in/yaml/v3"
)

const (
	chartDir  = "../../deploy/helm/ogcloud"
	namespace = "ogcloud"
	// The chart needs an install target's values; minikube's serves tests about any target.
	minikubeValues = chartDir + "/ci/minikube.yaml"
)

// object is one rendered manifest.
type object map[string]any

func (o object) kind() string { return str(map[string]any(o), "kind") }

func (o object) name() string { return str(map[string]any(o), "metadata", "name") }

// rendered is a chart rendering for one value set.
type rendered []object

// valueSets are the files in the chart's ci/ folder, which `mise run check:chart` also validates.
func valueSets(t *testing.T) []string {
	t.Helper()
	sets, err := filepath.Glob(filepath.Join(chartDir, "ci", "*.yaml"))
	if err != nil || len(sets) == 0 {
		t.Fatalf("no value sets in %s/ci: %v", chartDir, err)
	}
	return sets
}

// render runs helm template with the given value files and decodes every document.
func render(t *testing.T, values ...string) rendered {
	t.Helper()
	args := []string{"template", "ogcloud", chartDir, "--namespace", namespace}
	for _, file := range values {
		args = append(args, "-f", file)
	}
	var stderr bytes.Buffer
	command := exec.CommandContext(t.Context(), "helm", args...)
	command.Stderr = &stderr
	output, err := command.Output()
	if err != nil {
		t.Fatalf("helm template %v: %v\n%s", values, err, stderr.String())
	}
	decoder := yaml.NewDecoder(bytes.NewReader(output))
	var objects rendered
	for {
		// Decoded into a plain map: yaml.v3 would give nested mappings the named type too.
		var doc map[string]any
		err := decoder.Decode(&doc)
		if errors.Is(err, io.EOF) {
			return objects
		}
		if err != nil {
			t.Fatalf("decode rendered chart: %v", err)
		}
		if doc != nil {
			objects = append(objects, object(doc))
		}
	}
}

// find returns the one object of kind and name, failing the test when it is missing.
func (r rendered) find(t *testing.T, kind, name string) object {
	t.Helper()
	for _, o := range r {
		if o.kind() == kind && o.name() == name {
			return o
		}
	}
	t.Fatalf("no %s %s rendered", kind, name)
	return nil
}

// get walks maps by key; a missing key gives nil.
func get(value any, path ...string) any {
	if o, ok := value.(object); ok {
		value = map[string]any(o)
	}
	for _, key := range path {
		m, ok := value.(map[string]any)
		if !ok {
			return nil
		}
		value = m[key]
	}
	return value
}

func str(value any, path ...string) string {
	s, _ := get(value, path...).(string)
	return s
}

func containsLine(text, line string) bool {
	for _, candidate := range strings.Split(text, "\n") {
		if strings.TrimSpace(candidate) == line {
			return true
		}
	}
	return false
}
