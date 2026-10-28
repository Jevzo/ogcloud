package testimages

import (
	"strings"
	"testing"
)

func TestPinnedImagesAreFound(t *testing.T) {
	image, err := Image("kafka")
	if err != nil {
		t.Fatalf("Image: %v", err)
	}
	if !strings.HasPrefix(image, "apache/kafka:") {
		t.Errorf("kafka image = %q", image)
	}
	if _, err := Image("no-such-image"); err == nil {
		t.Error("an unknown image was found")
	}
}
