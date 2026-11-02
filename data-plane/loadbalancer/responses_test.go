package loadbalancer

import (
	"bufio"
	"bytes"
	"encoding/json"
	"strings"
	"testing"

	eventsv1 "github.com/jevzo/ogcloud/data-plane/pkg/gen/proto/ogcloud/events/v1"
	"github.com/jevzo/ogcloud/data-plane/pkg/runtimeprofiles"
)

var network = &eventsv1.NetworkSettingsUpdated{
	Motd: "&aWelcome", MaintenanceMotd: "&cMaintenance", VersionName: "OgWars", MaintenanceVersionName: "&4Closed", MaxPlayers: 500,
}

func TestStatusShowsTheNetworkOrWhyAClientCannotJoin(t *testing.T) {
	closed := &eventsv1.NetworkSettingsUpdated{Maintenance: true, Motd: network.GetMotd(), MaintenanceMotd: network.GetMaintenanceMotd(), MaintenanceVersionName: "&4Closed"}
	tests := []struct {
		name     string
		network  *eventsv1.NetworkSettingsUpdated
		protocol int32
		want     statusResponse
	}{
		{"open", network, 777, statusResponse{statusVersion{"OgWars", 777}, statusPlayers{500, 42}, textComponent{"§aWelcome"}}},
		{"too old", network, 5, statusResponse{statusVersion{"1.8.8-26.3", -1}, statusPlayers{500, 42}, textComponent{"§aWelcome"}}},
		{"maintenance", closed, 777, statusResponse{statusVersion{"§4Closed", -1}, statusPlayers{0, 42}, textComponent{"§cMaintenance"}}},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			if got := status(tt.network, 42, tt.protocol); got != tt.want {
				t.Errorf("got %+v, want %+v", got, tt.want)
			}
		})
	}
}

func TestTheStatusExchangeEchoesThePing(t *testing.T) {
	ping := []byte{1, 2, 3, 4, 5, 6, 7, 8}
	client := append(frame(appendVarInt(nil, packetStatusRequest)), frame(append(appendVarInt(nil, packetPing), ping...))...)
	var out bytes.Buffer

	err := answerStatus(bufio.NewReader(bytes.NewReader(client)), &out, status(network, 1, 777))

	if err != nil {
		t.Fatalf("answerStatus: %v", err)
	}
	reader := bufio.NewReader(&out)
	_, body, _, err := readPacket(reader, 4096)
	if err != nil {
		t.Fatal(err)
	}
	if response := decodeStatus(t, body); response.Players.Online != 1 {
		t.Errorf("status %+v", response)
	}
	id, pong, _, err := readPacket(reader, 64)
	if err != nil || id != packetPing || !bytes.Equal(pong, ping) {
		t.Errorf("pong = %d %x %v", id, pong, err)
	}
}

func TestUnsupportedVersionsAreNamedFromTheCatalog(t *testing.T) {
	if !strings.Contains(unsupportedVersionMessage, "1.8.8 through 26.3") {
		t.Errorf("message = %q", unsupportedVersionMessage)
	}
	if supported(runtimeprofiles.MinClientProtocol-1) || !supported(runtimeprofiles.MaxClientProtocol) {
		t.Error("the protocol range is off")
	}
}

func TestColourCodes(t *testing.T) {
	if got := legacyColors("&aA & B &zC &&lD"); got != "§aA & B &zC &§lD" {
		t.Errorf("got %q", got)
	}
}

// decodeStatus reads the JSON string of a status response payload.
func decodeStatus(t *testing.T, payload []byte) statusResponse {
	t.Helper()
	r := &byteReader{data: payload}
	length, err := readVarInt(r)
	if err != nil || int(length) != r.remaining() {
		t.Fatalf("status string: %d bytes of %d, %v", length, r.remaining(), err)
	}
	var response statusResponse
	if err := json.Unmarshal(r.take(int(length)), &response); err != nil {
		t.Fatalf("status JSON: %v", err)
	}
	return response
}
