// Command codegen renders generated sources from the contract files in contracts/ (D-0057).
//
//	codegen topics -in contracts/kafka/topics.yaml -go-out … -kotlin-out … -strimzi-out …
//	codegen profiles -in contracts/runtime-profiles.yaml -go-out … -kotlin-out …
//	codegen plugins -in contracts/runtime-profiles.yaml   (bumps the bundle plugin pins, D-0082)
package main

import (
	"context"
	"errors"
	"flag"
	"fmt"
	"net/http"
	"os"
	"path/filepath"
	"time"

	"github.com/jevzo/ogcloud/data-plane/pkg/contracts/profiles"
	"github.com/jevzo/ogcloud/data-plane/pkg/contracts/topics"
)

const (
	generatedFileMode   = 0o644
	generatedDirMode    = 0o750
	pluginLookupTimeout = time.Minute
)

func main() {
	if err := run(os.Args[1:]); err != nil {
		fmt.Fprintln(os.Stderr, "codegen:", err)
		os.Exit(1)
	}
}

func run(args []string) error {
	if len(args) == 0 {
		return errors.New("usage: codegen <topics|profiles|plugins> [flags]")
	}
	switch args[0] {
	case "topics":
		return runTopics(args[1:])
	case "profiles":
		return runProfiles(args[1:])
	case "plugins":
		return runPlugins(args[1:])
	default:
		return fmt.Errorf("unknown generator %q", args[0])
	}
}

func runTopics(args []string) error {
	flags := flag.NewFlagSet("topics", flag.ContinueOnError)
	in := flags.String("in", "", "path to topics.yaml")
	goOut := flags.String("go-out", "", "Go output file (optional)")
	goPackage := flags.String("go-package", "topics", "Go package name")
	kotlinOut := flags.String("kotlin-out", "", "Kotlin output file (optional)")
	kotlinPackage := flags.String("kotlin-package", "io.ogwars.cloud.messaging", "Kotlin package name")
	strimziOut := flags.String("strimzi-out", "", "Strimzi Helm template output file (optional)")
	if err := flags.Parse(args); err != nil {
		return fmt.Errorf("parse flags: %w", err)
	}
	if *in == "" {
		return errors.New("topics: -in is required")
	}

	data, err := os.ReadFile(*in)
	if err != nil {
		return fmt.Errorf("read %s: %w", *in, err)
	}
	catalog, err := topics.Parse(data)
	if err != nil {
		return fmt.Errorf("%s: %w", *in, err)
	}
	resolved, err := catalog.Resolve()
	if err != nil {
		return fmt.Errorf("%s: %w", *in, err)
	}

	if *goOut != "" {
		source, err := topics.RenderGo(*goPackage, resolved)
		if err != nil {
			return err
		}
		if err := write(*goOut, source); err != nil {
			return err
		}
	}
	if *kotlinOut != "" {
		if err := write(*kotlinOut, topics.RenderKotlin(*kotlinPackage, resolved)); err != nil {
			return err
		}
	}
	if *strimziOut != "" {
		if err := write(*strimziOut, topics.RenderStrimzi(resolved)); err != nil {
			return err
		}
	}
	return nil
}

func runProfiles(args []string) error {
	flags := flag.NewFlagSet("profiles", flag.ContinueOnError)
	in := flags.String("in", "", "path to runtime-profiles.yaml")
	goOut := flags.String("go-out", "", "Go output file (optional)")
	kotlinOut := flags.String("kotlin-out", "", "Kotlin output file (optional)")
	kotlinPackage := flags.String("kotlin-package", "io.ogwars.cloud.domain.runtime", "Kotlin package name")
	if err := flags.Parse(args); err != nil {
		return fmt.Errorf("parse flags: %w", err)
	}
	if *in == "" {
		return errors.New("profiles: -in is required")
	}
	data, err := os.ReadFile(*in)
	if err != nil {
		return fmt.Errorf("read %s: %w", *in, err)
	}
	catalog, err := profiles.Parse(data)
	if err != nil {
		return fmt.Errorf("%s: %w", *in, err)
	}
	if err := catalog.Validate(); err != nil {
		return fmt.Errorf("%s: %w", *in, err)
	}
	if *goOut != "" {
		source, err := profiles.RenderGo(catalog)
		if err != nil {
			return err
		}
		if err := write(*goOut, source); err != nil {
			return err
		}
	}
	if *kotlinOut != "" {
		return write(*kotlinOut, profiles.RenderKotlin(*kotlinPackage, catalog))
	}
	return nil
}

func write(path string, content []byte) error {
	if err := os.MkdirAll(filepath.Dir(path), generatedDirMode); err != nil {
		return fmt.Errorf("create directory for %s: %w", path, err)
	}
	//nolint:gosec // G703: output paths come from the developer's own codegen invocation.
	if err := os.WriteFile(path, content, generatedFileMode); err != nil {
		return fmt.Errorf("write %s: %w", path, err)
	}
	return nil
}

// runPlugins moves the bundle plugin pins to their newest stable Modrinth releases and prints one line per bump.
func runPlugins(args []string) error {
	flags := flag.NewFlagSet("plugins", flag.ContinueOnError)
	in := flags.String("in", "", "path to runtime-profiles.yaml")
	api := flags.String("api", "https://api.modrinth.com", "Modrinth API base URL")
	if err := flags.Parse(args); err != nil {
		return fmt.Errorf("parse flags: %w", err)
	}
	if *in == "" {
		return errors.New("plugins: -in is required")
	}
	data, err := os.ReadFile(*in)
	if err != nil {
		return fmt.Errorf("read %s: %w", *in, err)
	}
	ctx, cancel := context.WithTimeout(context.Background(), pluginLookupTimeout)
	defer cancel()
	updated, bumps, err := profiles.UpdatePlugins(ctx, data, profiles.ModrinthReleases{Client: &http.Client{Timeout: pluginLookupTimeout}, BaseURL: *api})
	if err != nil {
		return err
	}
	if len(bumps) == 0 {
		return nil
	}
	catalog, err := profiles.Parse(updated)
	if err != nil {
		return err
	}
	if err := catalog.Validate(); err != nil {
		return fmt.Errorf("bumped catalog is invalid: %w", err)
	}
	if err := write(*in, updated); err != nil {
		return err
	}
	for _, bump := range bumps {
		fmt.Printf("%s %s -> %s\n", bump.ID, bump.From, bump.To)
	}
	return nil
}
