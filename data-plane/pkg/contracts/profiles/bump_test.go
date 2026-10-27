package profiles

import (
	"context"
	"errors"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"
)

const pinned = `# Third-party plugins.
plugins:
  - id: viaversion
    platforms: [velocity]
    file: ViaVersion.jar
    version: "5.12.1"
    url: https://cdn.modrinth.com/data/P1OZGk5p/versions/old/ViaVersion-5.12.1.jar
    sha512: ` + "aaaa" + `
  - id: viabackwards
    platforms: [velocity]
    file: ViaBackwards.jar
    version: "5.12.1"
    url: https://cdn.modrinth.com/data/NpvuJQoq/versions/same/ViaBackwards-5.12.1.jar
    sha512: bbbb
`

type fakeReleases map[string]Release

func (f fakeReleases) Latest(_ context.Context, project string, loaders []string) (Release, error) {
	if len(loaders) != 1 || loaders[0] != "velocity" {
		return Release{}, errors.New("unexpected loaders")
	}
	release, ok := f[project]
	if !ok {
		return Release{}, errors.New("unknown project")
	}
	return release, nil
}

func TestUpdatePlugins(t *testing.T) {
	newHash := strings.Repeat("c", 128)
	tests := []struct {
		name      string
		releases  fakeReleases
		wantBumps int
		wantLines []string
		wantErr   string
	}{
		{
			name: "only the plugin with a newer release moves; its own lines change",
			releases: fakeReleases{
				"P1OZGk5p": {Version: "5.13.0", URL: "https://cdn.modrinth.com/data/P1OZGk5p/versions/new/ViaVersion-5.13.0.jar", SHA512: newHash},
				"NpvuJQoq": {Version: "5.12.1", URL: "https://cdn.modrinth.com/data/NpvuJQoq/versions/same/ViaBackwards-5.12.1.jar", SHA512: "bbbb"},
			},
			wantBumps: 1,
			wantLines: []string{`    version: "5.13.0"`, "    sha512: " + newHash, `    version: "5.12.1"`, "# Third-party plugins."},
		},
		{
			name: "a release outside Modrinth's CDN is refused",
			releases: fakeReleases{
				"P1OZGk5p": {Version: "6.0.0", URL: "https://example.com/ViaVersion.jar", SHA512: newHash},
				"NpvuJQoq": {Version: "5.12.1", URL: "https://cdn.modrinth.com/data/NpvuJQoq/versions/same/ViaBackwards-5.12.1.jar"},
			},
			wantErr: "unexpected url",
		},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			out, bumps, err := UpdatePlugins(context.Background(), []byte(pinned), tt.releases)
			if tt.wantErr != "" {
				if err == nil || !strings.Contains(err.Error(), tt.wantErr) {
					t.Fatalf("err = %v, want %q", err, tt.wantErr)
				}
				return
			}
			if err != nil {
				t.Fatalf("UpdatePlugins: %v", err)
			}
			if len(bumps) != tt.wantBumps {
				t.Errorf("bumps = %+v, want %d", bumps, tt.wantBumps)
			}
			for _, line := range tt.wantLines {
				if !strings.Contains(string(out), line+"\n") {
					t.Errorf("output lacks line %q:\n%s", line, out)
				}
			}
			if strings.Count(string(out), "\n") != strings.Count(pinned, "\n") {
				t.Errorf("line count changed:\n%s", out)
			}
		})
	}
}

func TestUpdatePluginsRejectsAnEmptyFile(t *testing.T) {
	if _, _, err := UpdatePlugins(context.Background(), nil, fakeReleases{}); err == nil {
		t.Error("an empty catalog was accepted")
	}
}

func TestModrinthReleasesPicksTheNewestStableRelease(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.URL.Path != "/v2/project/P1OZGk5p/version" || r.URL.Query().Get("loaders") != `["velocity"]` {
			http.NotFound(w, r)
			return
		}
		_, _ = w.Write([]byte(`[
			{"version_number": "5.14.0-beta", "version_type": "beta", "files": [{"url": "https://cdn.modrinth.com/beta.jar", "primary": true, "hashes": {"sha512": "x"}}]},
			{"version_number": "5.13.0", "version_type": "release", "files": [{"url": "https://cdn.modrinth.com/stable.jar", "primary": true, "hashes": {"sha512": "y"}}]}
		]`))
	}))
	defer server.Close()

	release, err := ModrinthReleases{Client: server.Client(), BaseURL: server.URL}.Latest(context.Background(), "P1OZGk5p", []string{"velocity"})
	if err != nil {
		t.Fatalf("Latest: %v", err)
	}
	if release.Version != "5.13.0" || release.URL != "https://cdn.modrinth.com/stable.jar" || release.SHA512 != "y" {
		t.Errorf("Latest = %+v", release)
	}
	if _, err := (ModrinthReleases{Client: server.Client(), BaseURL: server.URL}).Latest(context.Background(), "missing", nil); err == nil {
		t.Error("Latest of an unknown project succeeded")
	}
}
