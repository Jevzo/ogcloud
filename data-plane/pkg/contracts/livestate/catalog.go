// Package livestate parses contracts/livestate/keys.yaml, the Valkey key layout (D-0111), and renders
// it for the Go data plane and the Kotlin control plane.
package livestate

import (
	"errors"
	"fmt"
	"regexp"
	"strings"
	"time"

	"github.com/jevzo/ogcloud/data-plane/pkg/strictyaml"
)

// ParamType says how a key parameter is typed and formatted.
type ParamType string

// The parameter types a pattern may use.
const (
	ParamUUID          ParamType = "uuid"
	ParamString        ParamType = "string"
	ParamLowercase     ParamType = "lowercase"
	ParamGroup         ParamType = "group"
	ParamReadModelKind ParamType = "readModelKind"
	ParamOneTimeKind   ParamType = "oneTimeKind"
)

// Catalog is the whole key layout.
type Catalog struct {
	Fields         Fields     `yaml:"fields"`
	TTLs           []TTL      `yaml:"ttls"`
	Constants      []Constant `yaml:"constants"`
	ReadModelKinds []Kind     `yaml:"readModelKinds"`
	OneTimeKinds   []Kind     `yaml:"oneTimeKinds"`
	Keys           []Key      `yaml:"keys"`
}

// Fields are the hash fields of revisioned records.
type Fields struct {
	Revision string `yaml:"revision"`
	Data     string `yaml:"data"`
}

// TTL is a named expiry, written like "15m" or "24h".
type TTL struct {
	Name     string `yaml:"name"`
	Duration string `yaml:"duration"`
	Doc      string `yaml:"doc"`
}

// Constant is a named string shared by every reader.
type Constant struct {
	Name  string `yaml:"name"`
	Value string `yaml:"value"`
	Doc   string `yaml:"doc"`
}

// Kind is one value of a kind enum and the text it contributes to keys.
type Kind struct {
	Name string `yaml:"name"`
	Key  string `yaml:"key"`
	Doc  string `yaml:"doc"`
}

// Key is a key pattern such as "server:{id:uuid}"; with Prefix, its leading literal is exported too.
type Key struct {
	Name    string `yaml:"name"`
	Pattern string `yaml:"pattern"`
	Doc     string `yaml:"doc"`
	Prefix  bool   `yaml:"prefix"`
}

// Segment is a literal piece of a pattern or a parameter.
type Segment struct {
	Literal string
	Param   *Param
}

// Param is a typed placeholder in a pattern.
type Param struct {
	Name string
	Type ParamType
}

var (
	identifierPattern = regexp.MustCompile(`^[a-z][A-Za-z0-9]*$`)
	paramPattern      = regexp.MustCompile(`\{([a-z][A-Za-z0-9]*):([a-zA-Z]+)\}`)
	knownParamTypes   = map[ParamType]bool{
		ParamUUID: true, ParamString: true, ParamLowercase: true, ParamGroup: true,
		ParamReadModelKind: true, ParamOneTimeKind: true,
	}
)

// Parse decodes keys.yaml, rejecting unknown fields.
func Parse(data []byte) (*Catalog, error) {
	var catalog Catalog
	if err := strictyaml.Decode(data, &catalog); err != nil {
		return nil, err
	}
	return &catalog, nil
}

// Validate checks names, patterns and durations, so the renderers can trust the catalog.
func (c *Catalog) Validate() error {
	var problems []error
	if c.Fields.Revision == "" || c.Fields.Data == "" {
		problems = append(problems, errors.New("fields: revision and data are required"))
	}
	names := map[string]bool{}
	claim := func(section, name string) {
		if !identifierPattern.MatchString(name) {
			problems = append(problems, fmt.Errorf("%s: %q is not a lowerCamelCase name", section, name))
		}
		if names[section+"/"+name] {
			problems = append(problems, fmt.Errorf("%s: %q is declared twice", section, name))
		}
		names[section+"/"+name] = true
	}
	for _, ttl := range c.TTLs {
		claim("ttls", ttl.Name)
		if _, err := ttl.Parsed(); err != nil {
			problems = append(problems, fmt.Errorf("ttls %s: %w", ttl.Name, err))
		}
	}
	for _, constant := range c.Constants {
		claim("constants", constant.Name)
		if constant.Value == "" {
			problems = append(problems, fmt.Errorf("constants %s: value is required", constant.Name))
		}
	}
	checkKinds := func(section string, kinds []Kind) {
		keys := map[string]bool{}
		for _, kind := range kinds {
			claim(section, kind.Name)
			if kind.Key == "" || keys[kind.Key] {
				problems = append(problems, fmt.Errorf("%s %s: key %q is empty or taken", section, kind.Name, kind.Key))
			}
			keys[kind.Key] = true
		}
	}
	checkKinds("readModelKinds", c.ReadModelKinds)
	checkKinds("oneTimeKinds", c.OneTimeKinds)
	for _, key := range c.Keys {
		claim("keys", key.Name)
		segments, err := key.Segments()
		if err != nil {
			problems = append(problems, fmt.Errorf("keys %s: %w", key.Name, err))
			continue
		}
		if key.Prefix && (len(segments) < 2 || segments[0].Param != nil) {
			problems = append(problems, fmt.Errorf("keys %s: a prefix needs literal text before a parameter", key.Name))
		}
	}
	return errors.Join(problems...)
}

// Parsed returns the duration.
func (t TTL) Parsed() (time.Duration, error) {
	duration, err := time.ParseDuration(t.Duration)
	if err != nil {
		return 0, fmt.Errorf("parse duration %q: %w", t.Duration, err)
	}
	if duration <= 0 {
		return 0, fmt.Errorf("duration %q must be positive", t.Duration)
	}
	return duration, nil
}

// Segments splits the pattern into literals and typed parameters.
func (k Key) Segments() ([]Segment, error) {
	var segments []Segment
	rest := k.Pattern
	for rest != "" {
		match := paramPattern.FindStringSubmatchIndex(rest)
		if match == nil {
			if strings.ContainsAny(rest, "{}") {
				return nil, fmt.Errorf("malformed parameter in %q", k.Pattern)
			}
			segments = append(segments, Segment{Literal: rest})
			break
		}
		if literal := rest[:match[0]]; literal != "" {
			if strings.ContainsAny(literal, "{}") {
				return nil, fmt.Errorf("malformed parameter in %q", k.Pattern)
			}
			segments = append(segments, Segment{Literal: literal})
		}
		param := Param{Name: rest[match[2]:match[3]], Type: ParamType(rest[match[4]:match[5]])}
		if !knownParamTypes[param.Type] {
			return nil, fmt.Errorf("unknown parameter type %q in %q", param.Type, k.Pattern)
		}
		segments = append(segments, Segment{Param: &param})
		rest = rest[match[1]:]
	}
	if len(segments) == 0 {
		return nil, errors.New("pattern is empty")
	}
	return segments, nil
}
