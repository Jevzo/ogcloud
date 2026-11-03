package templateloader

import (
	"bytes"
	"errors"
	"fmt"
	"io/fs"
	"os"
	"strings"

	"go.yaml.in/yaml/v3"

	"github.com/jevzo/ogcloud/data-plane/pkg/runtimeprofiles"
)

const yamlIndent = 2

// applyOverlays writes a platform's settings into the server files on every start (D-0119); a missing
// file is created with just those settings.
func applyOverlays(root *os.Root, overlays []runtimeprofiles.Overlay) error {
	byFile := map[string][]runtimeprofiles.Overlay{}
	var files []string
	for _, overlay := range overlays {
		if _, seen := byFile[overlay.File]; !seen {
			files = append(files, overlay.File)
		}
		byFile[overlay.File] = append(byFile[overlay.File], overlay)
	}
	for _, file := range files {
		content, err := root.ReadFile(file)
		if err != nil && !errors.Is(err, fs.ErrNotExist) {
			return fmt.Errorf("read %s: %w", file, err)
		}
		var patched []byte
		switch byFile[file][0].Format {
		case runtimeprofiles.OverlayProperties:
			patched = setProperties(content, byFile[file])
		case runtimeprofiles.OverlayYAML:
			if patched, err = setYAML(content, byFile[file]); err != nil {
				return fmt.Errorf("%s: %w", file, err)
			}
		default:
			return fmt.Errorf("%s: unknown overlay format %q", file, byFile[file][0].Format)
		}
		if err := writeFile(root, file, bytes.NewReader(patched)); err != nil {
			return err
		}
	}
	return nil
}

// setProperties replaces each key's line in place, keeping comments, order and the file's line endings;
// keys the file lacks are appended.
func setProperties(content []byte, overlays []runtimeprofiles.Overlay) []byte {
	newline := "\n"
	if bytes.Contains(content, []byte("\r\n")) {
		newline = "\r\n"
	}
	text := strings.TrimSuffix(strings.ReplaceAll(string(content), "\r\n", "\n"), "\n")
	var lines []string
	if text != "" {
		lines = strings.Split(text, "\n")
	}
	for _, overlay := range overlays {
		line := overlay.Key + "=" + overlay.Value
		found := false
		for i, existing := range lines {
			if propertyKey(existing) == overlay.Key {
				lines[i], found = line, true
			}
		}
		if !found {
			lines = append(lines, line)
		}
	}
	return []byte(strings.Join(lines, newline) + newline)
}

// propertyKey returns the key of a key=value or key:value line, or "" for comments and blank lines.
func propertyKey(line string) string {
	trimmed := strings.TrimSpace(line)
	if trimmed == "" || strings.HasPrefix(trimmed, "#") || strings.HasPrefix(trimmed, "!") {
		return ""
	}
	if end := strings.IndexAny(trimmed, "=:"); end >= 0 {
		return strings.TrimSpace(trimmed[:end])
	}
	return trimmed
}

// setYAML sets each dotted key, creating the mappings on the way, and keeps the rest of the document.
func setYAML(content []byte, overlays []runtimeprofiles.Overlay) ([]byte, error) {
	var document yaml.Node
	if err := yaml.Unmarshal(content, &document); err != nil {
		return nil, fmt.Errorf("parse YAML: %w", err)
	}
	if document.Kind == 0 {
		document = yaml.Node{Kind: yaml.DocumentNode, Content: []*yaml.Node{{Kind: yaml.MappingNode}}}
	}
	if document.Content[0].Kind != yaml.MappingNode {
		return nil, errors.New("the document is not a mapping")
	}
	for _, overlay := range overlays {
		if err := setPath(document.Content[0], strings.Split(overlay.Key, "."), overlay.Value); err != nil {
			return nil, fmt.Errorf("%s: %w", overlay.Key, err)
		}
	}
	var out bytes.Buffer
	encoder := yaml.NewEncoder(&out)
	encoder.SetIndent(yamlIndent)
	if err := encoder.Encode(&document); err != nil {
		return nil, fmt.Errorf("write YAML: %w", err)
	}
	if err := encoder.Close(); err != nil {
		return nil, fmt.Errorf("write YAML: %w", err)
	}
	return out.Bytes(), nil
}

// setPath sets keys under mapping. Every duplicate of a key is set, since YAML readers differ in which one
// they keep.
func setPath(mapping *yaml.Node, keys []string, value string) error {
	found := false
	for i := 0; i < len(mapping.Content); i += 2 {
		if mapping.Content[i].Value != keys[0] {
			continue
		}
		found = true
		child := mapping.Content[i+1]
		if len(keys) == 1 {
			mapping.Content[i+1] = scalar(value)
			continue
		}
		if child.Kind != yaml.MappingNode {
			return fmt.Errorf("%s is not a mapping", keys[0])
		}
		if err := setPath(child, keys[1:], value); err != nil {
			return err
		}
	}
	if found {
		return nil
	}
	if len(keys) == 1 {
		mapping.Content = append(mapping.Content, scalar(keys[0]), scalar(value))
		return nil
	}
	child := &yaml.Node{Kind: yaml.MappingNode}
	mapping.Content = append(mapping.Content, scalar(keys[0]), child)
	return setPath(child, keys[1:], value)
}

// scalar leaves the tag to YAML, so "true" is a boolean and "25565" a number.
func scalar(value string) *yaml.Node { return &yaml.Node{Kind: yaml.ScalarNode, Value: value} }
