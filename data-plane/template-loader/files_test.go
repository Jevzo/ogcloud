package templateloader

import (
	"archive/tar"
	"bytes"
	"compress/gzip"
	"context"
	"crypto/sha256"
	"encoding/hex"
	"errors"
	"net/http"
	"net/http/httptest"
	"os"
	"path/filepath"
	"testing"

	nodev1 "github.com/jevzo/ogcloud/data-plane/pkg/gen/proto/ogcloud/node/v1"
)

type entry struct {
	name     string
	typeflag byte
	content  string
	link     string
}

func archive(t *testing.T, entries ...entry) []byte {
	t.Helper()
	var buffer bytes.Buffer
	zipped := gzip.NewWriter(&buffer)
	writer := tar.NewWriter(zipped)
	for _, e := range entries {
		header := &tar.Header{Name: e.name, Typeflag: e.typeflag, Mode: 0o644, Size: int64(len(e.content)), Linkname: e.link}
		if e.typeflag != tar.TypeReg {
			header.Size = 0
		}
		if err := writer.WriteHeader(header); err != nil {
			t.Fatal(err)
		}
		if _, err := writer.Write([]byte(e.content)); e.typeflag == tar.TypeReg && err != nil {
			t.Fatal(err)
		}
	}
	if err := writer.Close(); err != nil {
		t.Fatal(err)
	}
	if err := zipped.Close(); err != nil {
		t.Fatal(err)
	}
	return buffer.Bytes()
}

func dataRoot(t *testing.T) (*os.Root, string) {
	t.Helper()
	dir := t.TempDir()
	root, err := os.OpenRoot(dir)
	if err != nil {
		t.Fatal(err)
	}
	t.Cleanup(func() { _ = root.Close() })
	return root, dir
}

func sum(content []byte) string {
	digest := sha256.Sum256(content)
	return hex.EncodeToString(digest[:])
}

func TestArchivesAreExtractedIntoTheDataDirectory(t *testing.T) {
	root, dir := dataRoot(t)

	err := extract(root, bytes.NewReader(archive(t,
		entry{name: "world/", typeflag: tar.TypeDir},
		entry{name: "world/level.dat", typeflag: tar.TypeReg, content: "level"},
		entry{name: "./server.properties", typeflag: tar.TypeReg, content: "motd=hi"},
	)), 1<<20)

	if err != nil {
		t.Fatalf("extract: %v", err)
	}
	if content, _ := os.ReadFile(filepath.Join(dir, "world", "level.dat")); string(content) != "level" {
		t.Errorf("level.dat = %q", content)
	}
}

func TestUnsafeArchivesAreRefused(t *testing.T) {
	tests := map[string][]entry{
		"parent":   {{name: "../escape", typeflag: tar.TypeReg, content: "x"}},
		"absolute": {{name: "/etc/passwd", typeflag: tar.TypeReg, content: "x"}},
		"nested":   {{name: "world/../../escape", typeflag: tar.TypeReg, content: "x"}},
		"symlink":  {{name: "plugins", typeflag: tar.TypeSymlink, link: "/etc"}},
		"hardlink": {{name: "passwd", typeflag: tar.TypeLink, link: "/etc/passwd"}},
		"too big":  {{name: "big", typeflag: tar.TypeReg, content: "0123456789"}},
	}
	for name, entries := range tests {
		t.Run(name, func(t *testing.T) {
			root, dir := dataRoot(t)

			err := extract(root, bytes.NewReader(archive(t, entries...)), 8)

			if !errors.Is(err, ErrUnsafe) {
				t.Errorf("extract = %v", err)
			}
			if _, statErr := os.Stat(filepath.Join(filepath.Dir(dir), "escape")); statErr == nil {
				t.Error("a file escaped the data directory")
			}
		})
	}
}

func TestDownloadsMustMatchTheirChecksum(t *testing.T) {
	content := []byte("plugin bytes")
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, _ *http.Request) { _, _ = w.Write(content) }))
	defer server.Close()
	root, dir := dataRoot(t)
	good := &nodev1.Download{Url: server.URL, Sha256: sum(content), SizeBytes: uint64(len(content))}
	bad := &nodev1.Download{Url: server.URL, Sha256: sum([]byte("other")), SizeBytes: uint64(len(content))}

	if err := install(context.Background(), server.Client(), root, "plugins/ogcloud.jar", good); err != nil {
		t.Fatalf("install: %v", err)
	}
	err := install(context.Background(), server.Client(), root, "plugins/tampered.jar", bad)

	if installed, _ := os.ReadFile(filepath.Join(dir, "plugins", "ogcloud.jar")); string(installed) != string(content) {
		t.Errorf("installed %q", installed)
	}
	if err == nil {
		t.Error("a tampered download was accepted")
	}
	if leftovers, _ := os.ReadDir(filepath.Join(dir, "plugins")); len(leftovers) != 1 {
		t.Errorf("plugins holds %d files; the tampered one must not stay", len(leftovers))
	}
}
