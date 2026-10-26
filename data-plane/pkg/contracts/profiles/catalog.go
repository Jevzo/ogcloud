// Package profiles turns contracts/runtime-profiles.yaml into Go and Kotlin catalogs.
package profiles

import (
	"errors"
	"fmt"
	"regexp"

	"github.com/jevzo/ogcloud/data-plane/pkg/contracts/internal/strictyaml"
)

// Forwarding modes the platform supports (D-0065).
const forwardingBungeeGuard = "bungeeguard"

// platform holds the identifiers generated code uses for one platform value.
type platform struct {
	role       string
	goConst    string
	kotlinEnum string
}

var (
	platforms = map[string]platform{
		"paper":       {"backend", "PlatformPaper", "PAPER"},
		"pandaspigot": {"backend", "PlatformPandaSpigot", "PANDASPIGOT"},
		"velocity":    {"proxy", "PlatformVelocity", "VELOCITY"},
		"fabric":      {"backend", "PlatformFabric", "FABRIC"},
		"neoforge":    {"backend", "PlatformNeoForge", "NEOFORGE"},
	}
	idPattern = regexp.MustCompile(`^[a-z][a-z0-9]*(-[a-z0-9.]+)+$`)
)

// Catalog is the parsed runtime-profiles.yaml.
type Catalog struct {
	Forwarding      string         `yaml:"forwarding"`
	ClientProtocols ProtocolRange  `yaml:"clientProtocols"`
	Profiles        []ProfileEntry `yaml:"profiles"`
}

// ProtocolRange is the inclusive range of client protocol numbers the network admits.
type ProtocolRange struct {
	Min int `yaml:"min"`
	Max int `yaml:"max"`
}

// ProfileEntry is one profile as written in runtime-profiles.yaml.
type ProfileEntry struct {
	ID          string `yaml:"id"`
	Platform    string `yaml:"platform"`
	Version     string `yaml:"version"`
	Protocol    int    `yaml:"protocol"`
	JavaVersion int    `yaml:"java"`
}

// Parse decodes runtime-profiles.yaml.
func Parse(data []byte) (Catalog, error) {
	var catalog Catalog
	if err := strictyaml.Decode(data, &catalog); err != nil {
		return Catalog{}, fmt.Errorf("runtime profile catalog: %w", err)
	}
	return catalog, nil
}

// Validate checks every rule the generated code relies on.
func (c Catalog) Validate() error {
	var problems []error
	if c.Forwarding != forwardingBungeeGuard {
		problems = append(problems, fmt.Errorf("forwarding must be %q (D-0065), got %q", forwardingBungeeGuard, c.Forwarding))
	}
	if c.ClientProtocols.Min <= 0 || c.ClientProtocols.Max < c.ClientProtocols.Min {
		problems = append(problems, fmt.Errorf("clientProtocols must be a positive range, got %d..%d", c.ClientProtocols.Min, c.ClientProtocols.Max))
	}
	roles := map[string]bool{}
	seen := map[string]bool{}
	for i, entry := range c.Profiles {
		where := fmt.Sprintf("profiles[%d] (%s)", i, entry.ID)
		if !idPattern.MatchString(entry.ID) {
			problems = append(problems, fmt.Errorf("%s: id must match %s", where, idPattern))
		}
		if seen[entry.ID] {
			problems = append(problems, fmt.Errorf("%s: duplicate id", where))
		}
		seen[entry.ID] = true
		info, known := platforms[entry.Platform]
		if !known {
			problems = append(problems, fmt.Errorf("%s: unknown platform %q", where, entry.Platform))
			continue
		}
		roles[info.role] = true
		if entry.Version == "" || entry.JavaVersion <= 0 {
			problems = append(problems, fmt.Errorf("%s: version and java are required", where))
		}
		switch {
		case info.role == "proxy" && entry.Protocol != 0:
			problems = append(problems, fmt.Errorf("%s: proxies speak every client protocol; omit protocol", where))
		case info.role == "backend" && (entry.Protocol < c.ClientProtocols.Min || entry.Protocol > c.ClientProtocols.Max):
			problems = append(problems, fmt.Errorf("%s: protocol %d is outside clientProtocols", where, entry.Protocol))
		}
	}
	if !roles["proxy"] || !roles["backend"] {
		problems = append(problems, errors.New("the catalog needs at least one proxy and one backend profile"))
	}
	return errors.Join(problems...)
}
