package objects

import "github.com/jevzo/ogcloud/data-plane/pkg/config"

// FromConfig reads OGCLOUD_S3_ENDPOINT, OGCLOUD_S3_REGION, OGCLOUD_S3_BUCKET, OGCLOUD_S3_PATH_STYLE and the
// key files OGCLOUD_S3_ACCESS_KEY_FILE and OGCLOUD_S3_SECRET_KEY_FILE. The region is required: URLs
// signed for the wrong one are refused with nothing but a 403.
func FromConfig(c *config.Source) Settings {
	return Settings{
		Endpoint:  c.Required("OGCLOUD_S3_ENDPOINT"),
		Region:    c.Required("OGCLOUD_S3_REGION"),
		Bucket:    c.Required("OGCLOUD_S3_BUCKET"),
		AccessKey: c.Secret("OGCLOUD_S3_ACCESS_KEY_FILE"),
		SecretKey: c.Secret("OGCLOUD_S3_SECRET_KEY_FILE"),
		PathStyle: c.Bool("OGCLOUD_S3_PATH_STYLE", false),
	}
}
