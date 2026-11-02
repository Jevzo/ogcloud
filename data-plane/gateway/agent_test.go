package gateway

import (
	"bytes"
	"context"
	"crypto/sha256"
	"encoding/hex"
	"io"
	"net/http"
	"strings"
	"testing"
	"time"

	"github.com/aws/aws-sdk-go-v2/aws"
	"github.com/aws/aws-sdk-go-v2/service/s3"
	"google.golang.org/grpc/codes"
	"google.golang.org/grpc/status"

	"github.com/jevzo/ogcloud/data-plane/internal/tests3"
	internalv1 "github.com/jevzo/ogcloud/data-plane/pkg/gen/proto/ogcloud/internalapi/v1"
	nodev1 "github.com/jevzo/ogcloud/data-plane/pkg/gen/proto/ogcloud/node/v1"
	"github.com/jevzo/ogcloud/data-plane/pkg/objects"
	"github.com/jevzo/ogcloud/data-plane/pkg/valkeykeys"
)

const archiveSHA = "9f86d081884c7d659a2feaa0c55ad015a3bf4f1b2b0b822cd15d6c15b0f00a08"

func TestPodsGetTheirGroupsTemplateAndTheirProfilesBundle(t *testing.T) {
	h := start(t, patience)
	h.state.models[string(valkeykeys.ReadModelActiveTemplate)+":lobby"] = marshal(t, &internalv1.ActiveTemplate{
		GroupId: "lobby", ObjectKey: "templates/lobby/t.tar.gz", Sha256: archiveSHA, SizeBytes: 4,
	})
	h.state.models[string(valkeykeys.ReadModelRuntimeBundle)+":paper-26.3"] = marshal(t, &internalv1.RuntimeBundle{
		Artifacts: []*internalv1.RuntimeBundleArtifact{{Path: "plugins/ogcloud.jar", ObjectKey: "runtime/ogcloud.jar", Sha256: archiveSHA, SizeBytes: 9}},
	})

	assets, err := h.agents.GetPodAssets(as(t, lobbyToken), &nodev1.GetPodAssetsRequest{})
	if err != nil {
		t.Fatalf("GetPodAssets: %v", err)
	}
	if assets.GetTemplate().GetUrl() != "get:templates/lobby/t.tar.gz" || assets.GetTemplate().GetSha256() != archiveSHA {
		t.Errorf("template = %v", assets.GetTemplate())
	}
	if len(assets.GetArtifacts()) != 1 || assets.GetArtifacts()[0].GetPath() != "plugins/ogcloud.jar" ||
		assets.GetArtifacts()[0].GetDownload().GetUrl() != "get:runtime/ogcloud.jar" {
		t.Errorf("artifacts = %v", assets.GetArtifacts())
	}
	// The proxy's group has no template and its profile no bundle yet.
	if _, err := h.agents.GetPodAssets(as(t, proxyToken), &nodev1.GetPodAssetsRequest{}); status.Code(err) != codes.Unavailable {
		t.Errorf("without a bundle: %v", err)
	}
}

func TestUploadsLandInTheGroupAndAreRegisteredForTheServer(t *testing.T) {
	h := start(t, patience)
	ctx := as(t, lobbyToken)

	upload, err := h.agents.CreateTemplateUpload(ctx, &nodev1.CreateTemplateUploadRequest{})
	if err != nil {
		t.Fatalf("CreateTemplateUpload: %v", err)
	}
	completed, err := h.agents.CompleteTemplateUpload(ctx, &nodev1.CompleteTemplateUploadRequest{UploadId: upload.GetUploadId(), Sha256: archiveSHA, SizeBytes: 4})
	if err != nil {
		t.Fatalf("CompleteTemplateUpload: %v", err)
	}
	_, invalid := h.agents.CompleteTemplateUpload(ctx, &nodev1.CompleteTemplateUploadRequest{UploadId: "../bedwars/x", Sha256: archiveSHA, SizeBytes: 4})

	key := "templates/lobby/" + upload.GetUploadId() + ".tar.gz"
	if upload.GetUrl() != "put:"+key || completed.GetTemplateVersionId() != "v-1" {
		t.Errorf("upload %v, completed %v", upload, completed)
	}
	if len(h.controller.registrations) != 1 || h.controller.registrations[0].GetObjectKey() != key || h.controller.registrations[0].GetServerId() != "s-lobby" {
		t.Errorf("registered %v", h.controller.registrations)
	}
	if status.Code(invalid) != codes.InvalidArgument {
		t.Errorf("an escaping upload id: %v", invalid)
	}
}

func TestPresignedAssetsAndUploadsWorkAgainstSeaweedFS(t *testing.T) {
	ctx := context.Background()
	endpoint := tests3.Start(t)
	put(t, tests3.Client(endpoint), "templates/lobby/t.tar.gz", "test")
	state := &fakeState{models: map[string][]byte{
		string(valkeykeys.ReadModelActiveTemplate) + ":lobby":     marshal(t, &internalv1.ActiveTemplate{ObjectKey: "templates/lobby/t.tar.gz", Sha256: archiveSHA, SizeBytes: 4}),
		string(valkeykeys.ReadModelRuntimeBundle) + ":paper-26.3": marshal(t, &internalv1.RuntimeBundle{}),
	}}
	dependencies := testDependencies(state, &recorder{events: make(chan published, 1)}, time.Now)
	controller := &fakeController{}
	dependencies.Templates = controller
	dependencies.Objects = objects.NewPresigner(objects.Settings{
		Endpoint: endpoint, Region: tests3.Region, Bucket: tests3.Bucket, AccessKey: tests3.AccessKey, SecretKey: tests3.SecretKey, PathStyle: true,
	}, time.Minute)
	agent := NewAgentService(dependencies)
	pod := context.WithValue(ctx, peerKey{}, Peer{ServerID: "s-lobby", GroupID: "lobby", ProfileID: "paper-26.3"})

	assets, err := agent.GetPodAssets(pod, &nodev1.GetPodAssetsRequest{})
	if err != nil {
		t.Fatalf("GetPodAssets: %v", err)
	}
	if body := fetch(t, http.MethodGet, assets.GetTemplate().GetUrl(), nil); body != "test" || sha(body) != assets.GetTemplate().GetSha256() {
		t.Errorf("downloaded %q", body)
	}
	upload, err := agent.CreateTemplateUpload(pod, &nodev1.CreateTemplateUploadRequest{})
	if err != nil {
		t.Fatalf("CreateTemplateUpload: %v", err)
	}
	fetch(t, http.MethodPut, upload.GetUrl(), []byte("pushed"))
	if _, err := agent.CompleteTemplateUpload(pod, &nodev1.CompleteTemplateUploadRequest{UploadId: upload.GetUploadId(), Sha256: sha("pushed"), SizeBytes: 6}); err != nil {
		t.Fatalf("CompleteTemplateUpload: %v", err)
	}

	key := controller.registrations[0].GetObjectKey()
	object, err := tests3.Client(endpoint).GetObject(ctx, &s3.GetObjectInput{Bucket: aws.String(tests3.Bucket), Key: aws.String(key)})
	if err != nil {
		t.Fatalf("the pushed archive is not at %s: %v", key, err)
	}
	defer func() { _ = object.Body.Close() }()
	if content, _ := io.ReadAll(object.Body); string(content) != "pushed" {
		t.Errorf("stored %q", content)
	}
}

func put(t *testing.T, client *s3.Client, key, content string) {
	t.Helper()
	_, err := client.PutObject(context.Background(), &s3.PutObjectInput{Bucket: aws.String(tests3.Bucket), Key: aws.String(key), Body: strings.NewReader(content)})
	if err != nil {
		t.Fatalf("put %s: %v", key, err)
	}
}

// fetch sends body to url and returns the response body; anything but 200 fails the test.
func fetch(t *testing.T, method, url string, body []byte) string {
	t.Helper()
	request, err := http.NewRequestWithContext(context.Background(), method, url, bytes.NewReader(body))
	if err != nil {
		t.Fatal(err)
	}
	response, err := http.DefaultClient.Do(request)
	if err != nil {
		t.Fatalf("%s: %v", method, err)
	}
	defer func() { _ = response.Body.Close() }()
	content, err := io.ReadAll(response.Body)
	if err != nil || response.StatusCode != http.StatusOK {
		t.Fatalf("%s %s: %d %s %v", method, url, response.StatusCode, content, err)
	}
	return string(content)
}

func sha(content string) string {
	sum := sha256.Sum256([]byte(content))
	return hex.EncodeToString(sum[:])
}
