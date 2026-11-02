package kafka

import (
	"crypto/tls"

	"github.com/jevzo/ogcloud/data-plane/pkg/config"
)

// FromConfig reads OGCLOUD_KAFKA_BROKERS, OGCLOUD_KAFKA_USERNAME and OGCLOUD_KAFKA_PASSWORD_FILE; tlsConfig
// is nil for plaintext.
func FromConfig(c *config.Source, clientID string, tlsConfig *tls.Config) Settings {
	settings := Settings{Brokers: c.List("OGCLOUD_KAFKA_BROKERS"), ClientID: clientID, Username: c.String("OGCLOUD_KAFKA_USERNAME", ""), TLS: tlsConfig}
	if len(settings.Brokers) == 0 {
		c.Required("OGCLOUD_KAFKA_BROKERS")
	}
	if settings.Username != "" {
		settings.Password = c.Secret("OGCLOUD_KAFKA_PASSWORD_FILE")
	}
	return settings
}
