package templateloader

import (
	"flag"
	"os"
	"path/filepath"
	"strings"
	"testing"

	"github.com/jevzo/ogcloud/data-plane/pkg/runtimeprofiles"
)

var update = flag.Bool("update", false, "rewrite golden files")

func TestOverlaysPatchTemplateFilesAndKeepTheRest(t *testing.T) {
	root, dir := dataRoot(t)
	for _, name := range []string{"server.properties", "spigot.yml"} {
		content, err := os.ReadFile(filepath.Join("testdata", name+".in"))
		if err != nil {
			t.Fatal(err)
		}
		if err := os.WriteFile(filepath.Join(dir, name), content, 0o600); err != nil {
			t.Fatal(err)
		}
	}

	if err := applyOverlays(root, runtimeprofiles.OverlaysOf(runtimeprofiles.PlatformPaper)); err != nil {
		t.Fatalf("applyOverlays: %v", err)
	}

	for _, name := range []string{"server.properties", "spigot.yml"} {
		golden(t, filepath.Join(dir, name), filepath.Join("testdata", name+".golden"))
	}
}

func TestOverlaysCreateMissingFiles(t *testing.T) {
	root, dir := dataRoot(t)

	if err := applyOverlays(root, runtimeprofiles.OverlaysOf(runtimeprofiles.PlatformPandaSpigot)); err != nil {
		t.Fatalf("applyOverlays: %v", err)
	}

	golden(t, filepath.Join(dir, "server.properties"), filepath.Join("testdata", "server.properties.new.golden"))
	golden(t, filepath.Join(dir, "spigot.yml"), filepath.Join("testdata", "spigot.yml.new.golden"))
}

func TestApplyingOverlaysTwiceChangesNothing(t *testing.T) {
	root, dir := dataRoot(t)
	overlays := runtimeprofiles.OverlaysOf(runtimeprofiles.PlatformPaper)
	if err := applyOverlays(root, overlays); err != nil {
		t.Fatal(err)
	}
	first, _ := os.ReadFile(filepath.Join(dir, "spigot.yml"))

	if err := applyOverlays(root, overlays); err != nil {
		t.Fatal(err)
	}

	if second, _ := os.ReadFile(filepath.Join(dir, "spigot.yml")); string(second) != string(first) {
		t.Errorf("second run changed spigot.yml:\n%s\nvs\n%s", first, second)
	}
}

func golden(t *testing.T, gotPath, goldenPath string) {
	t.Helper()
	got, err := os.ReadFile(gotPath)
	if err != nil {
		t.Fatal(err)
	}
	if *update {
		if err := os.WriteFile(goldenPath, got, 0o600); err != nil {
			t.Fatal(err)
		}
	}
	want, err := os.ReadFile(goldenPath)
	if err != nil {
		t.Fatalf("read golden: %v", err)
	}
	if string(got) != string(want) {
		t.Errorf("%s differs from %s; rerun with -update after reviewing\n%s", gotPath, goldenPath, got)
	}
}

func TestDuplicateYAMLKeysAreAllSet(t *testing.T) {
	bungeecord := runtimeprofiles.Overlay{File: "spigot.yml", Format: runtimeprofiles.OverlayYAML, Key: "settings.bungeecord", Value: "true"}
	patched, err := setYAML([]byte("settings:\n  bungeecord: false\n  bungeecord: false\n"), []runtimeprofiles.Overlay{bungeecord})
	if err != nil {
		t.Fatal(err)
	}
	if strings.Count(string(patched), "bungeecord: true") != 2 {
		t.Errorf("patched:\n%s", patched)
	}
}
