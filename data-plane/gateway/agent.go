package gateway

import (
	"context"
	"regexp"

	"google.golang.org/grpc/codes"
	"google.golang.org/grpc/status"

	internalv1 "github.com/jevzo/ogcloud/data-plane/pkg/gen/proto/ogcloud/internalapi/v1"
	nodev1 "github.com/jevzo/ogcloud/data-plane/pkg/gen/proto/ogcloud/node/v1"
	"github.com/jevzo/ogcloud/data-plane/pkg/templateobjects"
	"github.com/jevzo/ogcloud/data-plane/pkg/valkeykeys"
)

var sha256Hex = regexp.MustCompile(`^[0-9a-f]{64}$`)

// AgentService serves the template loader and the push agent of a game pod (D-0080, D-0081).
type AgentService struct {
	nodev1.UnimplementedTemplateAgentServiceServer
	Dependencies
}

// NewAgentService serves template agents.
func NewAgentService(dependencies Dependencies) *AgentService {
	return &AgentService{Dependencies: dependencies}
}

// GetPodAssets presigns the group's active template and the profile's runtime bundle.
func (a *AgentService) GetPodAssets(ctx context.Context, _ *nodev1.GetPodAssetsRequest) (*nodev1.GetPodAssetsResponse, error) {
	peer, ok := PeerFrom(ctx)
	if !ok {
		return nil, status.Error(codes.Internal, "unauthenticated call")
	}
	response := &nodev1.GetPodAssetsResponse{}
	template := &internalv1.ActiveTemplate{}
	switch err := a.readModel(ctx, valkeykeys.ReadModelActiveTemplate, peer.GroupID, template); status.Code(err) {
	case codes.OK:
		download, err := a.download(ctx, template.GetObjectKey(), template.GetSha256(), template.GetSizeBytes())
		if err != nil {
			return nil, err
		}
		response.Template = download
	case codes.NotFound: // The group starts without a template.
	default:
		return nil, err
	}
	bundle := &internalv1.RuntimeBundle{}
	if err := a.readModel(ctx, valkeykeys.ReadModelRuntimeBundle, peer.ProfileID, bundle); err != nil {
		// The controller publishes every profile's bundle; until then the loader retries (D-0079).
		return nil, status.Errorf(codes.Unavailable, "runtime bundle of %s: %s", peer.ProfileID, status.Convert(err).Message())
	}
	for _, artifact := range bundle.GetArtifacts() {
		download, err := a.download(ctx, artifact.GetObjectKey(), artifact.GetSha256(), artifact.GetSizeBytes())
		if err != nil {
			return nil, err
		}
		response.Artifacts = append(response.Artifacts, &nodev1.BundleArtifact{Path: artifact.GetPath(), Download: download})
	}
	return response, nil
}

// CreateTemplateUpload presigns an upload into the pod's group (D-0081).
func (a *AgentService) CreateTemplateUpload(ctx context.Context, _ *nodev1.CreateTemplateUploadRequest) (*nodev1.CreateTemplateUploadResponse, error) {
	peer, ok := PeerFrom(ctx)
	if !ok {
		return nil, status.Error(codes.Internal, "unauthenticated call")
	}
	id, err := a.IDs.Next()
	if err != nil {
		return nil, status.Errorf(codes.Internal, "upload id: %v", err)
	}
	url, err := a.Objects.Put(ctx, templateKey(peer.GroupID, id))
	if err != nil {
		return nil, status.Errorf(codes.Unavailable, "presign: %v", err)
	}
	return &nodev1.CreateTemplateUploadResponse{UploadId: id, Url: url}, nil
}

// CompleteTemplateUpload registers an uploaded archive with the controller, which promotes it for STATIC groups.
func (a *AgentService) CompleteTemplateUpload(ctx context.Context, request *nodev1.CompleteTemplateUploadRequest) (*nodev1.CompleteTemplateUploadResponse, error) {
	peer, ok := PeerFrom(ctx)
	if !ok {
		return nil, status.Error(codes.Internal, "unauthenticated call")
	}
	if !uuidText.MatchString(request.GetUploadId()) || !sha256Hex.MatchString(request.GetSha256()) || request.GetSizeBytes() == 0 {
		return nil, status.Error(codes.InvalidArgument, "an upload id, a lower-case SHA-256 and a size are required")
	}
	registered, err := a.Templates.RegisterTemplateVersion(ctx, &internalv1.RegisterTemplateVersionRequest{
		ServerId: peer.ServerID, ObjectKey: templateKey(peer.GroupID, request.GetUploadId()),
		Sha256: request.GetSha256(), SizeBytes: request.GetSizeBytes(),
	})
	if err != nil {
		return nil, controllerError("template registration", err)
	}
	return &nodev1.CompleteTemplateUploadResponse{TemplateVersionId: registered.GetTemplateVersionId(), Promoted: registered.GetPromoted()}, nil
}

func (a *AgentService) download(ctx context.Context, key, sha256 string, size uint64) (*nodev1.Download, error) {
	url, err := a.Objects.Get(ctx, key)
	if err != nil {
		return nil, status.Errorf(codes.Unavailable, "presign: %v", err)
	}
	return &nodev1.Download{Url: url, Sha256: sha256, SizeBytes: size}, nil
}

// templateKey follows the layout the API and the controller use (D-0081).
func templateKey(groupID, id string) string {
	return templateobjects.Prefix + groupID + "/" + id + templateobjects.ArchiveSuffix
}
