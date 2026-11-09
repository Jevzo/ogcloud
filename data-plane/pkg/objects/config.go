package objects

import "github.com/jevzo/ogcloud/data-plane/pkg/config"

// FromConfig reads OGCLOUD_S3_ENDPOINT, OGCLOUD_S3_REGION, OGCLOUD_S3_BUCKET, OGCLOUD_S3_PATH_STYLE and the
// key files OGCLOUD_S3_ACCESS_KEY_FILE and OGCLOUD_S3_SECRET_KEY_FILE. The region is required: URLs
// signed for the wrong one are refused with nothing but a 403.
func FromConfig(c *config.Source) Settings {
	return FromConfigPrefix(c, "OGCLOUD_S3")
}

// FromConfigPrefix reads the same settings under another prefix, e.g. OGCLOUD_BACKUP_S3 for the backup target.
func FromConfigPrefix(c *config.Source, prefix string) Settings {
	return Settings{
		Endpoint:  c.Required(prefix + "_ENDPOINT"),
		Region:    c.Required(prefix + "_REGION"),
		Bucket:    c.Required(prefix + "_BUCKET"),
		AccessKey: c.Secret(prefix + "_ACCESS_KEY_FILE"),
		SecretKey: c.Secret(prefix + "_SECRET_KEY_FILE"),
		PathStyle: c.Bool(prefix+"_PATH_STYLE", false),
	}
}
