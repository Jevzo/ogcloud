package deploytest

import (
	"bytes"
	"context"
	"net/http"
	"strings"
	"testing"
	"time"

	"github.com/aws/aws-sdk-go-v2/aws"
	"github.com/aws/aws-sdk-go-v2/credentials"
	"github.com/aws/aws-sdk-go-v2/service/s3"
	"github.com/testcontainers/testcontainers-go"
	"github.com/testcontainers/testcontainers-go/network"
	"github.com/testcontainers/testcontainers-go/wait"

	"github.com/jevzo/ogcloud/data-plane/internal/testvalkey"
	"github.com/jevzo/ogcloud/data-plane/pkg/objects"
)

// SeaweedFS runs here as the rendered chart runs it: the subchart's all-in-one container with the S3 users
// synced from Infisical, then the Job that creates OgCloud's bucket (D-0010, D-0146).

const (
	s3AccessKey = "ogcloud-access-key"
	s3SecretKey = "ogcloud-secret-key"
	s3Region    = "us-east-1"
)

func TestSeaweedFSServesOgCloudsIdentityOnlyItsBucket(t *testing.T) {
	ctx := context.Background()
	objectsInChart := render(t, minikubeValues)
	server := startSeaweedFS(t, objectsInChart)
	for range 2 {
		runBucketJob(t, objectsInChart, server.network)
	}

	client := s3.New(s3.Options{
		BaseEndpoint: aws.String(server.endpoint), Region: s3Region, UsePathStyle: true,
		Credentials: credentials.NewStaticCredentialsProvider(s3AccessKey, s3SecretKey, ""),
	})
	name := aws.String(chartBucket(t, objectsInChart))
	key := aws.String("templates/lobby/probe.tar.gz")
	if _, err := client.PutObject(ctx, &s3.PutObjectInput{Bucket: name, Key: key, Body: strings.NewReader("probe")}); err != nil {
		t.Fatalf("put: %v", err)
	}
	listed, err := client.ListObjectsV2(ctx, &s3.ListObjectsV2Input{Bucket: name, Prefix: aws.String("templates/")})
	if err != nil || len(listed.Contents) != 1 {
		t.Fatalf("list: %v, %d objects", err, len(listed.Contents))
	}
	upload, err := client.CreateMultipartUpload(ctx, &s3.CreateMultipartUploadInput{Bucket: name, Key: aws.String("big")})
	if err != nil {
		t.Fatalf("start a multipart upload: %v", err)
	}
	uploads, err := client.ListMultipartUploads(ctx, &s3.ListMultipartUploadsInput{Bucket: name})
	if err != nil || len(uploads.Uploads) != 1 {
		t.Fatalf("list multipart uploads, as the cleanup loop does: %v", err)
	}
	if _, err := client.AbortMultipartUpload(ctx, &s3.AbortMultipartUploadInput{Bucket: name, Key: aws.String("big"), UploadId: upload.UploadId}); err != nil {
		t.Fatalf("abort: %v", err)
	}

	// The gateway hands pods presigned URLs (D-0010).
	presigner := objects.NewPresigner(objects.Settings{
		Endpoint: server.endpoint, Region: s3Region, Bucket: *name, AccessKey: s3AccessKey, SecretKey: s3SecretKey, PathStyle: true,
	}, time.Minute)
	url, err := presigner.Get(ctx, *key)
	if err != nil {
		t.Fatal(err)
	}
	if body := download(t, url); body != "probe" {
		t.Errorf("a presigned download gave %q", body)
	}

	if _, err := client.CreateBucket(ctx, &s3.CreateBucketInput{Bucket: aws.String("elsewhere")}); err == nil {
		t.Error("OgCloud's identity may create buckets")
	}
	if status := anonymousGet(t, server.endpoint+"/"+*name+"/"+*key); status != http.StatusForbidden {
		t.Errorf("an anonymous read answered %d", status)
	}
	if _, err := client.DeleteObject(ctx, &s3.DeleteObjectInput{Bucket: name, Key: key}); err != nil {
		t.Errorf("delete: %v", err)
	}
}

type seaweedFS struct {
	network  string
	endpoint string
}

func startSeaweedFS(t *testing.T, objectsInChart rendered) seaweedFS {
	t.Helper()
	deployment := objectsInChart.find(t, "Deployment", "ogcloud-seaweedfs-all-in-one")
	spec := firstContainer(t, deployment)
	masterConfig := str(objectsInChart.find(t, "ConfigMap", "ogcloud-seaweedfs-master-config"), "data", "master.toml")
	dockerNetwork := testvalkey.Network(t)
	command := append([]string{"/bin/sh", "-c", `export POD_IP="$(hostname -i)"; exec "$@"`, "sh"}, stringList(get(spec, "command"))...)
	server, err := testcontainers.Run(context.Background(), str(spec, "image"),
		testcontainers.WithEntrypoint(command...),
		testcontainers.WithEnv(podEnv(t, spec, nil)),
		mount(t, mounted{
			"/etc/sw/s3/seaweedfs_s3_config": {content: s3Users(t, objectsInChart)},
			"/etc/seaweedfs/master.toml":     {content: masterConfig},
		}).restricted("/data", "/tmp"),
		network.WithNetwork([]string{"ogcloud-seaweedfs-all-in-one.ogcloud.svc"}, &testcontainers.DockerNetwork{Name: dockerNetwork}),
		testcontainers.WithExposedPorts("8333/tcp", "9333/tcp"),
		testcontainers.WithWaitStrategy(wait.ForHTTP("/cluster/status").WithPort("9333/tcp").WithStartupTimeout(time.Minute)),
	)
	testcontainers.CleanupContainer(t, server)
	if err != nil {
		t.Fatalf("start SeaweedFS: %v", err)
	}
	return seaweedFS{network: dockerNetwork, endpoint: "http://" + testvalkey.Address(t, server, "8333/tcp")}
}

// runBucketJob runs the chart's bucket Job to completion; it must succeed again when the bucket exists.
func runBucketJob(t *testing.T, objectsInChart rendered, dockerNetwork string) {
	t.Helper()
	spec := firstContainer(t, objectsInChart.find(t, "Job", "ogcloud-seaweedfs-bucket"))
	job, err := testcontainers.Run(context.Background(), str(spec, "image"),
		testcontainers.WithEntrypoint(append(stringList(get(spec, "command")), stringList(get(spec, "args"))...)...),
		testcontainers.WithEnv(podEnv(t, spec, nil)),
		mount(t, nil).restricted("/tmp"),
		network.WithNetwork(nil, &testcontainers.DockerNetwork{Name: dockerNetwork}),
		testcontainers.WithWaitStrategy(wait.ForExit().WithExitTimeout(time.Minute)),
	)
	testcontainers.CleanupContainer(t, job)
	if err != nil {
		t.Fatalf("run the bucket Job: %v", err)
	}
	state, err := job.State(context.Background())
	if err != nil {
		t.Fatal(err)
	}
	if state.ExitCode != 0 {
		t.Fatalf("the bucket Job exited %d:\n%s", state.ExitCode, containerLogs(t, job))
	}
}

// s3Users is the synced SeaweedFS config with the test's credentials in place of Infisical's.
func s3Users(t *testing.T, objectsInChart rendered) string {
	t.Helper()
	return syncedTemplate(t, objectsInChart, "ogcloud-seaweedfs-s3", "seaweedfs_s3_config",
		map[string]string{"S3_ACCESS_KEY": s3AccessKey, "S3_SECRET_KEY": s3SecretKey})
}

func chartBucket(t *testing.T, objectsInChart rendered) string {
	t.Helper()
	bucket := podEnv(t, firstContainer(t, objectsInChart.find(t, "Job", "ogcloud-seaweedfs-bucket")), nil)["BUCKET"]
	if bucket == "" {
		t.Fatal("the bucket Job names no bucket")
	}
	return bucket
}

func download(t *testing.T, url string) string {
	t.Helper()
	response, err := http.Get(url) //nolint:gosec,noctx // G107: the test's own presigned URL.
	if err != nil {
		t.Fatalf("download: %v", err)
	}
	defer func() { _ = response.Body.Close() }()
	var body bytes.Buffer
	_, _ = body.ReadFrom(response.Body)
	return body.String()
}

func anonymousGet(t *testing.T, url string) int {
	t.Helper()
	response, err := http.Get(url) //nolint:gosec,noctx // G107: the test's own server.
	if err != nil {
		t.Fatalf("anonymous get: %v", err)
	}
	_ = response.Body.Close()
	return response.StatusCode
}
