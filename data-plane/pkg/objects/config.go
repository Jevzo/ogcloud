package objects

import "github.com/jevzo/ogcloud/data-plane/pkg/config"

// FromConfig reads OGCLOUD_S3_ENDPOINT, OGCLOUD_S3_REGION, OGCLOUD_S3_BUCKET, OGCLOUD_S3_PATH_STYLE and the
// key files OGCLOUD_S3_ACCESS_KEY_FILE and OGCLOUD_S3_SECRET_KEY_FILE.
func FromConfig(c *config.Source) Settings {
	return Settings{
		Endpoint:  c.Required("OGCLOUD_S3_ENDPOINT"),
		Region:    c.String("OGCLOUD_S3_REGION", "us-east-1"),
		Bucket:    c.Required("OGCLOUD_S3_BUCKET"),
		AccessKey: c.Secret("OGCLOUD_S3_ACCESS_KEY_FILE"),
		SecretKey: c.Secret("OGCLOUD_S3_SECRET_KEY_FILE"),
		PathStyle: c.Bool("OGCLOUD_S3_PATH_STYLE", false),
	}
}
