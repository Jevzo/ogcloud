// Package config reads a component's settings from environment variables and mounted secret files. It
// collects every problem, so a misconfigured pod reports all of them at once.
package config

import (
	"errors"
	"fmt"
	"os"
	"strconv"
	"strings"
	"time"
)

// Source reads settings and remembers what was missing or malformed.
type Source struct {
	lookup   func(string) (string, bool)
	readFile func(string) ([]byte, error)
	problems []error
}

// FromEnvironment reads the process environment and the file system.
func FromEnvironment() *Source {
	return New(os.LookupEnv, os.ReadFile)
}

// New reads settings through lookup and secret files through readFile, e.g. fakes in tests.
func New(lookup func(string) (string, bool), readFile func(string) ([]byte, error)) *Source {
	return &Source{lookup: lookup, readFile: readFile}
}

// String returns the variable, or fallback when it is unset or empty.
func (s *Source) String(name, fallback string) string {
	if value, ok := s.lookup(name); ok && value != "" {
		return value
	}
	return fallback
}

// Required returns the variable and records a problem when it is unset or empty.
func (s *Source) Required(name string) string {
	value, ok := s.lookup(name)
	if !ok || value == "" {
		s.problems = append(s.problems, fmt.Errorf("%s is required", name))
	}
	return value
}

// Int returns the variable as an integer, or fallback when unset.
func (s *Source) Int(name string, fallback int) int {
	return parse(s, name, fallback, strconv.Atoi)
}

// Bool returns the variable as a boolean, or fallback when unset.
func (s *Source) Bool(name string, fallback bool) bool {
	return parse(s, name, fallback, strconv.ParseBool)
}

// Duration returns the variable as a duration such as "30s", or fallback when unset.
func (s *Source) Duration(name string, fallback time.Duration) time.Duration {
	return parse(s, name, fallback, time.ParseDuration)
}

// List returns the comma-separated variable, without blanks; empty when unset.
func (s *Source) List(name string) []string {
	var items []string
	for item := range strings.SplitSeq(s.String(name, ""), ",") {
		if trimmed := strings.TrimSpace(item); trimmed != "" {
			items = append(items, trimmed)
		}
	}
	return items
}

// Secret reads the file the variable points to, so secrets come from mounted files rather than the
// environment (D-0044). A trailing newline is dropped.
func (s *Source) Secret(name string) string {
	path := s.Required(name)
	if path == "" {
		return ""
	}
	content, err := s.readFile(path)
	if err != nil {
		s.problems = append(s.problems, fmt.Errorf("%s: read %s: %w", name, path, err))
		return ""
	}
	return strings.TrimRight(string(content), "\r\n")
}

// Err reports every problem met so far, or nil.
func (s *Source) Err() error {
	return errors.Join(s.problems...)
}

func parse[T any](s *Source, name string, fallback T, convert func(string) (T, error)) T {
	raw := s.String(name, "")
	if raw == "" {
		return fallback
	}
	value, err := convert(raw)
	if err != nil {
		s.problems = append(s.problems, fmt.Errorf("%s=%q: %w", name, raw, err))
		return fallback
	}
	return value
}
