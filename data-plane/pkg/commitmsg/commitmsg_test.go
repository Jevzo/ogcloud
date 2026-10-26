package commitmsg

import (
	"strings"
	"testing"
)

func TestValidate(t *testing.T) {
	tests := []struct {
		name    string
		message string
		wantErr string
	}{
		{"plain", "feat: add autoscaler", ""},
		{"scope and body", "fix(gateway): close stream on pod delete\n\nRefs: D-0013\n", ""},
		{"breaking", "refactor(api)!: drop v0 routes", ""},
		{"git comments ignored", "chore: tidy\n# Please enter the commit message\n", ""},
		{"merge exempt", "Merge branch 'v2' into feature", ""},
		{"fixup exempt", "fixup! feat: add autoscaler", ""},
		{"empty", "\n# only a comment\n", "empty"},
		{"no type", "add autoscaler", "not a Conventional Commit"},
		{"unknown type", "feature: add autoscaler", "not a Conventional Commit"},
		{"uppercase scope", "feat(API): add autoscaler", "not a Conventional Commit"},
		{"too long", "feat: " + strings.Repeat("a", 67), "max 72"},
		{"co-author claude", "feat: x\n\nCo-Authored-By: Claude Opus <noreply@anthropic.com>", "AI attribution"},
		{"session link", "feat: x\n\nClaude-Session: https://claude.ai/code/session_1", "AI attribution"},
		{"generated footer", "feat: x\n\nGenerated with [Claude Code](https://claude.com/claude-code)", "AI attribution"},
		{"human co-author", "feat: x\n\nCo-Authored-By: Jevzo <jerrygit@outlook.com>", ""},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			err := Validate(tt.message)
			if tt.wantErr == "" {
				if err != nil {
					t.Errorf("Validate() = %v, want nil", err)
				}
				return
			}
			if err == nil || !strings.Contains(err.Error(), tt.wantErr) {
				t.Errorf("Validate() = %v, want error containing %q", err, tt.wantErr)
			}
		})
	}
}
