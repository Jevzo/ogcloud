package logging

import (
	"bytes"
	"encoding/json"
	"testing"
)

func TestLinesAreJSONWithTheComponentAndHonourTheLevel(t *testing.T) {
	var out bytes.Buffer
	logger, err := New(&out, "gateway", "info")
	if err != nil {
		t.Fatalf("New: %v", err)
	}

	logger.Debug("hidden")
	logger.Info("session opened", "serverId", "abc")

	var line map[string]any
	if err := json.Unmarshal(out.Bytes(), &line); err != nil {
		t.Fatalf("not one JSON line: %q", out.String())
	}
	if line["component"] != "gateway" || line["msg"] != "session opened" || line["serverId"] != "abc" {
		t.Errorf("unexpected line: %v", line)
	}
}

func TestUnknownLevelsAreRefused(t *testing.T) {
	if _, err := New(&bytes.Buffer{}, "gateway", "loud"); err == nil {
		t.Error("an unknown level was accepted")
	}
}
