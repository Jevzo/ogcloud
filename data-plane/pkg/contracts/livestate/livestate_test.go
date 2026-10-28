package livestate

import (
	"flag"
	"os"
	"path/filepath"
	"strings"
	"testing"
)

var update = flag.Bool("update", false, "rewrite golden files")

func TestRenderersMatchGoldenFiles(t *testing.T) {
	catalog := fixture(t)
	goSource, err := RenderGo("valkeykeys", catalog)
	if err != nil {
		t.Fatalf("RenderGo: %v", err)
	}
	kotlin, err := RenderKotlin("io.ogwars.cloud.livestate", catalog)
	if err != nil {
		t.Fatalf("RenderKotlin: %v", err)
	}

	tests := []struct {
		golden string
		got    []byte
	}{
		{"keys.go.golden", goSource},
		{"ValkeyKeys.kt.golden", kotlin},
		{"LiveStateKinds.kt.golden", RenderKotlinKinds("io.ogwars.cloud.domain.store", catalog)},
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

func TestSegmentsSplitLiteralsAndParameters(t *testing.T) {
	segments, err := Key{Name: "rateLimit", Pattern: "ratelimit:{bucket:string}:{client:string}"}.Segments()
	if err != nil {
		t.Fatalf("Segments: %v", err)
	}
	var parts []string
	for _, segment := range segments {
		if segment.Param != nil {
			parts = append(parts, "<"+segment.Param.Name+":"+string(segment.Param.Type)+">")
		} else {
			parts = append(parts, segment.Literal)
		}
	}
	if got := strings.Join(parts, ""); got != "ratelimit:<bucket:string>:<client:string>" {
		t.Errorf("got %s", got)
	}
}

func TestValidateRejectsBrokenCatalogs(t *testing.T) {
	tests := []struct {
		name    string
		catalog Catalog
		want    string
	}{
		{"missing fields", Catalog{}, "revision and data are required"},
		{"unknown parameter type", withKey(Key{Name: "x", Pattern: "x:{id:number}"}), "unknown parameter type"},
		{"unclosed parameter", withKey(Key{Name: "x", Pattern: "x:{id:uuid"}), "malformed parameter"},
		{"prefix without literal", withKey(Key{Name: "x", Pattern: "{id:uuid}", Prefix: true}), "needs literal text"},
		{"duplicate key", withKey(Key{Name: "x", Pattern: "a"}, Key{Name: "x", Pattern: "b"}), "declared twice"},
		{"bad name", withKey(Key{Name: "X", Pattern: "a"}), "lowerCamelCase"},
		{"taken kind key", Catalog{Fields: fields(), ReadModelKinds: []Kind{{Name: "a", Key: "k"}, {Name: "b", Key: "k"}}}, "empty or taken"},
		{"bad duration", Catalog{Fields: fields(), TTLs: []TTL{{Name: "a", Duration: "-1m"}}}, "must be positive"},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			err := tt.catalog.Validate()
			if err == nil || !strings.Contains(err.Error(), tt.want) {
				t.Errorf("got %v, want an error containing %q", err, tt.want)
			}
		})
	}
}

func TestParseRejectsUnknownFields(t *testing.T) {
	if _, err := Parse([]byte("fields: {revision: rev, data: data}\nextra: 1\n")); err == nil {
		t.Error("an unknown field was accepted")
	}
}

func fixture(t *testing.T) *Catalog {
	t.Helper()
	data, err := os.ReadFile(filepath.Join("testdata", "keys.yaml"))
	if err != nil {
		t.Fatalf("read fixture: %v", err)
	}
	catalog, err := Parse(data)
	if err != nil {
		t.Fatalf("Parse: %v", err)
	}
	if err := catalog.Validate(); err != nil {
		t.Fatalf("Validate: %v", err)
	}
	return catalog
}

func fields() Fields { return Fields{Revision: "rev", Data: "data"} }

func withKey(keys ...Key) Catalog { return Catalog{Fields: fields(), Keys: keys} }
