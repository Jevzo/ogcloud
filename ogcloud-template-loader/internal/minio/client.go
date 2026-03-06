package minio

import (
	"context"
	"fmt"
	"io"
	"os"

	"github.com/minio/minio-go/v7"
	"github.com/minio/minio-go/v7/pkg/credentials"
)

type Client struct {
	inner *minio.Client
}

func NewClient(endpoint, accessKey, secretKey string) (*Client, error) {
	client, err := minio.New(endpoint, &minio.Options{
		Creds:  credentials.NewStaticV4(accessKey, secretKey, ""),
		Secure: false,
	})
	if err != nil {
		return nil, fmt.Errorf("create minio client: %w", err)
	}

	return &Client{inner: client}, nil
}

func (c *Client) UploadTemplate(ctx context.Context, bucket, objectPath, localPath string) error {
	_, err := c.inner.FPutObject(ctx, bucket, objectPath, localPath, minio.PutObjectOptions{
		ContentType: "application/gzip",
	})
	if err != nil {
		return fmt.Errorf("upload object %s/%s: %w", bucket, objectPath, err)
	}
	return nil
}

func (c *Client) DownloadTemplate(ctx context.Context, bucket, objectPath string) (string, error) {
	obj, err := c.inner.GetObject(ctx, bucket, objectPath, minio.GetObjectOptions{})
	if err != nil {
		return "", fmt.Errorf("get object %s/%s: %w", bucket, objectPath, err)
	}
	defer obj.Close()

	tmpFile, err := os.CreateTemp("", "template-*.tar.gz")
	if err != nil {
		return "", fmt.Errorf("create temp file: %w", err)
	}
	defer tmpFile.Close()

	if _, err := io.Copy(tmpFile, obj); err != nil {
		os.Remove(tmpFile.Name())
		return "", fmt.Errorf("download template: %w", err)
	}

	return tmpFile.Name(), nil
}
