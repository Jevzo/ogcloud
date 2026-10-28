// Package logging builds the JSON logger every data-plane component writes to stdout, where Loki picks it up
// (D-0025).
package logging

import (
	"fmt"
	"io"
	"log/slog"
)

// New returns a JSON logger tagged with the component, at level debug, info, warn or error.
func New(w io.Writer, component, level string) (*slog.Logger, error) {
	var parsed slog.Level
	if err := parsed.UnmarshalText([]byte(level)); err != nil {
		return nil, fmt.Errorf("log level %q: %w", level, err)
	}
	handler := slog.NewJSONHandler(w, &slog.HandlerOptions{Level: parsed})
	return slog.New(handler).With("component", component), nil
}
