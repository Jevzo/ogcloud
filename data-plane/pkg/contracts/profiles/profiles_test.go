package profiles

import (
	"flag"
	"os"
	"path/filepath"
	"strings"
	"testing"
)

var update = flag.Bool("update", false, "rewrite golden files")

func TestRenderersMatchGoldenFiles(t *testing.T) {
	catalog := parseFixture(t)
	goSource, err := RenderGo(catalog)
	if err != nil {
		t.Fatalf("RenderGo: %v", err)
	}
	tests := []struct {
		golden string
		got    []byte
	}{
		{"catalog.go.golden", goSource},
		{"RuntimeProfiles.kt.golden", RenderKotlin("io.ogwars.cloud.domain.runtime", catalog)},
	}
	for _, tt := range tests {
		t.Run(tt.golden, func(t *testing.T) {
			path := filepath.Join("testdata", tt.golden)
			if *update {
				if err := os.WriteFile(path, tt.got, 0o600); err != nil {
					t.Fatalf("write golden: %v", err)
				}
			}
			want, err := os.ReadFile(path)
			if err != nil {
				t.Fatalf("read golden: %v", err)
			}
			if string(tt.got) != string(want) {
				t.Errorf("output differs from %s; rerun with -update after reviewing\n%s", path, tt.got)
			}
		})
	}
}

func TestValidateRejectsInvalidCatalogs(t *testing.T) {
	const header = "forwarding: bungeeguard\nclientProtocols: {min: 47, max: 777}\nprofiles:\n"
	const proxy = "  - {id: velocity-4.3.0, platform: velocity, version: 4.3.0, java: 25}\n"
	const paper = "  - {id: paper-26.3, platform: paper, version: '26.3', protocol: 777, java: 25}\n"
	tests := []struct {
		name    string
		yaml    string
		wantErr string
	}{
		{"modern forwarding", "forwarding: modern\nclientProtocols: {min: 47, max: 777}\nprofiles:\n" + proxy + paper, "D-0065"},
		{"bad range", "forwarding: bungeeguard\nclientProtocols: {min: 777, max: 47}\nprofiles:\n" + proxy + paper, "positive range"},
		{"unknown platform", header + proxy + "  - {id: spigot-1.8, platform: spigot, version: '1.8', protocol: 47, java: 25}\n", "unknown platform"},
		{"duplicate", header + proxy + paper + paper, "duplicate id"},
		{"bad id", header + proxy + "  - {id: Paper, platform: paper, version: '26.3', protocol: 777, java: 25}\n", "id must match"},
		{"proxy with protocol", header + "  - {id: velocity-4.3.0, platform: velocity, version: 4.3.0, protocol: 777, java: 25}\n" + paper, "omit protocol"},
		{"protocol out of range", header + proxy + "  - {id: paper-27.1, platform: paper, version: '27.1', protocol: 800, java: 25}\n", "outside clientProtocols"},
		{"missing java", header + proxy + "  - {id: paper-26.3, platform: paper, version: '26.3', protocol: 777}\n", "java are required"},
		{"no proxy", header + paper, "at least one proxy"},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			catalog, err := Parse([]byte(tt.yaml))
			if err != nil {
				t.Fatalf("Parse: %v", err)
			}
			if err := catalog.Validate(); err == nil || !strings.Contains(err.Error(), tt.wantErr) {
				t.Errorf("Validate() = %v, want error containing %q", err, tt.wantErr)
			}
		})
	}
}

func parseFixture(t *testing.T) Catalog {
	t.Helper()
	data, err := os.ReadFile(filepath.Join("testdata", "runtime-profiles.yaml"))
	if err != nil {
		t.Fatalf("read fixture: %v", err)
	}
	catalog, err := Parse(data)
	if err != nil {
		t.Fatalf("Parse: %v", err)
	}
	if err := catalog.Validate(); err != nil {
		t.Fatalf("Validate: %v", err)
	}
	return catalog
}
