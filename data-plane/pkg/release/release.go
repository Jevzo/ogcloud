// Package release turns the targets `docker buildx bake --print` shows and the metadata each architecture's build
// writes into what the release workflow publishes: images pushed by digest, then one manifest list per tag (D-0194,
// D-0197).
package release

import (
	"encoding/json"
	"errors"
	"fmt"
	"slices"
	"strings"
)

// Bake is the part of `docker buildx bake --print` the release reads.
type Bake struct {
	Target map[string]BakeTarget `json:"target"`
}

// BakeTarget is one image of the bake file.
type BakeTarget struct {
	Tags []string `json:"tags"`
}

// Build is one target's entry in the file `docker buildx bake --metadata-file` writes.
type Build struct {
	Name   string `json:"image.name"`
	Digest string `json:"containerimage.digest"`
}

// Metadata holds one architecture's builds by target; bake adds keys of its own, which are skipped.
type Metadata map[string]json.RawMessage

// Manifest is one manifest list to create: the tag, its repository and each architecture's image.
type Manifest struct {
	Tag        string
	Repository string
	Sources    []string
}

// PushArgs returns the bake arguments that push every target by digest to its tag's repository: a pushed tag would
// name a single architecture, and the manifest lists take the tags afterwards.
func PushArgs(bake Bake) ([]string, error) {
	repositories, err := Repositories(bake)
	if err != nil {
		return nil, err
	}
	var args []string
	for index, name := range targetNames(bake) {
		args = append(args,
			"--set", name+".tags=",
			"--set", name+".output=type=image,name="+repositories[index]+",push-by-digest=true,push=true")
	}
	return args, nil
}

// Repositories returns every target's repository, sorted: the packages a registry holds for the bake file.
func Repositories(bake Bake) ([]string, error) {
	var repositories []string
	for _, name := range targetNames(bake) {
		_, repository, err := onlyTag(name, bake.Target[name])
		if err != nil {
			return nil, err
		}
		repositories = append(repositories, repository)
	}
	if len(repositories) == 0 {
		return nil, errors.New("bake printed no targets")
	}
	return repositories, nil
}

// Manifests pairs every target's tag with the image each architecture pushed for it.
func Manifests(bake Bake, architectures []Metadata) ([]Manifest, error) {
	if len(architectures) == 0 {
		return nil, errors.New("no build metadata given")
	}
	var manifests []Manifest
	for _, name := range targetNames(bake) {
		tag, repository, err := onlyTag(name, bake.Target[name])
		if err != nil {
			return nil, err
		}
		manifest := Manifest{Tag: tag, Repository: repository}
		for index, metadata := range architectures {
			build, err := metadata.build(name)
			if err != nil {
				return nil, fmt.Errorf("metadata %d: %w", index+1, err)
			}
			if build.Name != repository {
				return nil, fmt.Errorf("metadata %d: target %s was pushed to %q, not %q", index+1, name, build.Name, repository)
			}
			manifest.Sources = append(manifest.Sources, repository+"@"+build.Digest)
		}
		manifests = append(manifests, manifest)
	}
	if len(manifests) == 0 {
		return nil, errors.New("bake printed no targets")
	}
	return manifests, nil
}

func (m Metadata) build(target string) (Build, error) {
	raw, ok := m[target]
	if !ok {
		return Build{}, fmt.Errorf("no build of target %s", target)
	}
	var build Build
	if err := json.Unmarshal(raw, &build); err != nil {
		return Build{}, fmt.Errorf("target %s: %w", target, err)
	}
	if !strings.HasPrefix(build.Digest, "sha256:") {
		return Build{}, fmt.Errorf("target %s has no image digest", target)
	}
	return build, nil
}

func targetNames(bake Bake) []string {
	names := make([]string, 0, len(bake.Target))
	for name := range bake.Target {
		names = append(names, name)
	}
	slices.Sort(names)
	return names
}

// onlyTag returns a target's one tag and its repository: the tag becomes the manifest list, so it must be unique.
func onlyTag(name string, target BakeTarget) (tag, repository string, err error) {
	if len(target.Tags) != 1 {
		return "", "", fmt.Errorf("target %s has %d tags, want 1", name, len(target.Tags))
	}
	tag = target.Tags[0]
	// A registry may carry a port, so the tag starts after the last slash.
	slash := strings.LastIndex(tag, "/")
	colon := strings.LastIndex(tag, ":")
	if colon <= slash || colon == len(tag)-1 || strings.Contains(tag, "@") {
		return "", "", fmt.Errorf("target %s: %q is not a tagged image reference", name, tag)
	}
	return tag, tag[:colon], nil
}
