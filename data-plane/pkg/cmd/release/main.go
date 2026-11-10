// Command release reads `docker buildx bake --print` on stdin and prints what the release workflow publishes (D-0197):
//
//	release push-args                     the bake arguments that push every target by digest, one per line
//	release repositories                  every target's repository, one per line
//	release manifests <metadata.json>...  one line per manifest list: tag, repository, each architecture's image
package main

import (
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"os"
	"strings"

	"github.com/jevzo/ogcloud/data-plane/pkg/release"
)

const usage = "usage: release push-args | repositories | manifests <metadata.json>... (bake --print on stdin)"

func main() {
	if err := run(os.Args[1:], os.Stdin, os.Stdout); err != nil {
		fmt.Fprintln(os.Stderr, "release:", err)
		os.Exit(1)
	}
}

func run(args []string, stdin io.Reader, stdout io.Writer) error {
	if len(args) == 0 {
		return errors.New(usage)
	}
	var bake release.Bake
	if err := json.NewDecoder(stdin).Decode(&bake); err != nil {
		return fmt.Errorf("read bake --print: %w", err)
	}
	var lines []string
	switch args[0] {
	case "push-args":
		if len(args) != 1 {
			return errors.New(usage)
		}
		pushArgs, err := release.PushArgs(bake)
		if err != nil {
			return err
		}
		lines = pushArgs
	case "repositories":
		if len(args) != 1 {
			return errors.New(usage)
		}
		repositories, err := release.Repositories(bake)
		if err != nil {
			return err
		}
		lines = repositories
	case "manifests":
		architectures, err := readMetadata(args[1:])
		if err != nil {
			return err
		}
		manifests, err := release.Manifests(bake, architectures)
		if err != nil {
			return err
		}
		for _, manifest := range manifests {
			lines = append(lines, strings.Join(append([]string{manifest.Tag, manifest.Repository}, manifest.Sources...), " "))
		}
	default:
		return errors.New(usage)
	}
	for _, line := range lines {
		if _, err := fmt.Fprintln(stdout, line); err != nil {
			return fmt.Errorf("write: %w", err)
		}
	}
	return nil
}

func readMetadata(paths []string) ([]release.Metadata, error) {
	architectures := make([]release.Metadata, 0, len(paths))
	for _, path := range paths {
		data, err := os.ReadFile(path) //nolint:gosec // G304: the workflow passes the metadata files its builds wrote.
		if err != nil {
			return nil, fmt.Errorf("read %s: %w", path, err)
		}
		var metadata release.Metadata
		if err := json.Unmarshal(data, &metadata); err != nil {
			return nil, fmt.Errorf("parse %s: %w", path, err)
		}
		architectures = append(architectures, metadata)
	}
	return architectures, nil
}
