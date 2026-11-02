package ids

import (
	"bytes"
	"crypto/rand"
	"regexp"
	"testing"
	"time"
)

var uuidV7 = regexp.MustCompile(`^[0-9a-f]{8}-[0-9a-f]{4}-7[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$`)

func TestIdsAreVersion7AndSortByTime(t *testing.T) {
	now := time.Date(2026, 11, 2, 12, 0, 0, 0, time.UTC)
	generator := New(func() time.Time { return now }, rand.Reader)

	first, err := generator.Next()
	if err != nil {
		t.Fatalf("Next: %v", err)
	}
	now = now.Add(time.Millisecond)
	second, err := generator.Next()
	if err != nil {
		t.Fatalf("Next: %v", err)
	}

	if !uuidV7.MatchString(first) || !uuidV7.MatchString(second) {
		t.Errorf("not UUIDv7: %s %s", first, second)
	}
	if first >= second {
		t.Errorf("%s does not sort before %s", first, second)
	}
	// 2026-11-02T12:00:00Z is 0x01a19c217200 milliseconds after the epoch.
	if first[:13] != "01a19c21-7200" {
		t.Errorf("timestamp bits = %s", first[:13])
	}
}

func TestRandomnessFailuresAreReported(t *testing.T) {
	if _, err := New(time.Now, bytes.NewReader(nil)).Next(); err == nil {
		t.Error("an empty random source was accepted")
	}
}
