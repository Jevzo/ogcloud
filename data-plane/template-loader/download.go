package templateloader

import (
	"context"
	"fmt"
	"io"
	"net/http"
	"os"

	nodev1 "github.com/jevzo/ogcloud/data-plane/pkg/gen/proto/ogcloud/node/v1"
)

// fetch streams a presigned download into consume and checks it against the announced hash and size
// once everything was read.
func fetch(ctx context.Context, client *http.Client, download *nodev1.Download, consume func(io.Reader) error) error {
	request, err := http.NewRequestWithContext(ctx, http.MethodGet, download.GetUrl(), nil)
	if err != nil {
		return fmt.Errorf("download request: %w", err)
	}
	response, err := client.Do(request)
	if err != nil {
		return fmt.Errorf("download: %w", err)
	}
	defer func() { _ = response.Body.Close() }()
	if response.StatusCode != http.StatusOK {
		return fmt.Errorf("download: status %d", response.StatusCode)
	}
	seen := newDigest()
	body := io.TeeReader(response.Body, seen)
	if err := consume(body); err != nil {
		return err
	}
	// The consumer may stop early, e.g. at the archive's end; the rest still counts.
	if _, err := io.Copy(io.Discard, body); err != nil {
		return fmt.Errorf("download: %w", err)
	}
	return seen.check(download.GetSha256(), download.GetSizeBytes())
}

// install downloads a bundle file next to its target and moves it into place only once it checks out.
func install(ctx context.Context, client *http.Client, root *os.Root, name string, download *nodev1.Download) error {
	partial := name + ".ogcloud-partial"
	err := fetch(ctx, client, download, func(body io.Reader) error { return writeFile(root, partial, body) })
	if err != nil {
		_ = root.Remove(partial)
		return fmt.Errorf("%s: %w", name, err)
	}
	if err := root.Rename(partial, name); err != nil {
		return fmt.Errorf("install %s: %w", name, err)
	}
	return nil
}
