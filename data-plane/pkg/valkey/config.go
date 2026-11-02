package valkey

import (
	"crypto/tls"

	"github.com/jevzo/ogcloud/data-plane/pkg/config"
)

// FromConfig reads OGCLOUD_VALKEY_ADDRESSES, OGCLOUD_VALKEY_SENTINEL_MASTER, OGCLOUD_VALKEY_USERNAME and
// OGCLOUD_VALKEY_PASSWORD_FILE; tlsConfig is nil for plaintext.
func FromConfig(c *config.Source, tlsConfig *tls.Config) Settings {
	settings := Settings{
		Addresses:      c.List("OGCLOUD_VALKEY_ADDRESSES"),
		SentinelMaster: c.String("OGCLOUD_VALKEY_SENTINEL_MASTER", ""),
		Username:       c.String("OGCLOUD_VALKEY_USERNAME", ""),
		TLS:            tlsConfig,
	}
	if len(settings.Addresses) == 0 {
		c.Required("OGCLOUD_VALKEY_ADDRESSES")
	}
	if settings.Username != "" {
		settings.Password = c.Secret("OGCLOUD_VALKEY_PASSWORD_FILE")
	}
	return settings
}
