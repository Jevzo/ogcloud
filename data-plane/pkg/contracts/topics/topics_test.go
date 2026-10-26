package topics

import (
	"flag"
	"os"
	"path/filepath"
	"strings"
	"testing"
	"time"
)

var update = flag.Bool("update", false, "rewrite golden files")

func TestRenderersMatchGoldenFiles(t *testing.T) {
	topics := resolveFixture(t)
	goSource, err := RenderGo("topics", topics)
	if err != nil {
		t.Fatalf("RenderGo: %v", err)
	}

	tests := []struct {
		golden string
		got    []byte
	}{
		{"topics.go.golden", goSource},
		{"KafkaTopics.kt.golden", RenderKotlin("io.ogwars.cloud.messaging", topics)},
		{"strimzi.yaml.golden", RenderStrimzi(topics)},
	}
	for _, tt := range tests {
		t.Run(tt.golden, func(t *testing.T) {
			path := filepath.Join("testdata", tt.golden)
			if *update {
				if err := os.WriteFile(path, tt.got, 0o600); err != nil {
					t.Fatalf("write golden: %v", err)
				}
			}
			want, err := os.ReadFile(path)
			if err != nil {
				t.Fatalf("read golden: %v", err)
			}
			if string(tt.got) != string(want) {
				t.Errorf("output differs from %s; rerun with -update after reviewing\n--- got ---\n%s", path, tt.got)
			}
		})
	}
}

func TestResolveAppliesDefaultsAndExpandsDeadLetters(t *testing.T) {
	topics := resolveFixture(t)

	if len(topics) != 3 {
		t.Fatalf("got %d topics, want 3", len(topics))
	}
	lifecycle, connect, deadLetter := topics[0], topics[1], topics[2]
	if lifecycle.Partitions != 12 || lifecycle.Retention != 7*day {
		t.Errorf("lifecycle defaults not applied: %+v", lifecycle)
	}
	if connect.Partitions != 24 || connect.Retention != 12*time.Hour {
		t.Errorf("connect overrides not applied: %+v", connect)
	}
	if deadLetter.Name != "ogcloud.player.connect.dlt" || deadLetter.Retention != 14*day ||
		len(deadLetter.Producers) != 1 || deadLetter.Producers[0] != "controller" || len(deadLetter.Consumers) != 0 {
		t.Errorf("dead-letter topic wrong: %+v", deadLetter)
	}
}

func TestResolveRejectsInvalidCatalogs(t *testing.T) {
	valid := "defaults: {partitions: 3, retention: 1d, deadLetterRetention: 2d}\ntopics:\n"
	const msg = "message: ogcloud.events.v1.Test, "
	tests := []struct {
		name    string
		yaml    string
		wantErr string
	}{
		{"bad name", valid + "  - {name: Server.Lifecycle, " + msg + "key: id, producers: [a], consumers: [b]}", "name must match"},
		{"duplicate", valid + "  - {name: ogcloud.a, " + msg + "key: id, producers: [a], consumers: [b]}\n  - {name: ogcloud.a, " + msg + "key: id, producers: [a], consumers: [b]}", "duplicate topic name"},
		{"dlt clash", valid + "  - {name: ogcloud.a, " + msg + "key: id, producers: [a], consumers: [b], deadLetter: true}\n  - {name: ogcloud.a.dlt, " + msg + "key: id, producers: [a], consumers: [b]}", "duplicate topic name"},
		{"missing key", valid + "  - {name: ogcloud.a, " + msg + "producers: [a], consumers: [b]}", "key must name"},
		{"no consumer", valid + "  - {name: ogcloud.a, " + msg + "key: id, producers: [a]}", "at least one producer and one consumer"},
		{"bad service", valid + "  - {name: ogcloud.a, " + msg + "key: id, producers: [Api], consumers: [b]}", `service "Api"`},
		{"bad message", valid + "  - {name: ogcloud.a, message: ServerCommand, key: id, producers: [a], consumers: [b]}", "fully qualified Protobuf message"},
		{"no defaults", "topics:\n  - {name: ogcloud.a, " + msg + "key: id, producers: [a], consumers: [b]}", "defaults.partitions must be positive"},
		{"dlt without retention", "defaults: {partitions: 3, retention: 1d}\ntopics:\n  - {name: ogcloud.a, " + msg + "key: id, producers: [a], consumers: [b], deadLetter: true}", "deadLetterRetention must be positive"},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			catalog, err := Parse([]byte(tt.yaml))
			if err != nil {
				t.Fatalf("Parse: %v", err)
			}
			_, err = catalog.Resolve()
			if err == nil || !strings.Contains(err.Error(), tt.wantErr) {
				t.Errorf("Resolve error = %v, want it to contain %q", err, tt.wantErr)
			}
		})
	}
}

func TestParseRejectsUnknownFields(t *testing.T) {
	_, err := Parse([]byte("defaults: {partitions: 3, retention: 1d}\ntopics:\n  - {name: ogcloud.a, partition: 3}"))
	if err == nil || !strings.Contains(err.Error(), "partition") {
		t.Errorf("Parse error = %v, want unknown field error", err)
	}
}

func TestParseRetention(t *testing.T) {
	tests := []struct {
		in      string
		want    time.Duration
		wantErr bool
	}{
		{"7d", 7 * day, false},
		{"12h", 12 * time.Hour, false},
		{"90m", 90 * time.Minute, false},
		{"xd", 0, true},
		{"seven", 0, true},
	}
	for _, tt := range tests {
		t.Run(tt.in, func(t *testing.T) {
			got, err := parseRetention(tt.in)
			if (err != nil) != tt.wantErr || got != tt.want {
				t.Errorf("parseRetention(%q) = %v, %v; want %v, error %v", tt.in, got, err, tt.want, tt.wantErr)
			}
		})
	}
}

func resolveFixture(t *testing.T) []Topic {
	t.Helper()
	data, err := os.ReadFile(filepath.Join("testdata", "topics.yaml"))
	if err != nil {
		t.Fatalf("read fixture: %v", err)
	}
	catalog, err := Parse(data)
	if err != nil {
		t.Fatalf("Parse: %v", err)
	}
	topics, err := catalog.Resolve()
	if err != nil {
		t.Fatalf("Resolve: %v", err)
	}
	return topics
}
