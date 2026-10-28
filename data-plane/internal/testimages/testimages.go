// Package testimages reads the Testcontainers images pinned in testing/images.properties, which the Kotlin
// tests share (D-0113).
package testimages

import (
	"bufio"
	"errors"
	"fmt"
	"os"
	"path/filepath"
	"strings"
)

const file = "testing/images.properties"

// Image returns the pinned image for name, e.g. "kafka", searching upwards from the working directory for
// the repository's testing/images.properties.
func Image(name string) (string, error) {
	path, err := locate()
	if err != nil {
		return "", err
	}
	images, err := read(path)
	if err != nil {
		return "", err
	}
	image, ok := images[name]
	if !ok {
		return "", fmt.Errorf("no image %q in %s", name, path)
	}
	return image, nil
}

func locate() (string, error) {
	dir, err := os.Getwd()
	if err != nil {
		return "", fmt.Errorf("working directory: %w", err)
	}
	for {
		candidate := filepath.Join(dir, file)
		if _, err := os.Stat(candidate); err == nil {
			return candidate, nil
		}
		parent := filepath.Dir(dir)
		if parent == dir {
			return "", errors.New(file + " not found above the working directory")
		}
		dir = parent
	}
}

// read parses key=value lines, skipping blanks and # comments.
func read(path string) (map[string]string, error) {
	f, err := os.Open(path) //nolint:gosec // G304: the path is the repository's own file, found by locate.
	if err != nil {
		return nil, fmt.Errorf("open %s: %w", path, err)
	}
	defer func() { _ = f.Close() }()
	images := map[string]string{}
	scanner := bufio.NewScanner(f)
	for scanner.Scan() {
		line := strings.TrimSpace(scanner.Text())
		if line == "" || strings.HasPrefix(line, "#") {
			continue
		}
		name, image, ok := strings.Cut(line, "=")
		if !ok {
			return nil, fmt.Errorf("%s: malformed line %q", path, line)
		}
		images[strings.TrimSpace(name)] = strings.TrimSpace(image)
	}
	if err := scanner.Err(); err != nil {
		return nil, fmt.Errorf("read %s: %w", path, err)
	}
	return images, nil
}
