package deploytest

import (
	"os"
	"regexp"
	"regexp/syntax"
	"strings"
	"testing"

	"go.yaml.in/yaml/v3"
)

const (
	cleanupWorkflow = "../../.github/workflows/ghcr-cleanup.yml"
	cleanupAction   = "dataaxiom/ghcr-cleanup-action@"
)

// cleanupPatterns returns the tag patterns the cleanup's steps pass to the action, by input name.
func cleanupPatterns(t *testing.T) map[string]string {
	t.Helper()
	content, err := os.ReadFile(cleanupWorkflow)
	if err != nil {
		t.Fatal(err)
	}
	var workflow struct {
		Jobs map[string]struct {
			Steps []struct {
				Uses string         `yaml:"uses"`
				With map[string]any `yaml:"with"`
			} `yaml:"steps"`
		} `yaml:"jobs"`
	}
	if err := yaml.Unmarshal(content, &workflow); err != nil {
		t.Fatal(err)
	}
	patterns := map[string]string{}
	for _, job := range workflow.Jobs {
		for _, step := range job.Steps {
			if !strings.HasPrefix(step.Uses, cleanupAction) {
				continue
			}
			for _, input := range []string{"delete-tags", "exclude-tags"} {
				if pattern, ok := step.With[input].(string); ok {
					if _, seen := patterns[input]; seen {
						t.Fatalf("more than one step sets %s", input)
					}
					patterns[input] = pattern
				}
			}
		}
	}
	return patterns
}

// starHeight is how deeply repetitions nest; the action refuses patterns above 1 (safe-regex2), quantified groups
// such as (a+)? included.
func starHeight(re *syntax.Regexp) int {
	height := 0
	for _, sub := range re.Sub {
		height = max(height, starHeight(sub))
	}
	switch re.Op {
	case syntax.OpStar, syntax.OpPlus, syntax.OpQuest, syntax.OpRepeat:
		height++
	}
	return height
}

func TestCleanupTagPatternsPassTheActionsReDoSCheck(t *testing.T) {
	patterns := cleanupPatterns(t)
	for _, input := range []string{"delete-tags", "exclude-tags"} {
		pattern, ok := patterns[input]
		if !ok {
			t.Fatalf("no step sets %s", input)
		}
		parsed, err := syntax.Parse(pattern, syntax.Perl)
		if err != nil {
			t.Fatalf("%s: %v", input, err)
		}
		if height := starHeight(parsed); height > 1 {
			t.Errorf("%s %q nests repetitions (star height %d), which the action refuses", input, pattern, height)
		}
	}
}

func TestCleanupDeletesDryRunsAndKeepsEveryRealRelease(t *testing.T) {
	patterns := cleanupPatterns(t)
	dryRun := regexp.MustCompile(patterns["delete-tags"])
	release := regexp.MustCompile(patterns["exclude-tags"])
	for _, tc := range []struct {
		tag             string
		dryRun, release bool
	}{
		{tag: "0.0.0-dryrun-b539203", dryRun: true},
		{tag: "26.3-0.0.0-dryrun-b539203", dryRun: true},
		{tag: "1.8.8-0.0.0-dryrun-b539203", dryRun: true},
		{tag: "2.0.0", release: true},
		{tag: "4.3.0-2.0.0", release: true},
		{tag: "1.8.8-2.10.3", release: true},
		{tag: "2.0.0-alpha.1"},
		{tag: "26.3-2.0.0-alpha.12"},
		{tag: "sha256-" + amd64Digest[len("sha256:"):] + ".sig"},
	} {
		if got := dryRun.MatchString(tc.tag); got != tc.dryRun {
			t.Errorf("delete-tags on %q = %t, want %t", tc.tag, got, tc.dryRun)
		}
		if got := release.MatchString(tc.tag); got != tc.release {
			t.Errorf("exclude-tags on %q = %t, want %t", tc.tag, got, tc.release)
		}
	}
}
