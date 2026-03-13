package runtimeconfig

import (
	"os"
	"path/filepath"
	"strings"
	"testing"

	"go.yaml.in/yaml/v3"
)

func TestApplyModernPaperDisablesVelocityForwarding(t *testing.T) {
	dataDir := t.TempDir()

	writeTestFile(t, filepath.Join(dataDir, "server.properties"), "motd=test\nonline-mode=true\n")
	writeTestFile(t, filepath.Join(dataDir, "spigot.yml"), "settings:\n  bungeecord: false\n")
	writeTestFile(t, filepath.Join(dataDir, "config", "paper-global.yml"), strings.TrimSpace(`
_version: 31
proxies:
  bungee-cord:
    online-mode: true
  velocity:
    enabled: true
    online-mode: true
    secret: old-secret
`)+"\n")

	if err := Apply(paperModernScope, dataDir, "test-forwarding-secret"); err != nil {
		t.Fatalf("apply modern paper overlay: %v", err)
	}

	serverProperties := readTestFile(t, filepath.Join(dataDir, "server.properties"))
	if !strings.Contains(serverProperties, "online-mode=false") {
		t.Fatalf("expected server.properties to disable online mode, got %q", serverProperties)
	}

	spigotConfig := readTestFile(t, filepath.Join(dataDir, "spigot.yml"))
	if !strings.Contains(spigotConfig, "bungeecord: true") {
		t.Fatalf("expected spigot.yml to enable bungeecord, got %q", spigotConfig)
	}

	bungeeGuardConfig := readTestFile(t, filepath.Join(dataDir, "plugins", "BungeeGuard", "config.yml"))
	if !strings.Contains(bungeeGuardConfig, "'test-forwarding-secret'") {
		t.Fatalf("expected BungeeGuard config to include forwarding secret, got %q", bungeeGuardConfig)
	}

	paperGlobal := readPaperGlobal(t, filepath.Join(dataDir, "config", "paper-global.yml"))
	proxies := paperGlobal["proxies"].(map[string]any)
	velocity := proxies["velocity"].(map[string]any)

	if enabled, ok := velocity["enabled"].(bool); !ok || enabled {
		t.Fatalf("expected velocity forwarding to be disabled, got %#v", velocity["enabled"])
	}
	if secret, ok := velocity["secret"].(string); !ok || secret != "" {
		t.Fatalf("expected velocity secret to be cleared, got %#v", velocity["secret"])
	}
}

func writeTestFile(t *testing.T, path, content string) {
	t.Helper()
	if err := os.MkdirAll(filepath.Dir(path), 0755); err != nil {
		t.Fatalf("mkdir %s: %v", path, err)
	}
	if err := os.WriteFile(path, []byte(content), 0644); err != nil {
		t.Fatalf("write %s: %v", path, err)
	}
}

func readTestFile(t *testing.T, path string) string {
	t.Helper()
	content, err := os.ReadFile(path)
	if err != nil {
		t.Fatalf("read %s: %v", path, err)
	}
	return string(content)
}

func readPaperGlobal(t *testing.T, path string) map[string]any {
	t.Helper()
	content, err := os.ReadFile(path)
	if err != nil {
		t.Fatalf("read %s: %v", path, err)
	}

	var config map[string]any
	if err := yaml.Unmarshal(content, &config); err != nil {
		t.Fatalf("unmarshal %s: %v", path, err)
	}
	return config
}
