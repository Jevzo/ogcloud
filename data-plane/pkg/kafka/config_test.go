package kafka

import (
	"os"
	"strings"
	"testing"

	"github.com/jevzo/ogcloud/data-plane/pkg/config"
)

func source(env map[string]string, files map[string]string) *config.Source {
	return config.New(func(name string) (string, bool) {
		value, ok := env[name]
		return value, ok
	}, func(path string) ([]byte, error) {
		if content, ok := files[path]; ok {
			return []byte(content), nil
		}
		return nil, os.ErrNotExist
	})
}

func TestSettingsComeFromTheEnvironment(t *testing.T) {
	env := source(map[string]string{
		"OGCLOUD_KAFKA_BROKERS": "a:9093,b:9093", "OGCLOUD_KAFKA_USERNAME": "gateway", "OGCLOUD_KAFKA_PASSWORD_FILE": "/pw",
	}, map[string]string{"/pw": "secret\n"})

	settings := FromConfig(env, "gateway", nil)

	if err := env.Err(); err != nil {
		t.Fatalf("Err: %v", err)
	}
	if len(settings.Brokers) != 2 || settings.ClientID != "gateway" || settings.Username != "gateway" || settings.Password != "secret" {
		t.Errorf("settings = %+v", settings)
	}
}

func TestBrokersAreRequiredAndPasswordsOnlyReadWithAUser(t *testing.T) {
	env := source(map[string]string{}, nil)

	settings := FromConfig(env, "gateway", nil)

	if err := env.Err(); err == nil || !strings.Contains(err.Error(), "OGCLOUD_KAFKA_BROKERS") {
		t.Errorf("Err = %v", err)
	}
	if settings.Password != "" {
		t.Errorf("password read without a user")
	}
}
