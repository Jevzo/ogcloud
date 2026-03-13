package runtimeconfig

import (
	"errors"
	"fmt"
	"os"
	"path/filepath"
	"regexp"
	"strings"

	"go.yaml.in/yaml/v3"
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
	case paperModernScope:
		return applyPaperRuntime(dataDir, secret, true)
	case paperLegacyScope:
		return applyPaperRuntime(dataDir, secret, false)
	default:
		return fmt.Errorf("unsupported runtime scope: %s", scopePrefix)
	}
}

func applyPaperRuntime(dataDir, secret string, modern bool) error {
	if err := patchServerProperties(filepath.Join(dataDir, "server.properties")); err != nil {
		return err
	}
	if err := patchSpigotConfig(filepath.Join(dataDir, "spigot.yml")); err != nil {
		return err
	}
	if modern {
		if err := patchPaperGlobalConfig(filepath.Join(dataDir, "config", "paper-global.yml")); err != nil {
			return err
		}
	}
	return writeManagedFile(
		filepath.Join(dataDir, "plugins", "BungeeGuard", "config.yml"),
		bungeeGuardConfig(secret),
	)
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

func patchPaperGlobalConfig(path string) error {
	content, _, err := readTextFile(path)
	if err != nil {
		if errors.Is(err, os.ErrNotExist) {
			return nil
		}
		return err
	}

	var document yaml.Node
	if err := yaml.Unmarshal(content, &document); err != nil {
		return fmt.Errorf("parse paper-global.yml: %w", err)
	}

	root, err := resolveDocumentMap(&document)
	if err != nil {
		return fmt.Errorf("resolve paper-global.yml root: %w", err)
	}

	proxies := ensureMappingValue(root, "proxies")
	velocity := ensureMappingValue(proxies, "velocity")
	setScalarValue(velocity, "enabled", "!!bool", "false", 0)
	setScalarValue(velocity, "secret", "!!str", "", yaml.DoubleQuotedStyle)

	updated, err := yaml.Marshal(&document)
	if err != nil {
		return fmt.Errorf("marshal paper-global.yml: %w", err)
	}

	return writeManagedFile(path, string(updated))
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

func resolveDocumentMap(document *yaml.Node) (*yaml.Node, error) {
	switch document.Kind {
	case 0, yaml.DocumentNode:
		if len(document.Content) == 0 {
			root := &yaml.Node{Kind: yaml.MappingNode, Tag: "!!map"}
			document.Kind = yaml.DocumentNode
			document.Content = []*yaml.Node{root}
			return root, nil
		}
		root := document.Content[0]
		if root.Kind != yaml.MappingNode {
			return nil, fmt.Errorf("expected mapping root, got kind %d", root.Kind)
		}
		return root, nil
	case yaml.MappingNode:
		return document, nil
	default:
		return nil, fmt.Errorf("expected document or mapping root, got kind %d", document.Kind)
	}
}

func ensureMappingValue(parent *yaml.Node, key string) *yaml.Node {
	value := mappingValue(parent, key)
	if value != nil {
		if value.Kind != yaml.MappingNode {
			value.Kind = yaml.MappingNode
			value.Tag = "!!map"
			value.Content = nil
		}
		return value
	}

	keyNode := &yaml.Node{Kind: yaml.ScalarNode, Tag: "!!str", Value: key}
	valueNode := &yaml.Node{Kind: yaml.MappingNode, Tag: "!!map"}
	parent.Content = append(parent.Content, keyNode, valueNode)
	return valueNode
}

func setScalarValue(parent *yaml.Node, key, tag, value string, style yaml.Style) {
	current := mappingValue(parent, key)
	if current == nil {
		keyNode := &yaml.Node{Kind: yaml.ScalarNode, Tag: "!!str", Value: key}
		current = &yaml.Node{}
		parent.Content = append(parent.Content, keyNode, current)
	}

	current.Kind = yaml.ScalarNode
	current.Tag = tag
	current.Style = style
	current.Value = value
	current.Content = nil
}

func mappingValue(parent *yaml.Node, key string) *yaml.Node {
	if parent.Kind != yaml.MappingNode {
		return nil
	}

	for i := 0; i+1 < len(parent.Content); i += 2 {
		if parent.Content[i].Kind == yaml.ScalarNode && parent.Content[i].Value == key {
			return parent.Content[i+1]
		}
	}

	return nil
}
