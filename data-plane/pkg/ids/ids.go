// Package ids makes UUIDv7 ids (D-0064), time-ordered like the control plane's.
package ids

import (
	"encoding/binary"
	"encoding/hex"
	"fmt"
	"io"
	"time"
)

// Generator makes ids from an injected clock and randomness.
type Generator struct {
	now    func() time.Time
	random io.Reader
}

// New uses now and random, e.g. time.Now and crypto/rand.Reader.
func New(now func() time.Time, random io.Reader) *Generator {
	return &Generator{now: now, random: random}
}

// Next returns a UUIDv7 (RFC 9562) in its lower-case text form.
func (g *Generator) Next() (string, error) {
	var id [16]byte
	if _, err := io.ReadFull(g.random, id[6:]); err != nil {
		return "", fmt.Errorf("random bits for an id: %w", err)
	}
	var millis [8]byte
	binary.BigEndian.PutUint64(millis[:], uint64(g.now().UnixMilli())) //nolint:gosec // G115: times before 1970 do not occur.
	copy(id[:6], millis[2:])
	id[6] = id[6]&0x0f | 0x70 // version 7
	id[8] = id[8]&0x3f | 0x80 // RFC 9562 variant
	text := hex.EncodeToString(id[:])
	return text[0:8] + "-" + text[8:12] + "-" + text[12:16] + "-" + text[16:20] + "-" + text[20:], nil
}
