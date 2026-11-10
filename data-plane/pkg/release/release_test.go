package release

import (
	"encoding/json"
	"slices"
	"strings"
	"testing"
)

const (
	amd64Digest = "sha256:3c67e7dddfd1530f33162adc0cadc8e3c340a2b93d44e25a6e5e196b9117d9be"
	arm64Digest = "sha256:009a05ab409e6adbfe6a4cb974a9b6b997c68709ddd8ca58f44cf528d9846d4a"
)

func bake(tags map[string][]string) Bake {
	targets := map[string]BakeTarget{}
	for name, tagList := range tags {
		targets[name] = BakeTarget{Tags: tagList}
	}
	return Bake{Target: targets}
}

// metadata builds a metadata file as bake writes it, including a key of bake's own.
func metadata(t *testing.T, builds map[string]Build) Metadata {
	t.Helper()
	file := map[string]any{"buildx.build.warnings": []string{"a warning"}}
	for name, build := range builds {
		file[name] = build
	}
	data, err := json.Marshal(file)
	if err != nil {
		t.Fatal(err)
	}
	var parsed Metadata
	if err := json.Unmarshal(data, &parsed); err != nil {
		t.Fatal(err)
	}
	return parsed
}

func TestPushArgs(t *testing.T) {
	tests := []struct {
		name    string
		bake    Bake
		want    []string
		wantErr string
	}{
		{
			name: "every target by digest, sorted",
			bake: bake(map[string][]string{
				"paper-26_3": {"ghcr.io/jevzo/ogcloud/paper:26.3-2.0.0-alpha.1"},
				"api":        {"ghcr.io/jevzo/ogcloud/api:2.0.0-alpha.1"},
			}),
			want: []string{
				"--set", "api.tags=",
				"--set", "api.output=type=image,name=ghcr.io/jevzo/ogcloud/api,push-by-digest=true,push=true",
				"--set", "paper-26_3.tags=",
				"--set", "paper-26_3.output=type=image,name=ghcr.io/jevzo/ogcloud/paper,push-by-digest=true,push=true",
			},
		},
		{
			name: "registry with a port",
			bake: bake(map[string][]string{"gateway": {"localhost:5000/ogcloud/gateway:dev"}}),
			want: []string{
				"--set", "gateway.tags=",
				"--set", "gateway.output=type=image,name=localhost:5000/ogcloud/gateway,push-by-digest=true,push=true",
			},
		},
		{name: "no targets", bake: bake(nil), wantErr: "no targets"},
		{name: "two tags", bake: bake(map[string][]string{"api": {"r/api:1", "r/api:latest"}}), wantErr: "2 tags"},
		{name: "untagged", bake: bake(map[string][]string{"api": {"localhost:5000/api"}}), wantErr: "not a tagged"},
		{name: "empty tag", bake: bake(map[string][]string{"api": {"r/api:"}}), wantErr: "not a tagged"},
		{name: "digest reference", bake: bake(map[string][]string{"api": {"r/api@sha256:00"}}), wantErr: "not a tagged"},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			got, err := PushArgs(tt.bake)
			if tt.wantErr != "" {
				if err == nil || !strings.Contains(err.Error(), tt.wantErr) {
					t.Fatalf("PushArgs() error = %v, want one containing %q", err, tt.wantErr)
				}
				return
			}
			if err != nil {
				t.Fatalf("PushArgs() error = %v", err)
			}
			if !slices.Equal(got, tt.want) {
				t.Errorf("PushArgs() =\n%q\nwant\n%q", got, tt.want)
			}
		})
	}
}

func TestRepositories(t *testing.T) {
	got, err := Repositories(bake(map[string][]string{
		"velocity-4_3_0": {"ghcr.io/jevzo/ogcloud/velocity:4.3.0-2.0.0"},
		"api":            {"ghcr.io/jevzo/ogcloud/api:2.0.0"},
	}))
	if err != nil {
		t.Fatalf("Repositories() error = %v", err)
	}
	if want := []string{"ghcr.io/jevzo/ogcloud/api", "ghcr.io/jevzo/ogcloud/velocity"}; !slices.Equal(got, want) {
		t.Errorf("Repositories() = %q, want %q", got, want)
	}
	if _, err := Repositories(bake(nil)); err == nil {
		t.Error("Repositories() of no targets succeeded")
	}
}

func TestManifests(t *testing.T) {
	images := bake(map[string][]string{
		"gateway":    {"ghcr.io/jevzo/ogcloud/gateway:2.0.0-alpha.1"},
		"paper-26_3": {"ghcr.io/jevzo/ogcloud/paper:26.3-2.0.0-alpha.1"},
	})
	pushed := func(digest string) map[string]Build {
		return map[string]Build{
			"gateway":    {Name: "ghcr.io/jevzo/ogcloud/gateway", Digest: digest},
			"paper-26_3": {Name: "ghcr.io/jevzo/ogcloud/paper", Digest: digest},
		}
	}
	tests := []struct {
		name    string
		builds  []map[string]Build
		want    []Manifest
		wantErr string
	}{
		{
			name:   "one list per tag from every architecture",
			builds: []map[string]Build{pushed(amd64Digest), pushed(arm64Digest)},
			want: []Manifest{
				{
					Tag:        "ghcr.io/jevzo/ogcloud/gateway:2.0.0-alpha.1",
					Repository: "ghcr.io/jevzo/ogcloud/gateway",
					Sources: []string{
						"ghcr.io/jevzo/ogcloud/gateway@" + amd64Digest,
						"ghcr.io/jevzo/ogcloud/gateway@" + arm64Digest,
					},
				},
				{
					Tag:        "ghcr.io/jevzo/ogcloud/paper:26.3-2.0.0-alpha.1",
					Repository: "ghcr.io/jevzo/ogcloud/paper",
					Sources: []string{
						"ghcr.io/jevzo/ogcloud/paper@" + amd64Digest,
						"ghcr.io/jevzo/ogcloud/paper@" + arm64Digest,
					},
				},
			},
		},
		{name: "no metadata", wantErr: "no build metadata"},
		{
			name:    "an architecture missed a target",
			builds:  []map[string]Build{pushed(amd64Digest), {"gateway": {Name: "ghcr.io/jevzo/ogcloud/gateway", Digest: arm64Digest}}},
			wantErr: "metadata 2: no build of target paper-26_3",
		},
		{
			name: "a build without a digest",
			builds: []map[string]Build{{
				"gateway":    {Name: "ghcr.io/jevzo/ogcloud/gateway"},
				"paper-26_3": {Name: "ghcr.io/jevzo/ogcloud/paper", Digest: amd64Digest},
			}},
			wantErr: "target gateway has no image digest",
		},
		{
			name: "a build pushed elsewhere",
			builds: []map[string]Build{{
				"gateway":    {Name: "ghcr.io/someone/gateway", Digest: amd64Digest},
				"paper-26_3": {Name: "ghcr.io/jevzo/ogcloud/paper", Digest: amd64Digest},
			}},
			wantErr: `pushed to "ghcr.io/someone/gateway"`,
		},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			var architectures []Metadata
			for _, builds := range tt.builds {
				architectures = append(architectures, metadata(t, builds))
			}
			got, err := Manifests(images, architectures)
			if tt.wantErr != "" {
				if err == nil || !strings.Contains(err.Error(), tt.wantErr) {
					t.Fatalf("Manifests() error = %v, want one containing %q", err, tt.wantErr)
				}
				return
			}
			if err != nil {
				t.Fatalf("Manifests() error = %v", err)
			}
			if len(got) != len(tt.want) {
				t.Fatalf("Manifests() = %+v, want %+v", got, tt.want)
			}
			for i := range got {
				if got[i].Tag != tt.want[i].Tag || got[i].Repository != tt.want[i].Repository ||
					!slices.Equal(got[i].Sources, tt.want[i].Sources) {
					t.Errorf("Manifests()[%d] = %+v, want %+v", i, got[i], tt.want[i])
				}
			}
		})
	}
}
