package runtimeconfig

import (
	"errors"
	"fmt"
	"os"
	"path/filepath"
	"regexp"
	"strings"
)

const (
	velocityScope    = "runtime/velocity"
	paperModernScope = "runtime/paper-1.21.11"
	paperLegacyScope = "runtime/paper-1.8.8"
)

var (
	onlineModePattern   = regexp.MustCompile(`(?m)^online-mode=.*$`)
	bungeecordPattern   = regexp.MustCompile(`(?m)^([ \t]*)bungeecord:\s*.*$`)
	settingsRootPattern = regexp.MustCompile(`(?m)^settings:\s*$`)
)

func Apply(scopePrefix, dataDir, forwardingSecret string) error {
	secret := strings.TrimSpace(forwardingSecret)
	if secret == "" {
		return errors.New("forwarding secret must not be empty")
	}
	if strings.ContainsAny(secret, "\r\n") {
		return errors.New("forwarding secret must be a single line")
	}

	switch scopePrefix {
	case velocityScope:
		return writeManagedFile(filepath.Join(dataDir, "forwarding.secret"), secret+"\n")
	case paperModernScope, paperLegacyScope:
		if err := patchServerProperties(filepath.Join(dataDir, "server.properties")); err != nil {
			return err
		}
		if err := patchSpigotConfig(filepath.Join(dataDir, "spigot.yml")); err != nil {
			return err
		}
		return writeManagedFile(
			filepath.Join(dataDir, "plugins", "BungeeGuard", "config.yml"),
			bungeeGuardConfig(secret),
		)
	default:
		return fmt.Errorf("unsupported runtime scope: %s", scopePrefix)
	}
}

func patchServerProperties(path string) error {
	content, newline, err := readTextFile(path)
	if err != nil {
		if errors.Is(err, os.ErrNotExist) {
			return writeManagedFile(path, "online-mode=false\n")
		}
		return err
	}

	updated := string(content)
	if onlineModePattern.Match(content) {
		updated = onlineModePattern.ReplaceAllString(updated, "online-mode=false")
	} else {
		updated = appendLine(updated, newline, "online-mode=false")
	}

	return writeManagedFile(path, updated)
}

func patchSpigotConfig(path string) error {
	content, newline, err := readTextFile(path)
	if err != nil {
		if errors.Is(err, os.ErrNotExist) {
			return writeManagedFile(path, "settings:\n  bungeecord: true\n")
		}
		return err
	}

	updated := string(content)
	switch {
	case bungeecordPattern.Match(content):
		updated = bungeecordPattern.ReplaceAllString(updated, "${1}bungeecord: true")
	case settingsRootPattern.Match(content):
		settingsReplacement := "settings:" + newline + "  bungeecord: true"
		updated = settingsRootPattern.ReplaceAllString(updated, settingsReplacement)
	default:
		updated = "settings:" + newline + "  bungeecord: true" + newline + newline + updated
	}

	return writeManagedFile(path, updated)
}

func bungeeGuardConfig(secret string) string {
	return "allowed-tokens:" + "\n" + "  - " + yamlQuoted(secret) + "\n"
}

func yamlQuoted(value string) string {
	return "'" + strings.ReplaceAll(value, "'", "''") + "'"
}

func readTextFile(path string) ([]byte, string, error) {
	content, err := os.ReadFile(path)
	if err != nil {
		return nil, "", err
	}
	return content, detectNewline(content), nil
}

func detectNewline(content []byte) string {
	if strings.Contains(string(content), "\r\n") {
		return "\r\n"
	}
	return "\n"
}

func appendLine(content, newline, line string) string {
	if content == "" {
		return line + newline
	}
	if !strings.HasSuffix(content, "\n") && !strings.HasSuffix(content, "\r\n") {
		content += newline
	}
	return content + line + newline
}

func writeManagedFile(path, content string) error {
	if err := os.MkdirAll(filepath.Dir(path), 0755); err != nil {
		return err
	}
	return os.WriteFile(path, []byte(content), 0644)
}
