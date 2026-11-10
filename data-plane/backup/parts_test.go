package backup

import (
	"bytes"
	"errors"
	"io"
	"slices"
	"testing"
)

// cutShort ends with io.ErrUnexpectedEOF after its content, as an HTTP body does when the connection drops early.
type cutShort struct{ content *bytes.Reader }

func (c cutShort) Read(p []byte) (int, error) {
	read, err := c.content.Read(p)
	if errors.Is(err, io.EOF) {
		return read, io.ErrUnexpectedEOF
	}
	return read, err
}

func TestEachPartPassesOnlyABodyOfTheAnnouncedSize(t *testing.T) {
	const part = 4
	tests := []struct {
		name      string
		body      io.Reader
		size      int64
		wantParts []string
		wantErr   bool
	}{
		{"a whole number of parts", bytes.NewReader([]byte("abcdefgh")), 8, []string{"abcd", "efgh"}, false},
		{"a short last part", bytes.NewReader([]byte("abcdefghij")), 10, []string{"abcd", "efgh", "ij"}, false},
		{"a body that ends early", bytes.NewReader([]byte("abcdefghij")), 12, nil, true},
		{"a connection dropped mid-part", cutShort{bytes.NewReader([]byte("abcdefghij"))}, 12, nil, true},
		{"a body longer than announced", bytes.NewReader([]byte("abcdefghij")), 6, nil, true},
	}
	for _, test := range tests {
		t.Run(test.name, func(t *testing.T) {
			var parts []string
			err := eachPart(test.body, test.size, make([]byte, part), func(_ int32, content []byte) error {
				parts = append(parts, string(content))
				return nil
			})
			if (err != nil) != test.wantErr {
				t.Fatalf("eachPart = %v, want an error: %v", err, test.wantErr)
			}
			if !test.wantErr && !slices.Equal(parts, test.wantParts) {
				t.Errorf("parts = %q, want %q", parts, test.wantParts)
			}
		})
	}
}

func TestReadExactlyRefusesAShortOrLongBody(t *testing.T) {
	if got, err := readExactly(bytes.NewReader([]byte("abc")), 3); err != nil || string(got) != "abc" {
		t.Errorf("readExactly = %q, %v", got, err)
	}
	for name, size := range map[string]int64{"short": 4, "long": 2} {
		if _, err := readExactly(bytes.NewReader([]byte("abc")), size); err == nil {
			t.Errorf("a %s body passed", name)
		}
	}
}
