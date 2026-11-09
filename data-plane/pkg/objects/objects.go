// Package objects hands out presigned URLs for templates and runtime bundles, so pods never hold
// object-storage credentials (D-0010).
package objects

import (
	"context"
	"fmt"
	"time"

	"github.com/aws/aws-sdk-go-v2/aws"
	"github.com/aws/aws-sdk-go-v2/credentials"
	"github.com/aws/aws-sdk-go-v2/service/s3"
)

// Settings reach the bucket on Hetzner Object Storage or SeaweedFS.
type Settings struct {
	Endpoint  string
	Region    string
	Bucket    string
	AccessKey string
	SecretKey string
	// SeaweedFS in the cluster needs path-style URLs.
	PathStyle bool
}

// Presigner signs URLs that stay valid for a fixed time.
type Presigner struct {
	client *s3.PresignClient
	bucket string
	expiry time.Duration
}

// NewClient reaches the bucket's endpoint with the settings' credentials. Checksums are sent only where S3 requires
// them: SeaweedFS and Hetzner Object Storage refuse some of the SDK's newer default ones.
func NewClient(s Settings) *s3.Client {
	return s3.New(s3.Options{
		BaseEndpoint:               aws.String(s.Endpoint),
		Region:                     s.Region,
		Credentials:                credentials.NewStaticCredentialsProvider(s.AccessKey, s.SecretKey, ""),
		UsePathStyle:               s.PathStyle,
		RequestChecksumCalculation: aws.RequestChecksumCalculationWhenRequired,
		ResponseChecksumValidation: aws.ResponseChecksumValidationWhenRequired,
	})
}

// NewPresigner signs for s.Bucket; URLs expire after expiry.
func NewPresigner(s Settings, expiry time.Duration) *Presigner {
	return &Presigner{client: s3.NewPresignClient(NewClient(s)), bucket: s.Bucket, expiry: expiry}
}

// Get returns a URL that downloads key.
func (p *Presigner) Get(ctx context.Context, key string) (string, error) {
	request, err := p.client.PresignGetObject(ctx, &s3.GetObjectInput{Bucket: &p.bucket, Key: &key}, s3.WithPresignExpires(p.expiry))
	if err != nil {
		return "", fmt.Errorf("presign download of %s: %w", key, err)
	}
	return request.URL, nil
}

// Put returns a URL that uploads key.
func (p *Presigner) Put(ctx context.Context, key string) (string, error) {
	request, err := p.client.PresignPutObject(ctx, &s3.PutObjectInput{Bucket: &p.bucket, Key: &key}, s3.WithPresignExpires(p.expiry))
	if err != nil {
		return "", fmt.Errorf("presign upload of %s: %w", key, err)
	}
	return request.URL, nil
}
