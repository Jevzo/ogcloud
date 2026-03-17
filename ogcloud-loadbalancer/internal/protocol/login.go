package protocol

import (
	"encoding/json"
	"fmt"
	"io"
)

const (
	LoginDisconnectPacketID = 0x00
)

func HandleLoginDisconnect(conn io.Writer, reason string) error {
	payload, err := json.Marshal(StatusDescription{Text: convertColors(reason)})
	if err != nil {
		return fmt.Errorf("marshal login disconnect: %w", err)
	}

	if err := writePacket(conn, LoginDisconnectPacketID, payload); err != nil {
		return fmt.Errorf("write login disconnect: %w", err)
	}

	return nil
}
