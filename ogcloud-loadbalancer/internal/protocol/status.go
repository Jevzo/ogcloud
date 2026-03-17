package protocol

import (
	"bytes"
	"encoding/json"
	"fmt"
	"io"
	"strings"
)

const (
	StatusResponsePacketID = 0x00
)

type StatusResponse struct {
	Version     StatusVersion     `json:"version"`
	Players     StatusPlayers     `json:"players"`
	Description StatusDescription `json:"description"`
}

type StatusVersion struct {
	Name     string `json:"name"`
	Protocol int    `json:"protocol"`
}

type StatusPlayers struct {
	Max    int `json:"max"`
	Online int `json:"online"`
}

type StatusDescription struct {
	Text string `json:"text"`
}

func HandleStatusRequest(conn io.ReadWriter, versionName, motd string, maxPlayers, onlinePlayers, protocolVersion int) error {
	packetLength, err := ReadVarInt(conn)
	if err != nil {
		return fmt.Errorf("read status request length: %w", err)
	}

	payload := make([]byte, packetLength)
	if _, err := io.ReadFull(conn, payload); err != nil {
		return fmt.Errorf("read status request payload: %w", err)
	}

	response := StatusResponse{
		Version: StatusVersion{
			Name:     versionName,
			Protocol: protocolVersion,
		},
		Players: StatusPlayers{
			Max:    maxPlayers,
			Online: onlinePlayers,
		},
		Description: StatusDescription{
			Text: convertColors(motd),
		},
	}

	jsonData, err := json.Marshal(response)
	if err != nil {
		return fmt.Errorf("marshal status response: %w", err)
	}

	if err := writePacket(conn, StatusResponsePacketID, jsonData); err != nil {
		return fmt.Errorf("write status response: %w", err)
	}

	pingLength, err := ReadVarInt(conn)
	if err != nil {
		return nil
	}

	pingPayload := make([]byte, pingLength)
	if _, err := io.ReadFull(conn, pingPayload); err != nil {
		return nil
	}

	if len(pingPayload) < 9 {
		return nil
	}

	if err := writeRawPacket(conn, pingPayload); err != nil {
		return fmt.Errorf("write pong: %w", err)
	}

	return nil
}

func writePacket(w io.Writer, packetID int32, data []byte) error {
	var payload bytes.Buffer
	if err := WriteVarInt(&payload, packetID); err != nil {
		return err
	}
	if err := WriteString(&payload, string(data)); err != nil {
		return err
	}

	if err := WriteVarInt(w, int32(payload.Len())); err != nil {
		return err
	}
	_, err := w.Write(payload.Bytes())
	return err
}

func writeRawPacket(w io.Writer, payload []byte) error {
	if err := WriteVarInt(w, int32(len(payload))); err != nil {
		return err
	}
	_, err := w.Write(payload)
	return err
}

func convertColors(motd string) string {
	return strings.ReplaceAll(motd, "&", "\u00a7")
}
