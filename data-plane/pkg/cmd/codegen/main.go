// Command codegen renders generated sources from the contract files in contracts/ (D-0057).
//
//	codegen topics -in contracts/kafka/topics.yaml -go-out … -kotlin-out … -strimzi-out …
//	codegen profiles -in contracts/runtime-profiles.yaml -go-out … -kotlin-out …
package main

import (
	"errors"
	"flag"
	"fmt"
	"os"
	"path/filepath"

	"github.com/jevzo/ogcloud/data-plane/pkg/contracts/profiles"
	"github.com/jevzo/ogcloud/data-plane/pkg/contracts/topics"
)

const (
	generatedFileMode = 0o644
	generatedDirMode  = 0o750
)

func main() {
	if err := run(os.Args[1:]); err != nil {
		fmt.Fprintln(os.Stderr, "codegen:", err)
		os.Exit(1)
	}
}

func run(args []string) error {
	if len(args) == 0 {
		return errors.New("usage: codegen <topics|profiles> [flags]")
	}
	switch args[0] {
	case "topics":
		return runTopics(args[1:])
	case "profiles":
		return runProfiles(args[1:])
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
