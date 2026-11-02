package valkey

import (
	"os"
	"strings"
	"testing"

	"github.com/jevzo/ogcloud/data-plane/pkg/config"
)

func TestSettingsComeFromTheEnvironment(t *testing.T) {
	env := config.New(func(name string) (string, bool) {
		value, ok := map[string]string{
			"OGCLOUD_VALKEY_ADDRESSES": "s1:26379,s2:26379", "OGCLOUD_VALKEY_SENTINEL_MASTER": "ogcloud",
			"OGCLOUD_VALKEY_USERNAME": "gateway", "OGCLOUD_VALKEY_PASSWORD_FILE": "/pw",
		}[name]
		return value, ok
	}, func(path string) ([]byte, error) {
		if path == "/pw" {
			return []byte("secret"), nil
		}
		return nil, os.ErrNotExist
	})

	settings := FromConfig(env, nil)

	if err := env.Err(); err != nil {
		t.Fatalf("Err: %v", err)
	}
	if strings.Join(settings.Addresses, ",") != "s1:26379,s2:26379" || settings.SentinelMaster != "ogcloud" || settings.Password != "secret" {
		t.Errorf("settings = %+v", settings)
	}
}

func TestAddressesAreRequired(t *testing.T) {
	env := config.New(func(string) (string, bool) { return "", false }, os.ReadFile)

	FromConfig(env, nil)

	if err := env.Err(); err == nil || !strings.Contains(err.Error(), "OGCLOUD_VALKEY_ADDRESSES") {
		t.Errorf("Err = %v", err)
	}
}
