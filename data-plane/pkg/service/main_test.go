package service

import (
	"context"
	"errors"
	"os"
	"testing"

	"github.com/jevzo/ogcloud/data-plane/pkg/config"
)

func environment(values map[string]string) *config.Source {
	return config.New(func(name string) (string, bool) {
		value, ok := values[name]
		return value, ok
	}, os.ReadFile)
}

func TestStartHandsServeItsSettingsAndEnvironment(t *testing.T) {
	done := errors.New("served")
	env := environment(map[string]string{"OGCLOUD_ADMIN_LISTEN": "127.0.0.1:0", "OGCLOUD_LOG_LEVEL": "debug"})

	err := start("test", env, func(*config.Source) (string, error) { return "settings", nil },
		func(_ context.Context, settings string, env Env) error {
			if settings != "settings" || env.Logger == nil || env.Registry == nil || env.Readiness == nil {
				t.Errorf("serve got %q, %+v", settings, env)
			}
			return done
		})

	if !errors.Is(err, done) {
		t.Errorf("start = %v", err)
	}
}

func TestStartStopsOnBadSettings(t *testing.T) {
	invalid := errors.New("invalid")

	err := start("test", environment(nil), func(*config.Source) (int, error) { return 0, invalid },
		func(context.Context, int, Env) error {
			t.Error("served despite bad settings")
			return nil
		})

	if !errors.Is(err, invalid) {
		t.Errorf("start = %v", err)
	}
}
