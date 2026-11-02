package loadbalancer

import (
	"bufio"
	"bytes"
	"errors"
	"testing"
)

func handshakePacket(protocol int32, host string, port uint16, next int32) []byte {
	return frame(handshakeBody(protocol, host, port, next))
}

func handshakeBody(protocol int32, host string, port uint16, next int32) []byte {
	body := appendVarInt(nil, packetHandshake)
	body = appendVarInt(body, protocol)
	body = appendString(body, host)
	body = append(body, byte(port>>8), byte(port))
	return appendVarInt(body, next)
}

func frame(body []byte) []byte {
	return append(appendVarInt(nil, int32(len(body))), body...)
}

func TestHandshakesAreParsed(t *testing.T) {
	raw := handshakePacket(777, "play.ogwars.io", 25565, nextLogin)

	id, payload, forwarded, err := readPacket(bufio.NewReader(bytes.NewReader(raw)), 4096)
	if err != nil || id != packetHandshake {
		t.Fatalf("readPacket = %d, %v", id, err)
	}
	handshake, err := parseHandshake(payload)

	if err != nil || handshake != (Handshake{Protocol: 777, Host: "play.ogwars.io", Port: 25565, NextState: nextLogin}) {
		t.Errorf("handshake = %+v, %v", handshake, err)
	}
	if !bytes.Equal(forwarded, raw) {
		t.Error("the forwarded bytes differ from what the client sent")
	}
}

func TestBadPacketsAreRefused(t *testing.T) {
	long := handshakePacket(47, string(bytes.Repeat([]byte("a"), 300)), 1, nextLogin)
	tests := map[string][]byte{
		"empty":             {},
		"zero length":       {0x00},
		"over the limit":    appendVarInt(nil, 5000),
		"six-byte varint":   {0xff, 0xff, 0xff, 0xff, 0xff, 0x01},
		"negative length":   appendVarInt(nil, -1),
		"truncated":         append(appendVarInt(nil, 10), 0x00, 0x01),
		"host over 255":     long,
		"trailing bytes":    frame(append(handshakeBody(47, "a", 1, nextLogin), 0x00)),
		"invalid UTF-8":     handshakePacket(47, "\xff\xfe", 1, nextLogin),
		"host past the end": {0x04, 0x00, 0x2f, 0x7f, 0x00},
	}
	for name, raw := range tests {
		t.Run(name, func(t *testing.T) {
			_, payload, _, err := readPacket(bufio.NewReader(bytes.NewReader(raw)), 512)
			if err == nil {
				_, err = parseHandshake(payload)
			}
			if err == nil {
				t.Error("accepted")
			}
		})
	}
}

func TestVarIntsRoundTrip(t *testing.T) {
	for _, value := range []int32{0, 1, 127, 128, 255, 25565, 2097151, 2147483647, -1, -2147483648} {
		got, err := readVarInt(bytes.NewReader(appendVarInt(nil, value)))
		if err != nil || got != value {
			t.Errorf("%d: got %d, %v", value, got, err)
		}
	}
	if _, err := readVarInt(bytes.NewReader([]byte{0x80, 0x80, 0x80, 0x80, 0x80, 0x01})); !errors.Is(err, errVarIntTooLong) {
		t.Errorf("six bytes: %v", err)
	}
}

func FuzzHandshakeParsing(f *testing.F) {
	f.Add(handshakePacket(777, "play.ogwars.io", 25565, nextLogin))
	f.Add(handshakePacket(47, "", 0, nextStatus))
	f.Add([]byte{0xfe, 0x01})
	f.Fuzz(func(t *testing.T, raw []byte) {
		_, payload, forwarded, err := readPacket(bufio.NewReader(bytes.NewReader(raw)), 4096)
		if err != nil {
			return
		}
		if len(forwarded) > len(raw) || !bytes.Equal(forwarded, raw[:len(forwarded)]) {
			t.Fatalf("forwarded bytes %x are not a prefix of %x", forwarded, raw)
		}
		handshake, err := parseHandshake(payload)
		if err == nil && len(handshake.Host) > maxHostBytes {
			t.Fatalf("host of %d bytes accepted", len(handshake.Host))
		}
	})
}
