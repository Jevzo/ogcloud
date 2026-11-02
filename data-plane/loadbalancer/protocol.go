// Package loadbalancer is the Minecraft TCP edge in front of Velocity: it answers status pings itself and
// hands logins to a running proxy with a PROXY v2 header (D-0118).
package loadbalancer

import (
	"bufio"
	"errors"
	"fmt"
	"io"
	"unicode/utf8"
)

// Minecraft's wire format: packets are a VarInt length, a VarInt id and the payload.
const (
	maxVarIntBytes = 5
	// The server address is at most 255 characters of up to 4 UTF-8 bytes each.
	maxHostChars = 255
	maxHostBytes = maxHostChars * utf8.UTFMax

	packetHandshake       = 0x00
	packetStatusRequest   = 0x00
	packetStatusResponse  = 0x00
	packetPing            = 0x01
	packetLoginDisconnect = 0x00

	nextStatus = 1
	nextLogin  = 2
	// Clients sent here by a server's transfer packet (1.20.5+) log in as usual.
	nextTransfer = 3

	// Clients before 1.7 open with this byte; they are older than anything the network admits.
	legacyPing = 0xFE
)

var (
	errVarIntTooLong = errors.New("varint longer than 5 bytes")
	errPacketSize    = errors.New("packet size out of bounds")
	errMalformed     = errors.New("malformed packet")
)

// Handshake is the first packet of every connection.
type Handshake struct {
	Protocol  int32
	Host      string
	Port      uint16
	NextState int32
}

func readVarInt(r io.ByteReader) (int32, error) {
	var value uint32
	for i := range maxVarIntBytes {
		b, err := r.ReadByte()
		if err != nil {
			return 0, fmt.Errorf("read varint: %w", err)
		}
		value |= uint32(b&0x7f) << (7 * i)
		if b&0x80 == 0 {
			return int32(value), nil //nolint:gosec // G115: VarInts are two's-complement 32-bit values.
		}
	}
	return 0, errVarIntTooLong
}

func appendVarInt(b []byte, value int32) []byte {
	v := uint32(value) //nolint:gosec // G115: VarInts encode the two's-complement bits.
	for v >= 0x80 {
		b = append(b, byte(v)|0x80)
		v >>= 7
	}
	return append(b, byte(v))
}

func appendString(b []byte, s string) []byte {
	return append(appendVarInt(b, int32(len(s))), s...) //nolint:gosec // G115: callers send short texts.
}

// readPacket reads one packet of at most maxSize bytes and returns its id, its payload and the packet as
// sent, length prefix included, for forwarding.
func readPacket(r *bufio.Reader, maxSize int) (int32, []byte, []byte, error) {
	prefix := &recordingReader{source: r}
	length, err := readVarInt(prefix)
	if err != nil {
		return 0, nil, nil, err
	}
	if length < 1 || int(length) > maxSize {
		return 0, nil, nil, fmt.Errorf("%w: %d", errPacketSize, length)
	}
	body := make([]byte, length)
	if _, err := io.ReadFull(r, body); err != nil {
		return 0, nil, nil, fmt.Errorf("read packet: %w", err)
	}
	payload := &byteReader{data: body}
	id, err := readVarInt(payload)
	if err != nil {
		return 0, nil, nil, fmt.Errorf("%w: %w", errMalformed, err)
	}
	return id, body[payload.offset:], append(prefix.read, body...), nil
}

// parseHandshake decodes a handshake payload, refusing anything that does not fit the format exactly.
func parseHandshake(payload []byte) (Handshake, error) {
	r := &byteReader{data: payload}
	protocol, err := readVarInt(r)
	if err != nil {
		return Handshake{}, fmt.Errorf("%w: protocol: %w", errMalformed, err)
	}
	hostLength, err := readVarInt(r)
	if err != nil || hostLength < 0 || hostLength > maxHostBytes || r.remaining() < int(hostLength)+2 {
		return Handshake{}, fmt.Errorf("%w: host", errMalformed)
	}
	host := r.take(int(hostLength))
	if !utf8.Valid(host) || utf8.RuneCount(host) > maxHostChars {
		return Handshake{}, fmt.Errorf("%w: host", errMalformed)
	}
	port := r.take(2)
	next, err := readVarInt(r)
	if err != nil || r.remaining() != 0 {
		return Handshake{}, fmt.Errorf("%w: next state", errMalformed)
	}
	return Handshake{Protocol: protocol, Host: string(host), Port: uint16(port[0])<<8 | uint16(port[1]), NextState: next}, nil
}

// writePacket sends one uncompressed packet.
func writePacket(w io.Writer, id int32, payload []byte) error {
	body := append(appendVarInt(nil, id), payload...)
	packet := append(appendVarInt(nil, int32(len(body))), body...) //nolint:gosec // G115: packets the LB writes are small.
	if _, err := w.Write(packet); err != nil {
		return fmt.Errorf("write packet: %w", err)
	}
	return nil
}

// recordingReader keeps the bytes it reads, so a packet is forwarded exactly as the client sent it.
type recordingReader struct {
	source io.ByteReader
	read   []byte
}

func (r *recordingReader) ReadByte() (byte, error) {
	b, err := r.source.ReadByte()
	if err == nil {
		r.read = append(r.read, b)
	}
	return b, err //nolint:wrapcheck // readVarInt wraps it.
}

type byteReader struct {
	data   []byte
	offset int
}

func (r *byteReader) ReadByte() (byte, error) {
	if r.offset >= len(r.data) {
		return 0, io.ErrUnexpectedEOF
	}
	b := r.data[r.offset]
	r.offset++
	return b, nil
}

func (r *byteReader) remaining() int { return len(r.data) - r.offset }

func (r *byteReader) take(n int) []byte {
	b := r.data[r.offset : r.offset+n]
	r.offset += n
	return b
}
