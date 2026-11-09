package backup

import (
	"bytes"
	"context"
	"errors"
	"fmt"
	"io"

	"github.com/aws/aws-sdk-go-v2/aws"
	"github.com/aws/aws-sdk-go-v2/service/s3"
	"github.com/aws/aws-sdk-go-v2/service/s3/types"

	"github.com/jevzo/ogcloud/data-plane/pkg/objects"
)

// partSize keeps memory bounded while copying: 20 GiB fit in 1,280 parts, well within S3's 10,000.
const partSize = 16 << 20

// Bucket is one S3 bucket: the data bucket or the backup target.
type Bucket struct {
	client *s3.Client
	name   string
}

// NewBucket reaches s.Bucket.
func NewBucket(s objects.Settings) *Bucket {
	return &Bucket{client: objects.NewClient(s), name: s.Bucket}
}

// List returns every object under prefix.
func (b *Bucket) List(ctx context.Context, prefix string) ([]Object, error) {
	var listed []Object
	pages := s3.NewListObjectsV2Paginator(b.client, &s3.ListObjectsV2Input{Bucket: &b.name, Prefix: &prefix})
	for pages.HasMorePages() {
		page, err := pages.NextPage(ctx)
		if err != nil {
			return nil, fmt.Errorf("list %s/%s: %w", b.name, prefix, err)
		}
		for _, object := range page.Contents {
			listed = append(listed, Object{
				Key:      aws.ToString(object.Key),
				Size:     aws.ToInt64(object.Size),
				Modified: aws.ToTime(object.LastModified),
			})
		}
	}
	return listed, nil
}

// copyTo streams key into target in parts held in memory one at a time: the two buckets may sit behind different
// endpoints, so S3 cannot copy server-side, and one PUT holds at most 5 GiB while templates may reach 20 (D-0103).
func (b *Bucket) copyTo(ctx context.Context, key string, target *Bucket) error {
	object, err := b.client.GetObject(ctx, &s3.GetObjectInput{Bucket: &b.name, Key: &key})
	if err != nil {
		return fmt.Errorf("read %s/%s: %w", b.name, key, err)
	}
	defer func() { _ = object.Body.Close() }()
	if aws.ToInt64(object.ContentLength) <= partSize {
		content, err := io.ReadAll(object.Body)
		if err != nil {
			return fmt.Errorf("read %s/%s: %w", b.name, key, err)
		}
		if _, err := target.client.PutObject(ctx, &s3.PutObjectInput{
			Bucket: &target.name, Key: &key, Body: bytes.NewReader(content),
		}); err != nil {
			return fmt.Errorf("write %s/%s: %w", target.name, key, err)
		}
		return nil
	}
	return target.putInParts(ctx, key, object.Body)
}

func (b *Bucket) putInParts(ctx context.Context, key string, body io.Reader) error {
	upload, err := b.client.CreateMultipartUpload(ctx, &s3.CreateMultipartUploadInput{Bucket: &b.name, Key: &key})
	if err != nil {
		return fmt.Errorf("start upload of %s/%s: %w", b.name, key, err)
	}
	completed, err := b.uploadParts(ctx, key, upload.UploadId, body)
	if err == nil {
		_, err = b.client.CompleteMultipartUpload(ctx, &s3.CompleteMultipartUploadInput{
			Bucket: &b.name, Key: &key, UploadId: upload.UploadId,
			MultipartUpload: &types.CompletedMultipartUpload{Parts: completed},
		})
	}
	if err != nil {
		// An abandoned upload would otherwise hold its parts until the bucket's cleanup (D-0043).
		_, abortErr := b.client.AbortMultipartUpload(ctx, &s3.AbortMultipartUploadInput{
			Bucket: &b.name, Key: &key, UploadId: upload.UploadId,
		})
		return errors.Join(fmt.Errorf("write %s/%s: %w", b.name, key, err), abortErr)
	}
	return nil
}

func (b *Bucket) uploadParts(ctx context.Context, key string, id *string, body io.Reader) ([]types.CompletedPart, error) {
	var completed []types.CompletedPart
	buffer := make([]byte, partSize)
	for number := int32(1); ; number++ {
		read, err := io.ReadFull(body, buffer)
		if errors.Is(err, io.EOF) {
			return completed, nil
		}
		if err != nil && !errors.Is(err, io.ErrUnexpectedEOF) {
			return nil, fmt.Errorf("read part %d: %w", number, err)
		}
		part, uploadErr := b.client.UploadPart(ctx, &s3.UploadPartInput{
			Bucket: &b.name, Key: &key, UploadId: id, PartNumber: aws.Int32(number),
			Body: bytes.NewReader(buffer[:read]),
		})
		if uploadErr != nil {
			return nil, fmt.Errorf("upload part %d: %w", number, uploadErr)
		}
		completed = append(completed, types.CompletedPart{ETag: part.ETag, PartNumber: aws.Int32(number)})
		if errors.Is(err, io.ErrUnexpectedEOF) {
			return completed, nil
		}
	}
}

// delete removes keys, at most a thousand per request as S3 allows.
func (b *Bucket) delete(ctx context.Context, keys []string) error {
	const batch = 1000
	for start := 0; start < len(keys); start += batch {
		end := min(start+batch, len(keys))
		identifiers := make([]types.ObjectIdentifier, 0, end-start)
		for _, key := range keys[start:end] {
			identifiers = append(identifiers, types.ObjectIdentifier{Key: aws.String(key)})
		}
		output, err := b.client.DeleteObjects(ctx, &s3.DeleteObjectsInput{
			Bucket: &b.name,
			Delete: &types.Delete{Objects: identifiers, Quiet: aws.Bool(true)},
		})
		if err != nil {
			return fmt.Errorf("delete from %s: %w", b.name, err)
		}
		if len(output.Errors) > 0 {
			first := output.Errors[0]
			return fmt.Errorf("delete %s/%s: %s", b.name, aws.ToString(first.Key), aws.ToString(first.Message))
		}
	}
	return nil
}
