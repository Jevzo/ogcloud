package loadbalancer

import (
	"bufio"
	"bytes"
	"encoding/json"
	"errors"
	"strings"
	"testing"

	eventsv1 "github.com/jevzo/ogcloud/data-plane/pkg/gen/proto/ogcloud/events/v1"
	"github.com/jevzo/ogcloud/data-plane/pkg/runtimeprofiles"
)

var network = &eventsv1.NetworkSettingsUpdated{
	MaxPlayers: 500,
	ServerList: &eventsv1.ServerListTexts{
		MotdJson:                     `{"text":"","extra":[{"text":"Welcome","color":"#ff8800"}]}`,
		MotdLegacy:                   "§6Welcome",
		MaintenanceMotdJson:          `{"text":"","extra":[{"text":"Maintenance","color":"red"}]}`,
		MaintenanceMotdLegacy:        "§cMaintenance",
		VersionNameLegacy:            "OgWars",
		MaintenanceVersionNameLegacy: "§4Closed",
	},
}

func TestStatusShowsTheNetworkOrWhyAClientCannotJoin(t *testing.T) {
	closed := &eventsv1.NetworkSettingsUpdated{Maintenance: true, ServerList: network.GetServerList()}
	tests := []struct {
		name     string
		network  *eventsv1.NetworkSettingsUpdated
		protocol int32
		want     string
	}{
		{
			"open, 1.16 or newer", network, 777,
			`{"version":{"name":"OgWars","protocol":777},"players":{"max":500,"online":42},` +
				`"description":{"text":"","extra":[{"text":"Welcome","color":"#ff8800"}]}}`,
		},
		{
			"open, before 1.16", network, 47,
			`{"version":{"name":"OgWars","protocol":47},"players":{"max":500,"online":42},"description":{"text":"§6Welcome"}}`,
		},
		{
			"1.15.2, the last before hex", network, firstHexProtocol - 1,
			`{"version":{"name":"OgWars","protocol":734},"players":{"max":500,"online":42},"description":{"text":"§6Welcome"}}`,
		},
		{
			"1.16, the first with hex", network, firstHexProtocol,
			`{"version":{"name":"OgWars","protocol":735},"players":{"max":500,"online":42},` +
				`"description":{"text":"","extra":[{"text":"Welcome","color":"#ff8800"}]}}`,
		},
		{
			"newer than the catalog", network, runtimeprofiles.MaxClientProtocol + 1,
			`{"version":{"name":"1.8.8-26.3","protocol":-1},"players":{"max":500,"online":42},` +
				`"description":{"text":"","extra":[{"text":"Welcome","color":"#ff8800"}]}}`,
		},
		{
			"too old", network, 5,
			`{"version":{"name":"1.8.8-26.3","protocol":-1},"players":{"max":500,"online":42},"description":{"text":"§6Welcome"}}`,
		},
		{
			"maintenance", closed, 777,
			`{"version":{"name":"§4Closed","protocol":-1},"players":{"max":0,"online":42},` +
				`"description":{"text":"","extra":[{"text":"Maintenance","color":"red"}]}}`,
		},
		{
			"maintenance, before 1.16", closed, 47,
			`{"version":{"name":"§4Closed","protocol":-1},"players":{"max":0,"online":42},"description":{"text":"§cMaintenance"}}`,
		},
		{
			"nothing rendered yet", &eventsv1.NetworkSettingsUpdated{MaxPlayers: 5}, 777,
			`{"version":{"name":"","protocol":777},"players":{"max":5,"online":42},"description":{"text":""}}`,
		},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			response, err := status(tt.network, 42, tt.protocol)
			if err != nil {
				t.Fatal(err)
			}
			got, err := json.Marshal(response)
			if err != nil {
				t.Fatal(err)
			}
			if string(got) != tt.want {
				t.Errorf("got  %s\nwant %s", got, tt.want)
			}
		})
	}
}

func TestTheStatusExchangeEchoesThePing(t *testing.T) {
	ping := []byte{1, 2, 3, 4, 5, 6, 7, 8}
	client := append(frame(appendVarInt(nil, packetStatusRequest)), frame(append(appendVarInt(nil, packetPing), ping...))...)
	var out bytes.Buffer

	response, err := status(network, 1, 777)
	if err != nil {
		t.Fatal(err)
	}
	err = answerStatus(bufio.NewReader(bytes.NewReader(client)), &out, response)

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

func TestAStatusWhoseRenderedTextsAreNoJSONFailsToEncode(t *testing.T) {
	broken := &eventsv1.NetworkSettingsUpdated{ServerList: &eventsv1.ServerListTexts{MotdJson: "{"}}
	response, err := status(broken, 0, firstHexProtocol)
	if err != nil {
		t.Fatal(err)
	}
	client := frame(appendVarInt(nil, packetStatusRequest))

	err = answerStatus(bufio.NewReader(bytes.NewReader(client)), &bytes.Buffer{}, response)

	if !errors.Is(err, errEncode) {
		t.Errorf("err = %v, want errEncode", err)
	}
}
