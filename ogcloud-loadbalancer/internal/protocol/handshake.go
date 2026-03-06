package protocol

import (
	"bytes"
	"errors"
	"io"
)

const (
	HandshakePacketID = 0x00
	StatusNextState   = 1
	LoginNextState    = 2
)

type Handshake struct {
	ProtocolVersion int32
	ServerAddress   string
	ServerPort      uint16
	NextState       int32
}

func ReadHandshake(r io.Reader) (*Handshake, []byte, error) {
	var rawBuf bytes.Buffer
	tee := io.TeeReader(r, &rawBuf)

	packetLength, err := ReadVarInt(tee)
	if err != nil {
		return nil, nil, err
	}

	if packetLength < 0 || packetLength > 1024 {
		return nil, nil, errors.New("invalid handshake packet length")
	}

	payload := make([]byte, packetLength)
	if _, err := io.ReadFull(tee, payload); err != nil {
		return nil, nil, err
	}

	pr := bytes.NewReader(payload)

	packetID, err := ReadVarInt(pr)
	if err != nil {
		return nil, nil, err
	}
	if packetID != HandshakePacketID {
		return nil, nil, errors.New("expected handshake packet ID 0x00")
	}

	protocolVersion, err := ReadVarInt(pr)
	if err != nil {
		return nil, nil, err
	}

	serverAddress, err := ReadString(pr)
	if err != nil {
		return nil, nil, err
	}

	portBuf := make([]byte, 2)
	if _, err := io.ReadFull(pr, portBuf); err != nil {
		return nil, nil, err
	}
	serverPort := uint16(portBuf[0])<<8 | uint16(portBuf[1])

	nextState, err := ReadVarInt(pr)
	if err != nil {
		return nil, nil, err
	}

	hs := &Handshake{
		ProtocolVersion: protocolVersion,
		ServerAddress:   serverAddress,
		ServerPort:      serverPort,
		NextState:       nextState,
	}

	return hs, rawBuf.Bytes(), nil
}
