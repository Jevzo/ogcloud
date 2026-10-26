// Package topics turns contracts/kafka/topics.yaml into Go and Kotlin constants and Strimzi
// manifests, so topic names, partitions, retention and ACLs have one source of truth (D-0011).
package topics

import (
	"errors"
	"fmt"
	"regexp"
	"slices"
	"time"

	"github.com/jevzo/ogcloud/data-plane/pkg/contracts/internal/strictyaml"
)

const deadLetterSuffix = ".dlt"

var (
	topicNamePattern   = regexp.MustCompile(`^ogcloud(\.[a-z0-9]+(-[a-z0-9]+)*)+$`)
	serviceNamePattern = regexp.MustCompile(`^[a-z][a-z0-9]*(-[a-z0-9]+)*$`)
)

// Catalog is the parsed topics.yaml.
type Catalog struct {
	Defaults Defaults `yaml:"defaults"`
	Topics   []Spec   `yaml:"topics"`
}

// Defaults apply to every topic that does not set its own value.
type Defaults struct {
	Partitions          int       `yaml:"partitions"`
	Retention           Retention `yaml:"retention"`
	DeadLetterRetention Retention `yaml:"deadLetterRetention"`
}

// Spec is one topic as written in topics.yaml.
type Spec struct {
	Name       string    `yaml:"name"`
	Key        string    `yaml:"key"`
	Partitions int       `yaml:"partitions"`
	Retention  Retention `yaml:"retention"`
	Producers  []string  `yaml:"producers"`
	Consumers  []string  `yaml:"consumers"`
	DeadLetter bool      `yaml:"deadLetter"`
}

// Topic is a fully resolved topic: defaults applied, dead-letter topics expanded.
type Topic struct {
	Name       string
	Partitions int
	Retention  time.Duration
	Producers  []string
	Consumers  []string
	DeadLetter bool
}

// Parse decodes topics.yaml, rejecting unknown fields so typos fail loudly.
func Parse(data []byte) (Catalog, error) {
	var catalog Catalog
	if err := strictyaml.Decode(data, &catalog); err != nil {
		return Catalog{}, fmt.Errorf("topics catalog: %w", err)
	}
	return catalog, nil
}

// Resolve validates the catalog and returns every topic, including dead-letter topics,
// in declaration order.
func (c Catalog) Resolve() ([]Topic, error) {
	if err := c.validate(); err != nil {
		return nil, err
	}
	var resolved []Topic
	for _, spec := range c.Topics {
		topic := Topic{
			Name:       spec.Name,
			Partitions: valueOr(spec.Partitions, c.Defaults.Partitions),
			Retention:  valueOr(spec.Retention.Duration, c.Defaults.Retention.Duration),
			Producers:  spec.Producers,
			Consumers:  spec.Consumers,
		}
		resolved = append(resolved, topic)
		if spec.DeadLetter {
			// Consumers park records they cannot process; operators read them back by hand.
			resolved = append(resolved, Topic{
				Name:       spec.Name + deadLetterSuffix,
				Partitions: topic.Partitions,
				Retention:  c.Defaults.DeadLetterRetention.Duration,
				Producers:  spec.Consumers,
				DeadLetter: true,
			})
		}
	}
	return resolved, nil
}

func (c Catalog) validate() error {
	var problems []error
	if c.Defaults.Partitions <= 0 {
		problems = append(problems, errors.New("defaults.partitions must be positive"))
	}
	if c.Defaults.Retention.Duration <= 0 {
		problems = append(problems, errors.New("defaults.retention must be positive"))
	}
	if c.Defaults.DeadLetterRetention.Duration <= 0 && slices.ContainsFunc(c.Topics, func(s Spec) bool { return s.DeadLetter }) {
		problems = append(problems, errors.New("defaults.deadLetterRetention must be positive when a topic has deadLetter: true"))
	}
	seen := map[string]bool{}
	for i, spec := range c.Topics {
		problems = append(problems, spec.validate(i, seen)...)
	}
	return errors.Join(problems...)
}

func (s Spec) validate(index int, seen map[string]bool) []error {
	var problems []error
	where := fmt.Sprintf("topics[%d] (%s)", index, s.Name)
	switch {
	case !topicNamePattern.MatchString(s.Name):
		problems = append(problems, fmt.Errorf("%s: name must match %s", where, topicNamePattern))
	case seen[s.Name] || seen[s.Name+deadLetterSuffix]:
		problems = append(problems, fmt.Errorf("%s: duplicate topic name", where))
	}
	seen[s.Name] = true
	if s.DeadLetter {
		seen[s.Name+deadLetterSuffix] = true
	}
	if s.Key == "" {
		problems = append(problems, fmt.Errorf("%s: key must name the field that orders records", where))
	}
	if s.Partitions < 0 {
		problems = append(problems, fmt.Errorf("%s: partitions must be positive", where))
	}
	if s.Retention.Duration < 0 {
		problems = append(problems, fmt.Errorf("%s: retention must be positive", where))
	}
	if len(s.Producers) == 0 || len(s.Consumers) == 0 {
		problems = append(problems, fmt.Errorf("%s: needs at least one producer and one consumer", where))
	}
	for _, service := range slices.Concat(s.Producers, s.Consumers) {
		if !serviceNamePattern.MatchString(service) {
			problems = append(problems, fmt.Errorf("%s: service %q must match %s", where, service, serviceNamePattern))
		}
	}
	return problems
}

func valueOr[T comparable](value, fallback T) T {
	var zero T
	if value == zero {
		return fallback
	}
	return value
}
