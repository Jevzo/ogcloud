package loadbalancer

import (
	"bufio"
	"encoding/json"
	"fmt"
	"io"
	"strings"

	eventsv1 "github.com/jevzo/ogcloud/data-plane/pkg/gen/proto/ogcloud/events/v1"
	"github.com/jevzo/ogcloud/data-plane/pkg/runtimeprofiles"
)

// Sizes the status exchange accepts from the client.
const (
	maxStatusRequestBytes = 16
	pingPayloadBytes      = 8
)

// incompatibleProtocol is the version a client shows in red, so it knows it cannot join.
const incompatibleProtocol = -1

// Kick texts; players see them when the network cannot take them.
const (
	noProxyMessage = "No proxy is available right now. Please try again in a moment."
)

// unsupportedVersionMessage names the client range of the runtime-profile catalog.
var unsupportedVersionMessage = fmt.Sprintf("Unsupported Minecraft version. Supported versions are %s through %s.",
	versionOf(runtimeprofiles.MinClientProtocol), versionOf(runtimeprofiles.MaxClientProtocol))

// supportedVersions is the version a status ping shows clients outside the range.
var supportedVersions = versionOf(runtimeprofiles.MinClientProtocol) + "-" + versionOf(runtimeprofiles.MaxClientProtocol)

func versionOf(protocol int) string {
	for _, profile := range runtimeprofiles.All() {
		if profile.Role == runtimeprofiles.RoleBackend && profile.Protocol == protocol {
			return profile.Version
		}
	}
	return fmt.Sprintf("protocol %d", protocol)
}

func supported(protocol int32) bool {
	return protocol >= runtimeprofiles.MinClientProtocol && protocol <= runtimeprofiles.MaxClientProtocol
}

type statusResponse struct {
	Version     statusVersion `json:"version"`
	Players     statusPlayers `json:"players"`
	Description textComponent `json:"description"`
}

type statusVersion struct {
	Name     string `json:"name"`
	Protocol int32  `json:"protocol"`
}

type statusPlayers struct {
	Max    uint32 `json:"max"`
	Online uint32 `json:"online"`
}

type textComponent struct {
	Text string `json:"text"`
}

// status builds the server-list entry: maintenance texts while in maintenance, the supported range for
// clients outside it, the network's texts otherwise.
func status(network *eventsv1.NetworkSettingsUpdated, online uint32, clientProtocol int32) statusResponse {
	response := statusResponse{
		Version:     statusVersion{Name: network.GetVersionName(), Protocol: clientProtocol},
		Players:     statusPlayers{Max: network.GetMaxPlayers(), Online: online},
		Description: textComponent{Text: legacyColors(network.GetMotd())},
	}
	switch {
	case network.GetMaintenance():
		response.Version = statusVersion{Name: legacyColors(network.GetMaintenanceVersionName()), Protocol: incompatibleProtocol}
		response.Description.Text = legacyColors(network.GetMaintenanceMotd())
	case !supported(clientProtocol):
		response.Version = statusVersion{Name: supportedVersions, Protocol: incompatibleProtocol}
	}
	return response
}

// answerStatus serves the status exchange: the status request, then an optional ping echoed as pong.
func answerStatus(r *bufio.Reader, w io.Writer, response statusResponse) error {
	id, _, _, err := readPacket(r, maxStatusRequestBytes)
	if err != nil {
		return err
	}
	if id != packetStatusRequest {
		return fmt.Errorf("%w: status request id %d", errMalformed, id)
	}
	body, err := json.Marshal(response)
	if err != nil {
		return fmt.Errorf("encode status: %w", err)
	}
	if err := writePacket(w, packetStatusResponse, appendString(nil, string(body))); err != nil {
		return err
	}
	id, payload, _, err := readPacket(r, maxStatusRequestBytes)
	if err != nil {
		// Some clients close after the status; that is not an error worth reporting.
		return nil //nolint:nilerr // the exchange is complete without a ping.
	}
	if id != packetPing || len(payload) != pingPayloadBytes {
		return fmt.Errorf("%w: ping", errMalformed)
	}
	return writePacket(w, packetPing, payload)
}

// refuseLogin ends a login with message.
func refuseLogin(w io.Writer, message string) error {
	body, err := json.Marshal(textComponent{Text: message})
	if err != nil {
		return fmt.Errorf("encode disconnect: %w", err)
	}
	return writePacket(w, packetLoginDisconnect, appendString(nil, string(body)))
}

// legacyColors turns the settings' & colour codes into the § codes clients render.
func legacyColors(text string) string {
	var b strings.Builder
	runes := []rune(text)
	for i := 0; i < len(runes); i++ {
		if runes[i] == '&' && i+1 < len(runes) && strings.ContainsRune("0123456789abcdefklmnorABCDEFKLMNOR", runes[i+1]) {
			b.WriteRune('§')
			continue
		}
		b.WriteRune(runes[i])
	}
	return b.String()
}
