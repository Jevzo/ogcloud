package config

import (
	"os"
	"strconv"
	"strings"
)

type Config struct {
	ListenAddr                    string
	Kafka                         KafkaConfig
	ApiURL                        string
	ApiEmail                      string
	ApiPassword                   string
	ApiStartupTimeoutSeconds      int
	ApiStartupPollIntervalSeconds int
	ProxyProtocol                 bool
	ProxyHealthTimeout            int
	DrainTimeoutSeconds           int
}

type KafkaConfig struct {
	Brokers []string
	GroupID string
}

func Load() *Config {
	return &Config{
		ListenAddr: getEnv("LISTEN_ADDR", ":25565"),
		Kafka: KafkaConfig{
			Brokers: getEnvAsSlice("KAFKA_BROKERS", []string{"localhost:9094"}),
			GroupID: getEnv("KAFKA_GROUP_ID", "ogcloud-loadbalancer"),
		},
		ApiURL:                        getEnv("API_URL", "http://localhost:8080"),
		ApiEmail:                      getEnv("OGCLOUD_API_EMAIL", ""),
		ApiPassword:                   getEnv("OGCLOUD_API_PASSWORD", ""),
		ApiStartupTimeoutSeconds:      getEnvAsInt("API_STARTUP_TIMEOUT_SECONDS", 300),
		ApiStartupPollIntervalSeconds: getEnvAsInt("API_STARTUP_POLL_INTERVAL_SECONDS", 5),
		ProxyProtocol:                 getEnvAsBool("PROXY_PROTOCOL_ENABLED", true),
		ProxyHealthTimeout:            getEnvAsInt("PROXY_HEALTH_TIMEOUT_SECONDS", 30),
		DrainTimeoutSeconds:           getEnvAsInt("DRAIN_TIMEOUT_SECONDS", 30),
	}
}

func getEnv(key, defaultVal string) string {
	if val := os.Getenv(key); val != "" {
		return val
	}
	return defaultVal
}

func getEnvAsInt(key string, defaultVal int) int {
	if val := os.Getenv(key); val != "" {
		if i, err := strconv.Atoi(val); err == nil {
			return i
		}
	}
	return defaultVal
}

func getEnvAsBool(key string, defaultVal bool) bool {
	if val := os.Getenv(key); val != "" {
		if b, err := strconv.ParseBool(val); err == nil {
			return b
		}
	}
	return defaultVal
}

func getEnvAsSlice(key string, defaultVal []string) []string {
	if val := os.Getenv(key); val != "" {
		return strings.Split(val, ",")
	}
	return defaultVal
}
