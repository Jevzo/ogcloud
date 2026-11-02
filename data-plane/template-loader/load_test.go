package templateloader

import (
	"archive/tar"
	"context"
	"errors"
	"io"
	"log/slog"
	"net/http"
	"net/http/httptest"
	"os"
	"path/filepath"
	"strings"
	"testing"

	"google.golang.org/grpc"

	nodev1 "github.com/jevzo/ogcloud/data-plane/pkg/gen/proto/ogcloud/node/v1"
	"github.com/jevzo/ogcloud/data-plane/pkg/runtimeprofiles"
)

type fakeAssets struct{ response *nodev1.GetPodAssetsResponse }

func (f *fakeAssets) GetPodAssets(context.Context, *nodev1.GetPodAssetsRequest, ...grpc.CallOption) (*nodev1.GetPodAssetsResponse, error) {
	return f.response, nil
}

// storage serves objects by path, like presigned URLs.
func storage(t *testing.T, objects map[string][]byte) *httptest.Server {
	t.Helper()
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		content, ok := objects[r.URL.Path]
		if !ok {
			http.NotFound(w, r)
			return
		}
		_, _ = w.Write(content)
	}))
	t.Cleanup(server.Close)
	return server
}

func download(server *httptest.Server, objectPath string, content []byte) *nodev1.Download {
	return &nodev1.Download{Url: server.URL + objectPath, Sha256: sum(content), SizeBytes: uint64(len(content))}
}

func paper(t *testing.T) runtimeprofiles.Profile {
	t.Helper()
	profile, ok := runtimeprofiles.Find("paper-26.3")
	if !ok {
		t.Fatal("paper-26.3 missing from the catalog")
	}
	return profile
}

func quiet() *slog.Logger { return slog.New(slog.NewTextHandler(io.Discard, nil)) }

func TestLoadingExtractsInstallsAndPatches(t *testing.T) {
	template := archive(t, entry{name: "server.properties", typeflag: tar.TypeReg, content: "motd=hi\nonline-mode=true\n"},
		entry{name: "plugins/Essentials.jar", typeflag: tar.TypeReg, content: "group plugin"})
	jar := []byte("ogcloud plugin")
	server := storage(t, map[string][]byte{"/template": template, "/jar": jar})
	root, dir := dataRoot(t)
	loader := &Loader{
		Assets: &fakeAssets{&nodev1.GetPodAssetsResponse{
			Template:  download(server, "/template", template),
			Artifacts: []*nodev1.BundleArtifact{{Path: "plugins/ogcloud.jar", Download: download(server, "/jar", jar)}},
		}},
		HTTP: server.Client(), Data: root, Profile: paper(t), MaxExtractBytes: 1 << 20, Logger: quiet(),
	}

	if err := loader.Run(context.Background()); err != nil {
		t.Fatalf("Run: %v", err)
	}

	read := func(name string) string {
		content, _ := os.ReadFile(filepath.Join(dir, name))
		return string(content)
	}
	if !strings.Contains(read("server.properties"), "online-mode=false") || read("plugins/Essentials.jar") != "group plugin" ||
		read("plugins/ogcloud.jar") != "ogcloud plugin" || !strings.Contains(read("spigot.yml"), "bungeecord: true") {
		t.Errorf("data directory: %v", read("server.properties"))
	}
	if read(".ogcloud/template.sha256") != sum(template)+"\n" || read(".ogcloud/bundle.txt") != "plugins/ogcloud.jar\n" {
		t.Errorf("state: %q %q", read(".ogcloud/template.sha256"), read(".ogcloud/bundle.txt"))
	}
}

func TestAVolumeHoldingTheTemplateIsKeptAndAnotherTemplateReplacesIt(t *testing.T) {
	first := archive(t, entry{name: "world/level.dat", typeflag: tar.TypeReg, content: "v1"})
	second := archive(t, entry{name: "world/level.dat", typeflag: tar.TypeReg, content: "v2"})
	server := storage(t, map[string][]byte{"/first": first, "/second": second})
	root, dir := dataRoot(t)
	assets := &fakeAssets{&nodev1.GetPodAssetsResponse{Template: download(server, "/first", first)}}
	loader := &Loader{Assets: assets, HTTP: server.Client(), Data: root, Profile: paper(t), MaxExtractBytes: 1 << 20, Logger: quiet()}
	level := filepath.Join(dir, "world", "level.dat")

	if err := loader.Run(context.Background()); err != nil {
		t.Fatal(err)
	}
	// The server played on; the volume is newer than the template it came from.
	if err := os.WriteFile(level, []byte("played"), 0o600); err != nil {
		t.Fatal(err)
	}
	if err := os.WriteFile(filepath.Join(dir, "stray.txt"), []byte("x"), 0o600); err != nil {
		t.Fatal(err)
	}
	if err := loader.Run(context.Background()); err != nil {
		t.Fatal(err)
	}
	if content, _ := os.ReadFile(level); string(content) != "played" {
		t.Errorf("a restart replaced the volume: %q", content)
	}

	assets.response = &nodev1.GetPodAssetsResponse{Template: download(server, "/second", second)}
	if err := loader.Run(context.Background()); err != nil {
		t.Fatal(err)
	}
	if content, _ := os.ReadFile(level); string(content) != "v2" {
		t.Errorf("a promoted template was not extracted: %q", content)
	}
	if _, err := os.Stat(filepath.Join(dir, "stray.txt")); !errors.Is(err, os.ErrNotExist) {
		t.Error("files from before the new template survived")
	}
}

func TestATamperedTemplateLeavesNothingBehind(t *testing.T) {
	template := archive(t, entry{name: "world/level.dat", typeflag: tar.TypeReg, content: "v1"})
	server := storage(t, map[string][]byte{"/t": template})
	root, dir := dataRoot(t)
	tampered := download(server, "/t", template)
	tampered.Sha256 = sum([]byte("something else"))
	loader := &Loader{Assets: &fakeAssets{&nodev1.GetPodAssetsResponse{Template: tampered}}, HTTP: server.Client(), Data: root,
		Profile: paper(t), MaxExtractBytes: 1 << 20, Logger: quiet()}
	// One attempt; Run would retry until its context ends.
	err := loader.load(context.Background())

	if err == nil || !strings.Contains(err.Error(), "checksum") {
		t.Errorf("load = %v", err)
	}
	if entries, _ := os.ReadDir(dir); len(entries) != 0 {
		t.Errorf("left %d entries in the data directory", len(entries))
	}
}

func TestBundlePathsMustStayInside(t *testing.T) {
	root, _ := dataRoot(t)
	loader := &Loader{Assets: &fakeAssets{&nodev1.GetPodAssetsResponse{Artifacts: []*nodev1.BundleArtifact{{Path: "../escape.jar"}}}},
		Data: root, Profile: paper(t), Logger: quiet()}

	if err := loader.Run(context.Background()); !errors.Is(err, ErrUnsafe) {
		t.Errorf("Run = %v", err)
	}
}
