package profiles

import (
	"context"
	"encoding/json"
	"fmt"
	"net/http"
	"net/url"
	"regexp"
	"strconv"
	"strings"

	"go.yaml.in/yaml/v3"
)

// ServerJar is the server jar a profile's image bakes in (D-0046), pinned with its hash (D-0139).
type ServerJar struct {
	Build  int    `yaml:"build"`
	URL    string `yaml:"url"`
	SHA256 string `yaml:"sha256"`
	// Source is the commit the build was made from, for the image's GPL source offer.
	Source string `yaml:"source"`
}

var sha256Pattern = regexp.MustCompile(`^[0-9a-f]{64}$`)

// sourcePrefix is where every platform keeps its sources.
const sourcePrefix = "https://github.com/"

func (s ServerJar) validate(where string, info platform) []error {
	var problems []error
	if s.Build <= 0 {
		problems = append(problems, fmt.Errorf("%s: server.build must be positive", where))
	}
	if info.serverURL == "" || !strings.HasPrefix(s.URL, info.serverURL) {
		problems = append(problems, fmt.Errorf("%s: server.url must start with %q", where, info.serverURL))
	}
	if !sha256Pattern.MatchString(s.SHA256) {
		problems = append(problems, fmt.Errorf("%s: server.sha256 must be 64 lowercase hex characters", where))
	}
	if !strings.HasPrefix(s.Source, sourcePrefix) {
		problems = append(problems, fmt.Errorf("%s: server.source must start with %q", where, sourcePrefix))
	}
	return problems
}

// ServerSource finds the newest stable build of a platform version (D-0139).
type ServerSource interface {
	Latest(ctx context.Context, version string) (ServerJar, error)
}

// UpdateServers moves every profile's server pin to the newest stable build of its version. As UpdatePlugins,
// only the values change in place.
func UpdateServers(ctx context.Context, data []byte, sources map[string]ServerSource) ([]byte, []Bump, error) {
	return updateEntries(data, "profiles", func(entry *yaml.Node, lines []string) (*Bump, error) {
		return updateServer(ctx, entry, lines, sources)
	})
}

func updateServer(ctx context.Context, entry *yaml.Node, lines []string, sources map[string]ServerSource) (*Bump, error) {
	id := scalarValue(entry, "id")
	source, known := sources[scalarValue(entry, "platform")]
	server := mappingValue(entry, "server")
	if !known || server == nil {
		return nil, fmt.Errorf("profile %s: no server pin or no build source for its platform", id)
	}
	latest, err := source.Latest(ctx, scalarValue(entry, "version"))
	if err != nil {
		return nil, fmt.Errorf("profile %s: %w", id, err)
	}
	build := mappingValue(server, "build")
	if build == nil || build.Value == strconv.Itoa(latest.Build) {
		return nil, nil
	}
	if !sha256Pattern.MatchString(latest.SHA256) {
		return nil, fmt.Errorf("profile %s: build %d has no valid sha256", id, latest.Build)
	}
	from := build.Value
	replaceOnLine(lines, build, strconv.Itoa(latest.Build))
	for key, value := range map[string]string{"url": latest.URL, "sha256": latest.SHA256, "source": latest.Source} {
		if node := mappingValue(server, key); node != nil {
			replaceOnLine(lines, node, value)
		}
	}
	return &Bump{ID: id, From: from, To: strconv.Itoa(latest.Build)}, nil
}

// FillBuilds reads PaperMC's Fill API (v3), which serves Paper, Velocity and Folia.
type FillBuilds struct {
	Client  *http.Client
	BaseURL string
	Project string
	// Repository is the project's GitHub repository, e.g. PaperMC/Paper.
	Repository string
}

type fillBuild struct {
	ID      int    `json:"id"`
	Channel string `json:"channel"`
	Commits []struct {
		SHA string `json:"sha"`
	} `json:"commits"`
	Downloads map[string]struct {
		URL       string            `json:"url"`
		Checksums map[string]string `json:"checksums"`
	} `json:"downloads"`
}

// fillStable is the only Fill channel OgCloud pins (AGENTS §9).
const fillStable = "STABLE"

// Latest returns the newest STABLE build; Fill lists builds newest first.
func (f FillBuilds) Latest(ctx context.Context, version string) (ServerJar, error) {
	var builds []fillBuild
	endpoint := fmt.Sprintf("%s/v3/projects/%s/versions/%s/builds", f.BaseURL, url.PathEscape(f.Project), url.PathEscape(version))
	if err := getJSON(ctx, f.Client, endpoint, &builds); err != nil {
		return ServerJar{}, err
	}
	for _, build := range builds {
		download, ok := build.Downloads["server:default"]
		if build.Channel != fillStable || !ok || len(build.Commits) == 0 {
			continue
		}
		return ServerJar{
			Build:  build.ID,
			URL:    download.URL,
			SHA256: download.Checksums["sha256"],
			Source: sourcePrefix + f.Repository + "/commit/" + build.Commits[0].SHA,
		}, nil
	}
	return ServerJar{}, fmt.Errorf("%s %s has no STABLE build", f.Project, version)
}

// HpfxdBuilds reads hpfxd's downloads API (v2), which serves PandaSpigot.
type HpfxdBuilds struct {
	Client  *http.Client
	BaseURL string
}

type hpfxdBuilds struct {
	Builds []struct {
		Build   int    `json:"build"`
		Channel string `json:"channel"`
		Changes []struct {
			Commit string `json:"commit"`
		} `json:"changes"`
		Downloads map[string]struct {
			SHA256 string `json:"sha256"`
		} `json:"downloads"`
	} `json:"builds"`
}

const (
	pandaSpigotProject = "pandaspigot"
	// hpfxd has no stable channel; "default" holds every regular build, as opposed to experimental ones.
	hpfxdDefault  = "default"
	hpfxdDownload = "paperclip"
)

// Latest returns the newest build of the default channel.
func (h HpfxdBuilds) Latest(ctx context.Context, version string) (ServerJar, error) {
	var listing hpfxdBuilds
	base := fmt.Sprintf("%s/v2/projects/%s/versions/%s/builds", h.BaseURL, pandaSpigotProject, url.PathEscape(version))
	if err := getJSON(ctx, h.Client, base, &listing); err != nil {
		return ServerJar{}, err
	}
	var newest ServerJar
	for _, build := range listing.Builds {
		download, ok := build.Downloads[hpfxdDownload]
		if build.Channel != hpfxdDefault || !ok || len(build.Changes) == 0 || build.Build <= newest.Build {
			continue
		}
		newest = ServerJar{
			Build:  build.Build,
			URL:    fmt.Sprintf("%s/%d/downloads/%s", base, build.Build, hpfxdDownload),
			SHA256: download.SHA256,
			Source: sourcePrefix + "hpfxd/PandaSpigot/commit/" + build.Changes[0].Commit,
		}
	}
	if newest.Build == 0 {
		return ServerJar{}, fmt.Errorf("%s %s has no build", pandaSpigotProject, version)
	}
	return newest, nil
}

func getJSON(ctx context.Context, client *http.Client, endpoint string, into any) error {
	request, err := http.NewRequestWithContext(ctx, http.MethodGet, endpoint, nil)
	if err != nil {
		return fmt.Errorf("build request: %w", err)
	}
	// PaperMC and Modrinth ask API clients to identify themselves.
	request.Header.Set("User-Agent", userAgent)
	response, err := client.Do(request)
	if err != nil {
		return fmt.Errorf("query %s: %w", endpoint, err)
	}
	defer func() { _ = response.Body.Close() }()
	if response.StatusCode != http.StatusOK {
		return fmt.Errorf("query %s: HTTP %d", endpoint, response.StatusCode)
	}
	if err := json.NewDecoder(response.Body).Decode(into); err != nil {
		return fmt.Errorf("decode %s: %w", endpoint, err)
	}
	return nil
}
