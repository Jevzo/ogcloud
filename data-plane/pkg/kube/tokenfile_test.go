package kube

import (
	"context"
	"errors"
	"testing"
	"time"
)

func TestTokenFilesAreReusedForAMinuteThenReread(t *testing.T) {
	now := time.Date(2026, 11, 2, 12, 0, 0, 0, time.UTC)
	contents, reads := []string{"first\n", "second"}, 0
	file := NewTokenFile("/token", func(string) ([]byte, error) {
		content := contents[reads]
		reads++
		return []byte(content), nil
	}, func() time.Time { return now })

	first, err := file.GetRequestMetadata(context.Background())
	if err != nil {
		t.Fatal(err)
	}
	now = now.Add(59 * time.Second)
	cached, _ := file.GetRequestMetadata(context.Background())
	now = now.Add(2 * time.Second)
	reread, _ := file.GetRequestMetadata(context.Background())

	if first["authorization"] != "Bearer first" || cached["authorization"] != "Bearer first" || reread["authorization"] != "Bearer second" {
		t.Errorf("tokens = %v %v %v", first, cached, reread)
	}
	if !file.RequireTransportSecurity() {
		t.Error("a token must never travel in plain text")
	}
}

func TestMissingOrEmptyTokenFilesFail(t *testing.T) {
	for name, read := range map[string]func(string) ([]byte, error){
		"missing": func(string) ([]byte, error) { return nil, errors.New("no such file") },
		"empty":   func(string) ([]byte, error) { return []byte(" \n"), nil },
	} {
		if _, err := NewTokenFile("/token", read, time.Now).GetRequestMetadata(context.Background()); err == nil {
			t.Errorf("%s: no error", name)
		}
	}
}
