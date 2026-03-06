package protocol

import (
	"errors"
	"io"
)

const (
	MaxVarIntBytes = 5
	segmentBits    = 0x7F
	continueBit    = 0x80
)

var ErrVarIntTooLong = errors.New("VarInt is too long")

func ReadVarInt(r io.Reader) (int32, error) {
	var result int32
	var numRead uint
	buf := make([]byte, 1)

	for {
		if _, err := io.ReadFull(r, buf); err != nil {
			return 0, err
		}

		result |= int32(buf[0]&segmentBits) << (7 * numRead)
		numRead++

		if buf[0]&continueBit == 0 {
			return result, nil
		}

		if numRead >= MaxVarIntBytes {
			return 0, ErrVarIntTooLong
		}
	}
}

func WriteVarInt(w io.Writer, value int32) error {
	uval := uint32(value)
	for {
		if uval&^segmentBits == 0 {
			_, err := w.Write([]byte{byte(uval)})
			return err
		}
		_, err := w.Write([]byte{byte(uval&segmentBits) | continueBit})
		if err != nil {
			return err
		}
		uval >>= 7
	}
}

func ReadString(r io.Reader) (string, error) {
	length, err := ReadVarInt(r)
	if err != nil {
		return "", err
	}

	if length < 0 {
		return "", errors.New("negative string length")
	}

	buf := make([]byte, length)
	if _, err := io.ReadFull(r, buf); err != nil {
		return "", err
	}

	return string(buf), nil
}

func WriteString(w io.Writer, s string) error {
	if err := WriteVarInt(w, int32(len(s))); err != nil {
		return err
	}
	_, err := w.Write([]byte(s))
	return err
}
