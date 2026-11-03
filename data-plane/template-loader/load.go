package templateloader

import (
	"context"
	"errors"
	"fmt"
	"io"
	"io/fs"
	"log/slog"
	"net/http"
	"os"
	"path"
	"slices"
	"strings"
	"time"

	"google.golang.org/grpc"
	"google.golang.org/grpc/codes"
	"google.golang.org/grpc/status"

	nodev1 "github.com/jevzo/ogcloud/data-plane/pkg/gen/proto/ogcloud/node/v1"
	"github.com/jevzo/ogcloud/data-plane/pkg/runtimeprofiles"
)

// Files in the state directory.
var (
	templateMarker = path.Join(stateDir, "template.sha256")
	bundleList     = path.Join(stateDir, "bundle.txt")
)

const (
	firstRetry = time.Second
	lastRetry  = 30 * time.Second
)

// AssetSource is the gateway's template agent service.
type AssetSource interface {
	GetPodAssets(ctx context.Context, in *nodev1.GetPodAssetsRequest, opts ...grpc.CallOption) (*nodev1.GetPodAssetsResponse, error)
}

// Loader fills the data directory before the server starts.
type Loader struct {
	Assets  AssetSource
	HTTP    *http.Client
	Data    *os.Root
	Profile runtimeprofiles.Profile
	// Template content written at most, against archives that unpack without end.
	MaxExtractBytes int64
	Logger          *slog.Logger
}

// Run loads until it succeeds, fails for good, or ctx ends; the pod does not restart a failed init
// container, so transient failures are retried here.
func (l *Loader) Run(ctx context.Context) error {
	wait := firstRetry
	for {
		err := l.load(ctx)
		if err == nil || permanent(err) {
			return err
		}
		l.Logger.Warn("loading failed, retrying", "error", err, "in", wait)
		select {
		case <-ctx.Done():
			return errors.Join(err, ctx.Err())
		case <-time.After(wait):
		}
		wait = min(2*wait, lastRetry)
	}
}

func (l *Loader) load(ctx context.Context) error {
	assets, err := l.Assets.GetPodAssets(ctx, &nodev1.GetPodAssetsRequest{})
	if err != nil {
		return fmt.Errorf("pod assets: %w", err)
	}
	previous, err := l.bundle()
	if err != nil {
		return err
	}
	if template := assets.GetTemplate(); template != nil {
		if err := l.template(ctx, template); err != nil {
			return err
		}
	}
	paths := make([]string, 0, len(assets.GetArtifacts()))
	for _, artifact := range assets.GetArtifacts() {
		name := path.Clean(artifact.GetPath())
		if !fs.ValidPath(name) || name == "." || strings.HasPrefix(name, stateDir) {
			return fmt.Errorf("%w: bundle path %q", ErrUnsafe, artifact.GetPath())
		}
		if err := install(ctx, l.HTTP, l.Data, name, artifact.GetDownload()); err != nil {
			return err
		}
		paths = append(paths, name)
	}
	// Files the bundle no longer carries would otherwise stay on a STATIC volume and end up in its pushes.
	for _, stale := range previous {
		if !slices.Contains(paths, stale) {
			if err := l.Data.Remove(stale); err != nil && !errors.Is(err, fs.ErrNotExist) {
				return fmt.Errorf("remove the old bundle file %s: %w", stale, err)
			}
		}
	}
	// The agent leaves bundle files out of pushes (D-0119).
	if err := writeState(l.Data, bundleList, strings.Join(paths, "\n")); err != nil {
		return err
	}
	if err := applyOverlays(l.Data, runtimeprofiles.OverlaysOf(l.Profile.Platform)); err != nil {
		// The template's file cannot be patched; it stays broken until the template changes.
		return fmt.Errorf("%w: %w", ErrPermanent, err)
	}
	return nil
}

// bundle returns the bundle files installed last time.
func (l *Loader) bundle() ([]string, error) {
	list, err := l.Data.ReadFile(bundleList)
	if errors.Is(err, fs.ErrNotExist) {
		return nil, nil
	}
	if err != nil {
		return nil, fmt.Errorf("read bundle list: %w", err)
	}
	return strings.Fields(string(list)), nil
}

// permanent tells failures a retry cannot fix from outages.
func permanent(err error) bool {
	if errors.Is(err, ErrUnsafe) || errors.Is(err, ErrPermanent) {
		return true
	}
	switch status.Code(err) {
	case codes.PermissionDenied, codes.Unauthenticated, codes.InvalidArgument, codes.NotFound, codes.FailedPrecondition:
		return true
	default:
		return false
	}
}

// template extracts the group's template unless the data directory already holds it, as a STATIC volume
// does after a restart; a different template replaces everything (D-0119).
func (l *Loader) template(ctx context.Context, template *nodev1.Download) error {
	if current, err := l.Data.ReadFile(templateMarker); err == nil && strings.TrimSpace(string(current)) == template.GetSha256() {
		l.Logger.Info("the data directory already holds the template", "sha256", template.GetSha256())
		return nil
	} else if err != nil && !errors.Is(err, fs.ErrNotExist) {
		return fmt.Errorf("read marker: %w", err)
	}
	if err := wipe(l.Data); err != nil {
		return err
	}
	err := fetch(ctx, l.HTTP, template, func(body io.Reader) error { return extract(l.Data, body, l.MaxExtractBytes) })
	if err != nil {
		// A half-written or tampered template must not be mistaken for data on the next try.
		return errors.Join(fmt.Errorf("template: %w", err), wipe(l.Data))
	}
	l.Logger.Info("template extracted", "sha256", template.GetSha256(), "bytes", template.GetSizeBytes())
	return writeState(l.Data, templateMarker, template.GetSha256())
}

func writeState(root *os.Root, name, content string) error {
	return replaceFile(root, name, strings.NewReader(content+"\n"))
}
