// Package profiles turns contracts/runtime-profiles.yaml into Go and Kotlin catalogs.
package profiles

import (
	"errors"
	"fmt"
	"regexp"
	"strconv"
	"strings"

	"github.com/jevzo/ogcloud/data-plane/pkg/strictyaml"
)

// Forwarding modes the platform supports (D-0065).
const forwardingBungeeGuard = "bungeeguard"

// platform holds the identifiers generated code uses for one platform value.
type platform struct {
	role       string
	goConst    string
	kotlinEnum string
	// serverURL prefixes every server jar download of the platform (D-0139).
	serverURL string
}

var (
	platforms = map[string]platform{
		"paper":       {"backend", "PlatformPaper", "PAPER", fillDownloads},
		"pandaspigot": {"backend", "PlatformPandaSpigot", "PANDASPIGOT", "https://downloads.hpfxd.com/"},
		"velocity":    {"proxy", "PlatformVelocity", "VELOCITY", fillDownloads},
		"fabric":      {"backend", "PlatformFabric", "FABRIC", ""},
		"neoforge":    {"backend", "PlatformNeoForge", "NEOFORGE", ""},
	}
	idPattern     = regexp.MustCompile(`^[a-z][a-z0-9]*(-[a-z0-9.]+)+$`)
	pluginPattern = regexp.MustCompile(`^[a-z][a-z0-9-]*$`)
	sha512Pattern = regexp.MustCompile(`^[0-9a-f]{128}$`)
	filePattern   = regexp.MustCompile(`^[A-Za-z0-9._-]+(/[A-Za-z0-9._-]+)*$`)
)

const maxPort = 65535

// fillDownloads is where PaperMC's Fill API serves its jars.
const fillDownloads = "https://fill-data.papermc.io/"

// Third-party plugins come only from Modrinth's CDN (D-0079).
const pluginURLPrefix = "https://cdn.modrinth.com/data/"

// Catalog is the parsed runtime-profiles.yaml.
type Catalog struct {
	Forwarding      string        `yaml:"forwarding"`
	ClientProtocols ProtocolRange `yaml:"clientProtocols"`
	// ServerPort is the port every server and proxy listens on inside its pod.
	ServerPort int `yaml:"serverPort"`
	// JVMFlags are the flags every server of a platform starts with, baked into its image (D-0139).
	JVMFlags map[string][]string `yaml:"jvmFlags"`
	// MinimumMemory is the smallest memory limit a group of a platform may set, as a Kubernetes quantity (D-0177).
	MinimumMemory map[string]string `yaml:"minimumMemory"`
	Profiles      []ProfileEntry    `yaml:"profiles"`
	Plugins       []PluginEntry     `yaml:"plugins"`
	Overlays      []OverlayEntry    `yaml:"overlays"`
}

// OverlayEntry is a file the template loader patches on every start of a platform's servers (D-0119).
type OverlayEntry struct {
	Platform string `yaml:"platform"`
	// File is relative to the server directory.
	File   string `yaml:"file"`
	Format string `yaml:"format"`
	// Set maps keys (dotted paths for YAML) to the values written.
	Set map[string]string `yaml:"set"`
}

// Overlay formats the loader can patch.
var overlayFormats = map[string]string{"properties": "OverlayProperties", "yaml": "OverlayYAML"}

// serverPortPlaceholder in an overlay value stands for the catalog's serverPort.
const serverPortPlaceholder = "${serverPort}"

// value resolves the placeholders in an overlay value.
func (c Catalog) value(raw string) string {
	return strings.ReplaceAll(raw, serverPortPlaceholder, strconv.Itoa(c.ServerPort))
}

// PluginEntry is a third-party plugin the runtime bundles of the listed platforms carry (D-0079).
type PluginEntry struct {
	ID        string   `yaml:"id"`
	Platforms []string `yaml:"platforms"`
	// File is the jar's name inside the server's plugins directory.
	File    string `yaml:"file"`
	Version string `yaml:"version"`
	URL     string `yaml:"url"`
	SHA512  string `yaml:"sha512"`
}

// ProtocolRange is the inclusive range of client protocol numbers the network admits.
type ProtocolRange struct {
	Min int `yaml:"min"`
	Max int `yaml:"max"`
}

// ProfileEntry is one profile as written in runtime-profiles.yaml.
type ProfileEntry struct {
	ID          string    `yaml:"id"`
	Platform    string    `yaml:"platform"`
	Version     string    `yaml:"version"`
	Protocol    int       `yaml:"protocol"`
	JavaVersion int       `yaml:"java"`
	Server      ServerJar `yaml:"server"`
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
	if c.ServerPort <= 0 || c.ServerPort > maxPort {
		problems = append(problems, fmt.Errorf("serverPort must be a TCP port, got %d", c.ServerPort))
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
		problems = append(problems, entry.Server.validate(where, info)...)
		if len(c.JVMFlags[entry.Platform]) == 0 {
			problems = append(problems, fmt.Errorf("%s: jvmFlags has no flags for %s", where, entry.Platform))
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
	problems = append(problems, c.validateJVMFlags()...)
	problems = append(problems, c.validateMinimumMemory()...)
	problems = append(problems, c.validatePlugins()...)
	problems = append(problems, c.validateOverlays()...)
	return errors.Join(problems...)
}

func (c Catalog) validateMinimumMemory() []error {
	var problems []error
	for name, quantity := range c.MinimumMemory {
		if _, known := platforms[name]; !known {
			problems = append(problems, fmt.Errorf("minimumMemory: unknown platform %q", name))
		}
		if _, err := memoryBytes(quantity); err != nil {
			problems = append(problems, fmt.Errorf("minimumMemory.%s: %w", name, err))
		}
	}
	return problems
}

var memoryQuantity = regexp.MustCompile(`^([1-9][0-9]*)(Ki|Mi|Gi)$`)

// memoryBytes reads a whole number of Ki, Mi or Gi.
func memoryBytes(quantity string) (int64, error) {
	match := memoryQuantity.FindStringSubmatch(quantity)
	if match == nil {
		return 0, fmt.Errorf("%q must be a whole Ki, Mi or Gi quantity", quantity)
	}
	value, err := strconv.ParseInt(match[1], 10, 64)
	if err != nil {
		return 0, fmt.Errorf("%q: %w", quantity, err)
	}
	return value << map[string]int{"Ki": 10, "Mi": 20, "Gi": 30}[match[2]], nil
}

func (c Catalog) validateJVMFlags() []error {
	var problems []error
	for name, flags := range c.JVMFlags {
		if _, known := platforms[name]; !known {
			problems = append(problems, fmt.Errorf("jvmFlags: unknown platform %q", name))
		}
		for _, flag := range flags {
			if !strings.HasPrefix(flag, "-") || strings.ContainsAny(flag, " \t'\"$`\\") {
				problems = append(problems, fmt.Errorf("jvmFlags.%s: %q must be one plain flag starting with -", name, flag))
			}
		}
	}
	return problems
}

func (c Catalog) validateOverlays() []error {
	var problems []error
	seen := map[string]bool{}
	for i, overlay := range c.Overlays {
		where := fmt.Sprintf("overlays[%d] (%s %s)", i, overlay.Platform, overlay.File)
		if _, known := platforms[overlay.Platform]; !known {
			problems = append(problems, fmt.Errorf("%s: unknown platform %q", where, overlay.Platform))
		}
		if !filePattern.MatchString(overlay.File) || strings.Contains(overlay.File, "..") {
			problems = append(problems, fmt.Errorf("%s: file must be a relative path inside the server directory", where))
		}
		if seen[overlay.Platform+"/"+overlay.File] {
			problems = append(problems, fmt.Errorf("%s: one entry per platform and file", where))
		}
		seen[overlay.Platform+"/"+overlay.File] = true
		if _, known := overlayFormats[overlay.Format]; !known {
			problems = append(problems, fmt.Errorf("%s: format must be properties or yaml", where))
		}
		if len(overlay.Set) == 0 {
			problems = append(problems, fmt.Errorf("%s: set is required", where))
		}
		for key := range overlay.Set {
			if key == "" || strings.HasPrefix(key, ".") || strings.HasSuffix(key, ".") || strings.Contains(key, "..") {
				problems = append(problems, fmt.Errorf("%s: invalid key %q", where, key))
			}
		}
	}
	return problems
}

func (c Catalog) validatePlugins() []error {
	var problems []error
	seen := map[string]bool{}
	for i, plugin := range c.Plugins {
		where := fmt.Sprintf("plugins[%d] (%s)", i, plugin.ID)
		if !pluginPattern.MatchString(plugin.ID) || seen[plugin.ID] {
			problems = append(problems, fmt.Errorf("%s: id must be unique and match %s", where, pluginPattern))
		}
		seen[plugin.ID] = true
		if len(plugin.Platforms) == 0 {
			problems = append(problems, fmt.Errorf("%s: platforms are required", where))
		}
		for _, name := range plugin.Platforms {
			if _, known := platforms[name]; !known {
				problems = append(problems, fmt.Errorf("%s: unknown platform %q", where, name))
			}
		}
		if !strings.HasSuffix(plugin.File, ".jar") || strings.ContainsAny(plugin.File, "/\\") {
			problems = append(problems, fmt.Errorf("%s: file must be a plain .jar name", where))
		}
		if plugin.Version == "" || !strings.HasPrefix(plugin.URL, pluginURLPrefix) {
			problems = append(problems, fmt.Errorf("%s: version and a %s url are required", where, pluginURLPrefix))
		}
		if !sha512Pattern.MatchString(plugin.SHA512) {
			problems = append(problems, fmt.Errorf("%s: sha512 must be 128 lowercase hex characters", where))
		}
	}
	return problems
}
