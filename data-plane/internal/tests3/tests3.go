// Package tests3 runs SeaweedFS with an S3 gateway from the pinned image (D-0113), as the Kotlin storage
// fixtures do.
package tests3

import (
	"context"
	"strings"
	"testing"

	"github.com/aws/aws-sdk-go-v2/aws"
	"github.com/aws/aws-sdk-go-v2/credentials"
	"github.com/aws/aws-sdk-go-v2/service/s3"
	"github.com/testcontainers/testcontainers-go"
	"github.com/testcontainers/testcontainers-go/wait"

	"github.com/jevzo/ogcloud/data-plane/internal/testimages"
)

// Test credentials and bucket, as in the Kotlin fixtures.
const (
	Bucket    = "ogcloud-test"
	AccessKey = "ogcloud-access"
	SecretKey = "ogcloud-secret-key"
	Region    = "us-east-1"
	port      = "8333/tcp"
)

const identities = `{"identities":[{"name":"ogcloud","credentials":[{"accessKey":"` + AccessKey + `","secretKey":"` +
	SecretKey + `"}],"actions":["Admin","Read","Write","List","Tagging"]}]}`

// Start runs SeaweedFS, creates Bucket and returns the S3 endpoint.
func Start(t *testing.T) string {
	t.Helper()
	ctx := context.Background()
	image, err := testimages.Image("seaweedfs")
	if err != nil {
		t.Fatalf("image: %v", err)
	}
	container, err := testcontainers.Run(ctx, image,
		testcontainers.WithFiles(testcontainers.ContainerFile{
			Reader: strings.NewReader(identities), ContainerFilePath: "/etc/seaweedfs/s3.json", FileMode: 0o644,
		}),
		testcontainers.WithCmd("server", "-dir=/data", "-s3", "-s3.port=8333", "-s3.config=/etc/seaweedfs/s3.json"),
		testcontainers.WithExposedPorts(port),
		testcontainers.WithWaitStrategy(wait.ForListeningPort(port)),
	)
	testcontainers.CleanupContainer(t, container)
	if err != nil {
		t.Fatalf("start SeaweedFS: %v", err)
	}
	endpoint, err := container.PortEndpoint(ctx, port, "http")
	if err != nil {
		t.Fatalf("endpoint: %v", err)
	}
	if _, err := Client(endpoint).CreateBucket(ctx, &s3.CreateBucketInput{Bucket: aws.String(Bucket)}); err != nil {
		t.Fatalf("create bucket: %v", err)
	}
	return endpoint
}

// Client is an admin client for the test bucket.
func Client(endpoint string) *s3.Client {
	return s3.New(s3.Options{
		BaseEndpoint: aws.String(endpoint),
		Region:       Region,
		Credentials:  credentials.NewStaticCredentialsProvider(AccessKey, SecretKey, ""),
		UsePathStyle: true,
	})
}
