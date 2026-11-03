package templateloader

import (
	"context"
	"errors"
	"fmt"
	"io"
	"log/slog"
	"net/http"
	"os"
	"sync"
	"time"

	"google.golang.org/grpc"
	"google.golang.org/grpc/codes"
	"google.golang.org/grpc/status"

	eventsv1 "github.com/jevzo/ogcloud/data-plane/pkg/gen/proto/ogcloud/events/v1"
	nodev1 "github.com/jevzo/ogcloud/data-plane/pkg/gen/proto/ogcloud/node/v1"
)

// SessionOpener opens the node session with the gateway.
type SessionOpener interface {
	Session(ctx context.Context, opts ...grpc.CallOption) (grpc.BidiStreamingClient[nodev1.SessionRequest, nodev1.SessionResponse], error)
}

// Uploads are the gateway's template-upload calls.
type Uploads interface {
	CreateTemplateUpload(ctx context.Context, in *nodev1.CreateTemplateUploadRequest, opts ...grpc.CallOption) (*nodev1.CreateTemplateUploadResponse, error)
	CompleteTemplateUpload(ctx context.Context, in *nodev1.CompleteTemplateUploadRequest, opts ...grpc.CallOption) (*nodev1.CompleteTemplateUploadResponse, error)
}

// Agent holds a TEMPLATE_AGENT session and pushes the server's files as a new template version when asked,
// and on shutdown for STATIC servers (D-0042).
type Agent struct {
	Gateway SessionOpener
	Uploads Uploads
	HTTP    *http.Client
	Data    *os.Root
	// Where archives are built before the upload.
	TmpDir         string
	PushOnShutdown bool
	// How long the push on shutdown may take; the pod's grace period bounds it.
	ShutdownTimeout time.Duration
	Version         string
	Logger          *slog.Logger

	mu sync.Mutex
}

// Run keeps the session open until ctx ends, then pushes once more if the server is STATIC.
func (a *Agent) Run(ctx context.Context) error {
	wait := firstRetry
	for ctx.Err() == nil {
		welcomed, err := a.session(ctx)
		if ctx.Err() != nil {
			break
		}
		if welcomed {
			wait = firstRetry
		}
		// A refused token or pod will not heal by reconnecting; it needs someone to look.
		level := slog.LevelWarn
		if code := status.Code(err); code == codes.PermissionDenied || code == codes.Unauthenticated {
			level = slog.LevelError
		}
		a.Logger.Log(ctx, level, "session ended, reconnecting", "error", err, "in", wait)
		select {
		case <-ctx.Done():
		case <-time.After(wait):
		}
		wait = min(2*wait, lastRetry)
	}
	if !a.PushOnShutdown {
		return nil
	}
	pushCtx, cancel := context.WithTimeout(context.WithoutCancel(ctx), a.ShutdownTimeout)
	defer cancel()
	if _, err := a.Push(pushCtx); err != nil {
		return fmt.Errorf("push on shutdown: %w", err)
	}
	a.Logger.Info("pushed on shutdown")
	return nil
}

// session runs one stream; it reports whether the gateway welcomed it.
func (a *Agent) session(ctx context.Context) (bool, error) {
	stream, err := a.Gateway.Session(ctx)
	if err != nil {
		return false, fmt.Errorf("open session: %w", err)
	}
	err = stream.Send(&nodev1.SessionRequest{Message: &nodev1.SessionRequest_Hello{Hello: &nodev1.Hello{
		ProtocolVersion: nodev1.ProtocolVersion_PROTOCOL_VERSION_1, NodeType: eventsv1.NodeType_NODE_TYPE_TEMPLATE_AGENT,
		IntegrationVersion: a.Version,
	}}})
	if err != nil {
		return false, fmt.Errorf("hello: %w", err)
	}
	welcome, err := stream.Recv()
	if err != nil || welcome.GetWelcome() == nil {
		return false, errors.Join(errors.New("no welcome"), err)
	}
	for {
		response, err := stream.Recv()
		if err != nil {
			return true, fmt.Errorf("receive: %w", err)
		}
		delivery := response.GetDelivery()
		if delivery == nil {
			continue
		}
		result := &nodev1.DeliveryResult{DeliveryId: delivery.GetDeliveryId(), Message: "template agents only push templates"}
		if delivery.GetPushTemplate() != nil {
			if version, err := a.Push(ctx); err != nil {
				a.Logger.Error("push failed", "error", err)
				result.Message = err.Error()
			} else {
				result.Success, result.Message = true, version
			}
		}
		if err := stream.Send(&nodev1.SessionRequest{Message: &nodev1.SessionRequest_DeliveryResult{DeliveryResult: result}}); err != nil {
			return true, fmt.Errorf("delivery result: %w", err)
		}
	}
}

// Push archives the data directory, uploads it and registers it; it returns the new version's id.
func (a *Agent) Push(ctx context.Context) (string, error) {
	a.mu.Lock()
	defer a.mu.Unlock()
	skip, err := newExcluder(a.Data)
	if err != nil {
		return "", err
	}
	archive, err := os.CreateTemp(a.TmpDir, "template-*.tar.gz")
	if err != nil {
		return "", fmt.Errorf("archive: %w", err)
	}
	defer func() {
		_ = archive.Close()
		_ = os.Remove(archive.Name())
	}()
	written := newDigest()
	skipped, err := pack(a.Data, io.MultiWriter(archive, written), skip)
	if err != nil {
		return "", err
	}
	if len(skipped) > 0 {
		a.Logger.Info("links and special files left out of the push", "paths", skipped)
	}
	upload, err := a.Uploads.CreateTemplateUpload(ctx, &nodev1.CreateTemplateUploadRequest{})
	if err != nil {
		return "", fmt.Errorf("create upload: %w", err)
	}
	if err := a.put(ctx, upload.GetUrl(), archive, written.size); err != nil {
		return "", err
	}
	completed, err := a.Uploads.CompleteTemplateUpload(ctx, &nodev1.CompleteTemplateUploadRequest{
		UploadId: upload.GetUploadId(), Sha256: written.sum(), SizeBytes: uint64(written.size), //nolint:gosec // G115: sizes are never negative.
	})
	if err != nil {
		return "", fmt.Errorf("complete upload: %w", err)
	}
	// The volume now holds the active template, so the next start keeps it (D-0119). Without the marker the
	// next start extracts that same template again, which costs time but loses nothing.
	if completed.GetPromoted() {
		if err := writeState(a.Data, templateMarker, written.sum()); err != nil {
			a.Logger.Error("pushed, but the volume is not marked as holding the template", "error", err)
		}
	}
	a.Logger.Info("template pushed", "version", completed.GetTemplateVersionId(), "promoted", completed.GetPromoted(), "bytes", written.size)
	return completed.GetTemplateVersionId(), nil
}

func (a *Agent) put(ctx context.Context, url string, archive *os.File, size int64) error {
	if _, err := archive.Seek(0, io.SeekStart); err != nil {
		return fmt.Errorf("rewind archive: %w", err)
	}
	request, err := http.NewRequestWithContext(ctx, http.MethodPut, url, archive)
	if err != nil {
		return fmt.Errorf("upload request: %w", err)
	}
	request.ContentLength = size
	response, err := a.HTTP.Do(request)
	if err != nil {
		return fmt.Errorf("upload: %w", err)
	}
	defer func() { _ = response.Body.Close() }()
	if response.StatusCode != http.StatusOK {
		return fmt.Errorf("upload: status %d", response.StatusCode)
	}
	return nil
}
