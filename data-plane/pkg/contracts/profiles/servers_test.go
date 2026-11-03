package profiles

import (
	"context"
	"errors"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"
)

const pinnedServers = `profiles:
  - id: paper-26.3
    platform: paper
    version: "26.3"
    # The jar baked into the image.
    server:
      build: 238
      url: https://fill-data.papermc.io/v1/objects/old/paper-26.3-238.jar
      sha256: ` + "1111111111111111111111111111111111111111111111111111111111111111" + `
      source: https://github.com/PaperMC/Paper/commit/old
  - id: velocity-4.3.0
    platform: velocity
    version: "4.3.0"
    server:
      build: 44
      url: https://fill-data.papermc.io/v1/objects/same/velocity-4.3.0-44.jar
      sha256: 2222222222222222222222222222222222222222222222222222222222222222
      source: https://github.com/PaperMC/Velocity/commit/same
`

type fakeBuilds map[string]ServerJar

func (f fakeBuilds) Latest(_ context.Context, version string) (ServerJar, error) {
	build, ok := f[version]
	if !ok {
		return ServerJar{}, errors.New("unknown version")
	}
	return build, nil
}

func TestUpdateServers(t *testing.T) {
	newHash := strings.Repeat("3", 64)
	tests := []struct {
		name      string
		sources   map[string]ServerSource
		wantBumps []Bump
		wantLines []string
		wantErr   string
	}{
		{
			name: "only a profile with a newer build moves, and only its server values change",
			sources: map[string]ServerSource{
				"paper":    fakeBuilds{"26.3": {Build: 240, URL: "https://fill-data.papermc.io/v1/objects/new/paper-26.3-240.jar", SHA256: newHash, Source: "https://github.com/PaperMC/Paper/commit/new"}},
				"velocity": fakeBuilds{"4.3.0": {Build: 44}},
			},
			wantBumps: []Bump{{ID: "paper-26.3", From: "238", To: "240"}},
			wantLines: []string{"      build: 240", "      sha256: " + newHash, "      source: https://github.com/PaperMC/Paper/commit/new", "    # The jar baked into the image.", "      build: 44"},
		},
		{
			name:    "a platform without a build source is an error",
			sources: map[string]ServerSource{"paper": fakeBuilds{"26.3": {Build: 238}}},
			wantErr: "no build source",
		},
		{
			name: "a build without a valid hash is refused",
			sources: map[string]ServerSource{
				"paper":    fakeBuilds{"26.3": {Build: 240, SHA256: "abc"}},
				"velocity": fakeBuilds{"4.3.0": {Build: 44}},
			},
			wantErr: "no valid sha256",
		},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			out, bumps, err := UpdateServers(context.Background(), []byte(pinnedServers), tt.sources)
			if tt.wantErr != "" {
				if err == nil || !strings.Contains(err.Error(), tt.wantErr) {
					t.Fatalf("UpdateServers() error = %v, want %q", err, tt.wantErr)
				}
				return
			}
			if err != nil {
				t.Fatalf("UpdateServers: %v", err)
			}
			if len(bumps) != len(tt.wantBumps) || (len(bumps) > 0 && bumps[0] != tt.wantBumps[0]) {
				t.Errorf("bumps = %v, want %v", bumps, tt.wantBumps)
			}
			for _, line := range tt.wantLines {
				if !strings.Contains(string(out), line+"\n") {
					t.Errorf("output lacks line %q:\n%s", line, out)
				}
			}
		})
	}
}

func TestFillBuildsTakesTheNewestStableBuild(t *testing.T) {
	api := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.URL.Path != "/v3/projects/paper/versions/26.3/builds" || r.Header.Get("User-Agent") == "" {
			http.NotFound(w, r)
			return
		}
		_, _ = w.Write([]byte(`[
			{"id": 241, "channel": "BETA", "commits": [{"sha": "beta"}], "downloads": {"server:default": {"url": "https://x/241.jar", "checksums": {"sha256": "b"}}}},
			{"id": 240, "channel": "STABLE", "commits": [{"sha": "abc"}], "downloads": {"server:default": {"url": "https://x/240.jar", "checksums": {"sha256": "s"}}}}
		]`))
	}))
	defer api.Close()

	got, err := FillBuilds{Client: api.Client(), BaseURL: api.URL, Project: "paper", Repository: "PaperMC/Paper"}.Latest(context.Background(), "26.3")
	if err != nil {
		t.Fatalf("Latest: %v", err)
	}
	want := ServerJar{Build: 240, URL: "https://x/240.jar", SHA256: "s", Source: "https://github.com/PaperMC/Paper/commit/abc"}
	if got != want {
		t.Errorf("Latest() = %+v, want %+v", got, want)
	}
}

func TestHpfxdBuildsTakesTheNewestDefaultBuild(t *testing.T) {
	api := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		_, _ = w.Write([]byte(`{"builds": [
			{"build": 129, "channel": "experimental", "changes": [{"commit": "exp"}], "downloads": {"paperclip": {"sha256": "e"}}},
			{"build": 128, "channel": "default", "changes": [{"commit": "c75e"}], "downloads": {"paperclip": {"sha256": "d288"}}},
			{"build": 127, "channel": "default", "changes": [{"commit": "b4af"}], "downloads": {"paperclip": {"sha256": "6436"}}}
		]}`))
	}))
	defer api.Close()

	got, err := HpfxdBuilds{Client: api.Client(), BaseURL: api.URL}.Latest(context.Background(), "1.8.8")
	if err != nil {
		t.Fatalf("Latest: %v", err)
	}
	want := ServerJar{
		Build:  128,
		URL:    api.URL + "/v2/projects/pandaspigot/versions/1.8.8/builds/128/downloads/paperclip",
		SHA256: "d288",
		Source: "https://github.com/hpfxd/PandaSpigot/commit/c75e",
	}
	if got != want {
		t.Errorf("Latest() = %+v, want %+v", got, want)
	}
}

func TestBuildSourcesReportMissingBuilds(t *testing.T) {
	api := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if strings.HasPrefix(r.URL.Path, "/v3/") {
			_, _ = w.Write([]byte(`[]`))
			return
		}
		http.Error(w, "gone", http.StatusInternalServerError)
	}))
	defer api.Close()

	if _, err := (FillBuilds{Client: api.Client(), BaseURL: api.URL, Project: "folia"}).Latest(context.Background(), "26.3"); err == nil || !strings.Contains(err.Error(), "no STABLE build") {
		t.Errorf("Fill error = %v, want no STABLE build", err)
	}
	if _, err := (HpfxdBuilds{Client: api.Client(), BaseURL: api.URL}).Latest(context.Background(), "1.8.8"); err == nil || !strings.Contains(err.Error(), "HTTP 500") {
		t.Errorf("hpfxd error = %v, want HTTP 500", err)
	}
}
