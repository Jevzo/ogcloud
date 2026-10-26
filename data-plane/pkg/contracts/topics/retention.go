package topics

import (
	"fmt"
	"strconv"
	"strings"
	"time"

	"go.yaml.in/yaml/v3"
)

const day = 24 * time.Hour

// Retention is a duration written like "7d" or "12h" in topics.yaml.
type Retention struct {
	time.Duration
}

// UnmarshalYAML accepts Go durations plus a "d" suffix for whole days.
func (r *Retention) UnmarshalYAML(node *yaml.Node) error {
	parsed, err := parseRetention(node.Value)
	if err != nil {
		return fmt.Errorf("line %d: %w", node.Line, err)
	}
	r.Duration = parsed
	return nil
}

func parseRetention(value string) (time.Duration, error) {
	if days, ok := strings.CutSuffix(value, "d"); ok {
		count, err := strconv.Atoi(days)
		if err != nil {
			return 0, fmt.Errorf("invalid retention %q: %w", value, err)
		}
		return time.Duration(count) * day, nil
	}
	parsed, err := time.ParseDuration(value)
	if err != nil {
		return 0, fmt.Errorf("invalid retention %q: %w", value, err)
	}
	return parsed, nil
}
