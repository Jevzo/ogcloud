package profiles

import (
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"net/http"
	"net/url"
	"regexp"
	"strings"

	"go.yaml.in/yaml/v3"
)

// Release is a plugin's newest stable release (D-0082).
type Release struct {
	Version string
	URL     string
	SHA512  string
}

// ReleaseSource finds the newest stable release of a Modrinth project for the given loaders.
type ReleaseSource interface {
	Latest(ctx context.Context, projectID string, loaders []string) (Release, error)
}

// Bump is one plugin whose pin moved.
type Bump struct {
	ID   string
	From string
	To   string
}

var modrinthProject = regexp.MustCompile(`^https://cdn\.modrinth\.com/data/([A-Za-z0-9]+)/`)

// loaders maps catalog platforms to the Modrinth loaders a plugin release must support.
var loaders = map[string][]string{
	"velocity":    {"velocity"},
	"paper":       {"paper"},
	"pandaspigot": {"spigot"},
	"fabric":      {"fabric"},
	"neoforge":    {"neoforge"},
}

// UpdatePlugins moves every plugin pin in runtime-profiles.yaml to its newest stable release. Only the version,
// url and sha512 values change in place, so comments and layout stay as they are.
func UpdatePlugins(ctx context.Context, data []byte, source ReleaseSource) ([]byte, []Bump, error) {
	var doc yaml.Node
	if err := yaml.Unmarshal(data, &doc); err != nil {
		return nil, nil, fmt.Errorf("parse runtime profiles: %w", err)
	}
	if len(doc.Content) == 0 {
		return nil, nil, errors.New("runtime profiles: the file is empty")
	}
	plugins := mappingValue(doc.Content[0], "plugins")
	if plugins == nil {
		return data, nil, nil
	}
	lines := strings.Split(string(data), "\n")
	var bumps []Bump
	for _, entry := range plugins.Content {
		bump, err := updateEntry(ctx, entry, lines, source)
		if err != nil {
			return nil, nil, err
		}
		if bump != nil {
			bumps = append(bumps, *bump)
		}
	}
	return []byte(strings.Join(lines, "\n")), bumps, nil
}

func updateEntry(ctx context.Context, entry *yaml.Node, lines []string, source ReleaseSource) (*Bump, error) {
	id := scalarValue(entry, "id")
	urlNode := mappingValue(entry, "url")
	if urlNode == nil {
		return nil, fmt.Errorf("plugin %s has no url", id)
	}
	match := modrinthProject.FindStringSubmatch(urlNode.Value)
	if match == nil {
		return nil, fmt.Errorf("plugin %s: url %s is not a Modrinth CDN url", id, urlNode.Value)
	}
	var wanted []string
	if platforms := mappingValue(entry, "platforms"); platforms != nil {
		for _, platform := range platforms.Content {
			wanted = append(wanted, loaders[platform.Value]...)
		}
	}
	release, err := source.Latest(ctx, match[1], wanted)
	if err != nil {
		return nil, fmt.Errorf("plugin %s: %w", id, err)
	}
	if release.URL == urlNode.Value {
		return nil, nil
	}
	if !strings.HasPrefix(release.URL, pluginURLPrefix) || !sha512Pattern.MatchString(release.SHA512) {
		return nil, fmt.Errorf("plugin %s: Modrinth returned an unexpected url or hash for %s", id, release.Version)
	}
	versionNode := mappingValue(entry, "version")
	from := ""
	if versionNode != nil {
		from = versionNode.Value
		replaceOnLine(lines, versionNode, release.Version)
	}
	replaceOnLine(lines, urlNode, release.URL)
	if hash := mappingValue(entry, "sha512"); hash != nil {
		replaceOnLine(lines, hash, release.SHA512)
	}
	return &Bump{ID: id, From: from, To: release.Version}, nil
}

// replaceOnLine swaps a scalar's value on its own line; the values are unique there, so layout and quoting stay.
func replaceOnLine(lines []string, node *yaml.Node, value string) {
	line := node.Line - 1
	lines[line] = strings.Replace(lines[line], node.Value, value, 1)
}

func mappingValue(mapping *yaml.Node, key string) *yaml.Node {
	if mapping.Kind != yaml.MappingNode {
		return nil
	}
	for i := 0; i+1 < len(mapping.Content); i += 2 {
		if mapping.Content[i].Value == key {
			return mapping.Content[i+1]
		}
	}
	return nil
}

func scalarValue(mapping *yaml.Node, key string) string {
	if node := mappingValue(mapping, key); node != nil {
		return node.Value
	}
	return ""
}

// ModrinthReleases reads releases from the Modrinth API (v2).
type ModrinthReleases struct {
	Client  *http.Client
	BaseURL string
}

type modrinthVersion struct {
	VersionNumber string `json:"version_number"`
	VersionType   string `json:"version_type"`
	Files         []struct {
		URL     string            `json:"url"`
		Primary bool              `json:"primary"`
		Hashes  map[string]string `json:"hashes"`
	} `json:"files"`
}

// Latest returns the newest release-channel version; Modrinth lists versions newest first.
func (m ModrinthReleases) Latest(ctx context.Context, projectID string, loaders []string) (Release, error) {
	encoded, err := json.Marshal(loaders)
	if err != nil {
		return Release{}, fmt.Errorf("encode loaders: %w", err)
	}
	endpoint := fmt.Sprintf("%s/v2/project/%s/version?loaders=%s", m.BaseURL, url.PathEscape(projectID), url.QueryEscape(string(encoded)))
	request, err := http.NewRequestWithContext(ctx, http.MethodGet, endpoint, nil)
	if err != nil {
		return Release{}, fmt.Errorf("build request: %w", err)
	}
	// Modrinth asks API clients to identify themselves.
	request.Header.Set("User-Agent", "ogcloud-codegen (github.com/Jevzo/ogcloud)")
	response, err := m.Client.Do(request)
	if err != nil {
		return Release{}, fmt.Errorf("query Modrinth: %w", err)
	}
	defer func() { _ = response.Body.Close() }()
	if response.StatusCode != http.StatusOK {
		return Release{}, fmt.Errorf("query Modrinth: HTTP %d for project %s", response.StatusCode, projectID)
	}
	var versions []modrinthVersion
	if err := json.NewDecoder(response.Body).Decode(&versions); err != nil {
		return Release{}, fmt.Errorf("decode Modrinth versions: %w", err)
	}
	for _, version := range versions {
		if version.VersionType != "release" {
			continue
		}
		for _, file := range version.Files {
			if file.Primary {
				return Release{Version: version.VersionNumber, URL: file.URL, SHA512: file.Hashes["sha512"]}, nil
			}
		}
	}
	return Release{}, errors.New("no stable release with a primary file")
}
