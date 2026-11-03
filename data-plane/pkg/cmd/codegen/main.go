// Command codegen renders generated sources from the contract files in contracts/ (D-0057).
//
//	codegen topics -in contracts/kafka/topics.yaml -go-out … -kotlin-out … -strimzi-out …
//	codegen profiles -in contracts/runtime-profiles.yaml -go-out … -kotlin-out … -bake-out …
//	codegen livestate -in contracts/livestate/keys.yaml -go-out … -kotlin-out … -kotlin-kinds-out …
//	codegen constants -in contracts/pod-labels.yaml -go-package … -go-out … -kotlin-package … -kotlin-out …
//	codegen plugins -in contracts/runtime-profiles.yaml   (bumps the bundle plugin pins, D-0082)
//	codegen servers -in contracts/runtime-profiles.yaml   (bumps the server jar pins, D-0139)
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

	"github.com/jevzo/ogcloud/data-plane/pkg/contracts/constants"
	"github.com/jevzo/ogcloud/data-plane/pkg/contracts/livestate"
	"github.com/jevzo/ogcloud/data-plane/pkg/contracts/profiles"
	"github.com/jevzo/ogcloud/data-plane/pkg/contracts/topics"
)

const (
	generatedFileMode = 0o644
	generatedDirMode  = 0o750
	lookupTimeout     = time.Minute
)

func main() {
	if err := run(os.Args[1:]); err != nil {
		fmt.Fprintln(os.Stderr, "codegen:", err)
		os.Exit(1)
	}
}

func run(args []string) error {
	if len(args) == 0 {
		return errors.New("usage: codegen <topics|profiles|livestate|constants|plugins|servers> [flags]")
	}
	switch args[0] {
	case "topics":
		return runTopics(args[1:])
	case "profiles":
		return runProfiles(args[1:])
	case "livestate":
		return runLivestate(args[1:])
	case "constants":
		return runConstants(args[1:])
	case "plugins":
		return runPlugins(args[1:])
	case "servers":
		return runServers(args[1:])
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
	bakeOut := flags.String("bake-out", "", "docker buildx bake file for the server images (optional)")
	if err := flags.Parse(args); err != nil {
		return fmt.Errorf("parse flags: %w", err)
	}
	if *in == "" {
		return errors.New("profiles: -in is required")
	}
	catalog, err := load(*in, profiles.Parse)
	if err != nil {
		return err
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
		if err := write(*kotlinOut, profiles.RenderKotlin(*kotlinPackage, catalog)); err != nil {
			return err
		}
	}
	if *bakeOut != "" {
		bake, err := profiles.RenderBake(catalog)
		if err != nil {
			return err
		}
		return write(*bakeOut, bake)
	}
	return nil
}

func runLivestate(args []string) error {
	flags := flag.NewFlagSet("livestate", flag.ContinueOnError)
	in := flags.String("in", "", "path to keys.yaml")
	goOut := flags.String("go-out", "", "Go output file (optional)")
	goPackage := flags.String("go-package", "valkeykeys", "Go package name")
	kotlinOut := flags.String("kotlin-out", "", "Kotlin ValkeyKeys output file (optional)")
	kotlinPackage := flags.String("kotlin-package", "io.ogwars.cloud.livestate", "Kotlin package of ValkeyKeys")
	kindsOut := flags.String("kotlin-kinds-out", "", "Kotlin kind enums output file (optional)")
	kindsPackage := flags.String("kotlin-kinds-package", "io.ogwars.cloud.domain.store", "Kotlin package of the kind enums")
	if err := flags.Parse(args); err != nil {
		return fmt.Errorf("parse flags: %w", err)
	}
	if *in == "" {
		return errors.New("livestate: -in is required")
	}
	catalog, err := load(*in, livestate.Parse)
	if err != nil {
		return err
	}
	if *goOut != "" {
		source, err := livestate.RenderGo(*goPackage, catalog)
		if err != nil {
			return err
		}
		if err := write(*goOut, source); err != nil {
			return err
		}
	}
	if *kotlinOut != "" {
		source, err := livestate.RenderKotlin(*kotlinPackage, catalog)
		if err != nil {
			return err
		}
		if err := write(*kotlinOut, source); err != nil {
			return err
		}
	}
	if *kindsOut != "" {
		return write(*kindsOut, livestate.RenderKotlinKinds(*kindsPackage, catalog))
	}
	return nil
}

func runConstants(args []string) error {
	flags := flag.NewFlagSet("constants", flag.ContinueOnError)
	in := flags.String("in", "", "path to the constants contract")
	goOut := flags.String("go-out", "", "Go output file")
	goPackage := flags.String("go-package", "", "Go package name")
	kotlinOut := flags.String("kotlin-out", "", "Kotlin output file")
	kotlinPackage := flags.String("kotlin-package", "", "Kotlin package name")
	if err := flags.Parse(args); err != nil {
		return fmt.Errorf("parse flags: %w", err)
	}
	if *in == "" || *goOut == "" || *goPackage == "" || *kotlinOut == "" || *kotlinPackage == "" {
		return errors.New("constants: -in, -go-out, -go-package, -kotlin-out and -kotlin-package are required")
	}
	catalog, err := load(*in, constants.Parse)
	if err != nil {
		return err
	}
	source, err := constants.RenderGo(*goPackage, *in, catalog)
	if err != nil {
		return err
	}
	if err := write(*goOut, source); err != nil {
		return err
	}
	return write(*kotlinOut, constants.RenderKotlin(*kotlinPackage, *in, catalog))
}

// load reads a contract file, then parses and validates it.
func load[T interface{ Validate() error }](path string, parse func([]byte) (T, error)) (T, error) {
	var zero T
	//nolint:gosec // G304: contract paths come from the developer's own codegen invocation.
	data, err := os.ReadFile(path)
	if err != nil {
		return zero, fmt.Errorf("read %s: %w", path, err)
	}
	catalog, err := parse(data)
	if err != nil {
		return zero, fmt.Errorf("%s: %w", path, err)
	}
	if err := catalog.Validate(); err != nil {
		return zero, fmt.Errorf("%s: %w", path, err)
	}
	return catalog, nil
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
	source := profiles.ModrinthReleases{Client: lookupClient(), BaseURL: *api}
	return bumpPins("plugins", *in, func(ctx context.Context, data []byte) ([]byte, []profiles.Bump, error) {
		return profiles.UpdatePlugins(ctx, data, source)
	})
}

// runServers moves the server jar pins to the newest stable build of each profile's version (D-0139).
func runServers(args []string) error {
	flags := flag.NewFlagSet("servers", flag.ContinueOnError)
	in := flags.String("in", "", "path to runtime-profiles.yaml")
	fill := flags.String("fill-api", "https://fill.papermc.io", "PaperMC Fill API base URL")
	hpfxd := flags.String("hpfxd-api", "https://downloads.hpfxd.com", "hpfxd downloads API base URL")
	if err := flags.Parse(args); err != nil {
		return fmt.Errorf("parse flags: %w", err)
	}
	client := lookupClient()
	sources := map[string]profiles.ServerSource{
		"paper":       profiles.FillBuilds{Client: client, BaseURL: *fill, Project: "paper", Repository: "PaperMC/Paper"},
		"velocity":    profiles.FillBuilds{Client: client, BaseURL: *fill, Project: "velocity", Repository: "PaperMC/Velocity"},
		"pandaspigot": profiles.HpfxdBuilds{Client: client, BaseURL: *hpfxd},
	}
	return bumpPins("servers", *in, func(ctx context.Context, data []byte) ([]byte, []profiles.Bump, error) {
		return profiles.UpdateServers(ctx, data, sources)
	})
}

func lookupClient() *http.Client {
	return &http.Client{Timeout: lookupTimeout}
}

// bumpPins rewrites runtime-profiles.yaml with update's pins, if they still validate, and prints one line per bump.
func bumpPins(name, in string, update func(context.Context, []byte) ([]byte, []profiles.Bump, error)) error {
	if in == "" {
		return fmt.Errorf("%s: -in is required", name)
	}
	//nolint:gosec // G304: the catalog path comes from the developer's own codegen invocation.
	data, err := os.ReadFile(in)
	if err != nil {
		return fmt.Errorf("read %s: %w", in, err)
	}
	ctx, cancel := context.WithTimeout(context.Background(), lookupTimeout)
	defer cancel()
	updated, bumps, err := update(ctx, data)
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
	if err := write(in, updated); err != nil {
		return err
	}
	for _, bump := range bumps {
		fmt.Printf("%s %s -> %s\n", bump.ID, bump.From, bump.To)
	}
	return nil
}
