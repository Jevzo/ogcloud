package backup

import (
	"bytes"
	"context"
	"crypto/rand"
	"errors"
	"io"
	"testing"
	"time"

	"github.com/aws/aws-sdk-go-v2/aws"
	"github.com/aws/aws-sdk-go-v2/service/s3"

	"github.com/jevzo/ogcloud/data-plane/internal/tests3"
	"github.com/jevzo/ogcloud/data-plane/pkg/objects"
)

const targetBucket = "ogcloud-backup-test"

// One SeaweedFS stands in for both: the data bucket and the backup target.
func buckets(t *testing.T) (data, target *Bucket, client *s3.Client) {
	t.Helper()
	endpoint := tests3.Start(t)
	client = tests3.Client(endpoint)
	if _, err := client.CreateBucket(context.Background(), &s3.CreateBucketInput{Bucket: aws.String(targetBucket)}); err != nil {
		t.Fatalf("create the target bucket: %v", err)
	}
	settings := func(bucket string) objects.Settings {
		return objects.Settings{
			Endpoint: endpoint, Region: tests3.Region, Bucket: bucket,
			AccessKey: tests3.AccessKey, SecretKey: tests3.SecretKey, PathStyle: true,
		}
	}
	return NewBucket(settings(tests3.Bucket)), NewBucket(settings(targetBucket)), client
}

func put(t *testing.T, client *s3.Client, bucket, key string, content []byte) {
	t.Helper()
	if _, err := client.PutObject(context.Background(), &s3.PutObjectInput{
		Bucket: aws.String(bucket), Key: aws.String(key), Body: bytes.NewReader(content),
	}); err != nil {
		t.Fatalf("put %s: %v", key, err)
	}
}

func read(t *testing.T, client *s3.Client, bucket, key string) []byte {
	t.Helper()
	object, err := client.GetObject(context.Background(), &s3.GetObjectInput{Bucket: aws.String(bucket), Key: aws.String(key)})
	if err != nil {
		t.Fatalf("get %s: %v", key, err)
	}
	defer func() { _ = object.Body.Close() }()
	content, err := io.ReadAll(object.Body)
	if err != nil {
		t.Fatalf("read %s: %v", key, err)
	}
	return content
}

func TestBackUpCopiesTemplatesPrunesCopiesLongGoneAndRestores(t *testing.T) {
	ctx := context.Background()
	data, target, client := buckets(t)
	small := []byte("small archive")
	large := make([]byte, partSize+partSize/2)
	if _, err := rand.Read(large); err != nil {
		t.Fatal(err)
	}
	put(t, client, tests3.Bucket, "templates/lobby/small.tar.gz", small)
	put(t, client, tests3.Bucket, "templates/lobby/large.tar.gz", large)
	put(t, client, tests3.Bucket, "runtime/velocity/ViaVersion.jar", []byte("not a template"))
	now := time.Now()

	result, err := BackUp(ctx, data, target, now, 14*24*time.Hour)
	if err != nil || result.Copied != 2 {
		t.Fatalf("first run: %+v, %v", result, err)
	}
	if !bytes.Equal(read(t, client, targetBucket, "templates/lobby/large.tar.gz"), large) {
		t.Error("the archive copied in parts differs from its source")
	}
	if listed, _ := target.List(ctx, "runtime/"); len(listed) != 0 {
		t.Errorf("non-template objects were copied: %v", listed)
	}
	if again, err := BackUp(ctx, data, target, now, 14*24*time.Hour); err != nil || again.Copied != 0 {
		t.Errorf("a second run copied %+v, %v", again, err)
	}

	if err := data.delete(ctx, []string{"templates/lobby/small.tar.gz"}); err != nil {
		t.Fatal(err)
	}
	// The copies are new here, but what counts is how long ago their source went (D-0186).
	later := now.Add(30 * 24 * time.Hour)
	if marked, err := BackUp(ctx, data, target, later, 14*24*time.Hour); err != nil || marked.Marked != 1 || marked.Pruned != 0 {
		t.Errorf("the copy of a template deleted just now: %+v, %v; want it marked and kept", marked, err)
	}
	if pruned, err := BackUp(ctx, data, target, later.Add(15*24*time.Hour), 14*24*time.Hour); err != nil || pruned.Pruned != 1 {
		t.Errorf("a copy whose source went over 14 days ago was kept: %+v, %v", pruned, err)
	}
	if marks, _ := target.List(ctx, markPrefix); len(marks) != 0 {
		t.Errorf("the pruned copy's mark stayed: %v", marks)
	}

	if err := data.delete(ctx, []string{"templates/lobby/large.tar.gz"}); err != nil {
		t.Fatal(err)
	}
	if lost, err := BackUp(ctx, data, target, later.Add(60*24*time.Hour), 14*24*time.Hour); !errors.Is(err, ErrSourceLost) {
		t.Errorf("a data bucket without any copied archive: %+v, %v; want ErrSourceLost", lost, err)
	}
	restored, err := Restore(ctx, target, data)
	if err != nil || restored.Copied != 1 {
		t.Fatalf("restore: %+v, %v", restored, err)
	}
	if !bytes.Equal(read(t, client, tests3.Bucket, "templates/lobby/large.tar.gz"), large) {
		t.Error("the restored archive differs from the backup")
	}
}
