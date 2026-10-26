// Package commitmsg enforces the commit message rules: Conventional Commits headers
// (release-please builds the changelog from them, D-0029) and no AI attribution (D-0038).
package commitmsg

import (
	"errors"
	"fmt"
	"regexp"
	"strings"
)

// MaxHeaderLength is the longest allowed first line (AGENTS.md §12).
const MaxHeaderLength = 72

var (
	headerPattern = regexp.MustCompile(
		`^(build|chore|ci|docs|feat|fix|perf|refactor|revert|style|test)(\([a-z0-9][a-z0-9/-]*\))?!?: \S`)
	// Git creates these itself; they are squashed or never reach the main history.
	exemptPrefixes   = []string{"Merge ", "fixup! ", "squash! ", "amend! "}
	attributionLines = []*regexp.Regexp{
		regexp.MustCompile(`(?im)^co-authored-by:.*(claude|anthropic)`),
		regexp.MustCompile(`(?im)^claude-session:`),
		regexp.MustCompile(`(?i)generated with \[?claude code`),
	}
)

// Validate returns every rule the message breaks, or nil.
func Validate(message string) error {
	message = stripComments(message)
	header, _, _ := strings.Cut(strings.TrimSpace(message), "\n")
	var problems []error
	if header == "" {
		return errors.New("commit message is empty")
	}
	if !isExempt(header) {
		if !headerPattern.MatchString(header) {
			problems = append(problems, fmt.Errorf("header %q is not a Conventional Commit: type(scope): summary", header))
		}
		if len(header) > MaxHeaderLength {
			problems = append(problems, fmt.Errorf("header is %d characters, max %d", len(header), MaxHeaderLength))
		}
	}
	for _, pattern := range attributionLines {
		if line := pattern.FindString(message); line != "" {
			problems = append(problems, fmt.Errorf("AI attribution is not allowed (D-0038): %q", line))
		}
	}
	return errors.Join(problems...)
}

func isExempt(header string) bool {
	for _, prefix := range exemptPrefixes {
		if strings.HasPrefix(header, prefix) {
			return true
		}
	}
	return false
}

// stripComments drops the lines git removes before recording the message.
func stripComments(message string) string {
	lines := strings.Split(message, "\n")
	kept := lines[:0]
	for _, line := range lines {
		if !strings.HasPrefix(line, "#") {
			kept = append(kept, line)
		}
	}
	return strings.Join(kept, "\n")
}
