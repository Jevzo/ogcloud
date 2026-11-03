package config

import (
	"errors"
	"io/fs"
	"strings"
	"testing"
	"time"
)

func source(env map[string]string, files map[string]string) *Source {
	return New(
		func(name string) (string, bool) {
			value, ok := env[name]
			return value, ok
		},
		func(path string) ([]byte, error) {
			content, ok := files[path]
			if !ok {
				return nil, fs.ErrNotExist
			}
			return []byte(content), nil
		},
	)
}

func TestValuesAndFallbacks(t *testing.T) {
	s := source(map[string]string{
		"NAME": "gateway", "PORT": "8443", "TLS": "true", "TIMEOUT": "5s", "BROKERS": " a:9092, ,b:9092 ", "EMPTY": "",
	}, nil)

	if got := s.String("NAME", "x"); got != "gateway" {
		t.Errorf("String = %q", got)
	}
	if got := s.String("EMPTY", "fallback"); got != "fallback" {
		t.Errorf("empty variable did not fall back: %q", got)
	}
	if got := s.Int("PORT", 0); got != 8443 {
		t.Errorf("Int = %d", got)
	}
	if got := s.Bool("TLS", false); !got {
		t.Error("Bool = false")
	}
	if got := s.Duration("TIMEOUT", time.Minute); got != 5*time.Second {
		t.Errorf("Duration = %v", got)
	}
	if got := s.Duration("MISSING", time.Minute); got != time.Minute {
		t.Errorf("Duration fallback = %v", got)
	}
	if got := strings.Join(s.List("BROKERS"), "|"); got != "a:9092|b:9092" {
		t.Errorf("List = %q", got)
	}
	if err := s.Err(); err != nil {
		t.Errorf("unexpected problems: %v", err)
	}
}

func TestSecretsComeFromFilesWithoutTrailingNewline(t *testing.T) {
	s := source(map[string]string{"PASSWORD_FILE": "/secrets/password"}, map[string]string{"/secrets/password": "s3cret\n"})

	if got := s.Secret("PASSWORD_FILE"); got != "s3cret" {
		t.Errorf("Secret = %q", got)
	}
}

func TestEveryProblemIsReported(t *testing.T) {
	s := source(map[string]string{"PORT": "eighty", "KEY_FILE": "/missing", "QUEUE": "0", "TIMEOUT": "-5s"}, nil)

	s.Required("BROKERS")
	s.Int("PORT", 1)
	s.Int("QUEUE", 1)
	s.Duration("TIMEOUT", time.Second)
	s.Secret("KEY_FILE")
	s.Secret("TOKEN_FILE")

	err := s.Err()
	for _, want := range []string{"BROKERS is required", `PORT="eighty"`, `QUEUE="0": must be positive`, `TIMEOUT="-5s": must be positive`,
		"KEY_FILE: read /missing", "TOKEN_FILE is required"} {
		if err == nil || !strings.Contains(err.Error(), want) {
			t.Errorf("missing %q in %v", want, err)
		}
	}
	if !errors.Is(err, fs.ErrNotExist) {
		t.Error("the file error is not wrapped")
	}
}
