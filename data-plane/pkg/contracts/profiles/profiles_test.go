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
	bake, err := RenderBake(catalog)
	if err != nil {
		t.Fatalf("RenderBake: %v", err)
	}
	tests := []struct {
		golden string
		got    []byte
	}{
		{"catalog.go.golden", goSource},
		{"RuntimeProfiles.kt.golden", RenderKotlin("io.ogwars.cloud.domain.runtime", catalog)},
		{"docker-bake.json.golden", bake},
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
	const base = "forwarding: bungeeguard\nclientProtocols: {min: 47, max: 777}\nserverPort: 25565\n"
	const header = base + "jvmFlags: {paper: [-XX:+UseG1GC], velocity: [-XX:+UseG1GC]}\nprofiles:\n"
	proxy := "  - {id: velocity-4.3.0, platform: velocity, version: 4.3.0, java: 25, server: " + server(fill, sha256Hex) + "}\n"
	paper := "  - {id: paper-26.3, platform: paper, version: '26.3', protocol: 777, java: 25, server: " + server(fill, sha256Hex) + "}\n"
	tests := []struct {
		name    string
		yaml    string
		wantErr string
	}{
		{"modern forwarding", "forwarding: modern\nclientProtocols: {min: 47, max: 777}\nserverPort: 25565\nprofiles:\n" + proxy + paper, "D-0065"},
		{"bad range", "forwarding: bungeeguard\nclientProtocols: {min: 777, max: 47}\nserverPort: 25565\nprofiles:\n" + proxy + paper, "positive range"},
		{"no port", "forwarding: bungeeguard\nclientProtocols: {min: 47, max: 777}\nprofiles:\n" + proxy + paper, "serverPort"},
		{"unknown platform", header + proxy + "  - {id: spigot-1.8, platform: spigot, version: '1.8', protocol: 47, java: 25}\n", "unknown platform"},
		{"duplicate", header + proxy + paper + paper, "duplicate id"},
		{"bad id", header + proxy + "  - {id: Paper, platform: paper, version: '26.3', protocol: 777, java: 25}\n", "id must match"},
		{"proxy with protocol", header + "  - {id: velocity-4.3.0, platform: velocity, version: 4.3.0, protocol: 777, java: 25}\n" + paper, "omit protocol"},
		{"protocol out of range", header + proxy + "  - {id: paper-27.1, platform: paper, version: '27.1', protocol: 800, java: 25}\n", "outside clientProtocols"},
		{"missing java", header + proxy + "  - {id: paper-26.3, platform: paper, version: '26.3', protocol: 777}\n", "java are required"},
		{"no proxy", header + paper, "at least one proxy"},
		{"plugin from elsewhere", header + proxy + paper + plugin("velocity", "ViaVersion.jar", "https://example.com/v.jar", strings.Repeat("a", 128)), "url are required"},
		{"plugin bad hash", header + proxy + paper + plugin("velocity", "ViaVersion.jar", modrinth, "abc"), "sha512"},
		{"plugin unknown platform", header + proxy + paper + plugin("spigot", "ViaVersion.jar", modrinth, strings.Repeat("a", 128)), "unknown platform"},
		{"plugin path", header + proxy + paper + plugin("velocity", "../ViaVersion.jar", modrinth, strings.Repeat("a", 128)), "plain .jar"},
		{"overlay escaping", header + proxy + paper + overlay("paper", "../server.properties", "properties", "online-mode"), "relative path"},
		{"overlay format", header + proxy + paper + overlay("paper", "server.toml", "toml", "online-mode"), "properties or yaml"},
		{"overlay key", header + proxy + paper + overlay("paper", "spigot.yml", "yaml", "settings..bungeecord"), "invalid key"},
		{"overlay platform", header + proxy + paper + overlay("spigot", "spigot.yml", "yaml", "a"), "unknown platform"},
		{"server from elsewhere", header + proxy + "  - {id: paper-26.3, platform: paper, version: '26.3', protocol: 777, java: 25, server: " + server("https://example.com/paper.jar", sha256Hex) + "}\n", "server.url"},
		{"server bad hash", header + proxy + "  - {id: paper-26.3, platform: paper, version: '26.3', protocol: 777, java: 25, server: " + server(fill, "abc") + "}\n", "server.sha256"},
		{"no server", header + proxy + "  - {id: paper-26.3, platform: paper, version: '26.3', protocol: 777, java: 25}\n", "server.build"},
		{"no flags", base + "profiles:\n" + proxy + paper, "jvmFlags has no flags"},
		{"flag with a space", base + "jvmFlags: {paper: ['-Xmx1G -Xms1G'], velocity: [-XX:+UseG1GC]}\nprofiles:\n" + proxy + paper, "one plain flag"},
		{"minimum memory unit", header + proxy + paper + "minimumMemory: {paper: 1.5G}\n", "whole Ki, Mi or Gi"},
		{"minimum memory platform", header + proxy + paper + "minimumMemory: {spigot: 1Gi}\n", "minimumMemory: unknown platform"},
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

const (
	modrinth  = "https://cdn.modrinth.com/data/P1OZGk5p/versions/5WgnasGG/ViaVersion-5.12.1.jar"
	fill      = "https://fill-data.papermc.io/v1/objects/x/paper-26.3-238.jar"
	sha256Hex = "989f93c29d5b61fe52804f834ea6b3a0a5775d05412e238ec78107197a6b5be6"
)

func server(url, sha string) string {
	return "{build: 238, url: '" + url + "', sha256: '" + sha + "', source: 'https://github.com/PaperMC/Paper/commit/abc'}"
}

func plugin(platform, file, url, sha string) string {
	return "plugins:\n  - {id: viaversion, platforms: [" + platform + "], file: '" + file + "', version: 5.12.1, url: '" + url + "', sha512: '" + sha + "'}\n"
}

func overlay(platform, file, format, key string) string {
	return "overlays:\n  - {platform: " + platform + ", file: '" + file + "', format: " + format + ", set: {'" + key + "': 'false'}}\n"
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
