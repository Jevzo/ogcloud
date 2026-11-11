package loadbalancer

import (
	"bufio"
	"encoding/json"
	"errors"
	"fmt"
	"io"

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
	Version     statusVersion   `json:"version"`
	Players     statusPlayers   `json:"players"`
	Description json.RawMessage `json:"description"`
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

// firstHexProtocol is 1.16's, the first whose chat components take hex colours.
const firstHexProtocol = 735

// errEncode marks a status answer that could not be written, which points at the rendered texts, not the client.
var errEncode = errors.New("encode status")

// status builds the server-list entry from the texts the controller rendered, so no tags are parsed here (D-0202):
// maintenance texts while in maintenance, the supported range for clients outside it, the network's texts otherwise.
func status(network *eventsv1.NetworkSettingsUpdated, online uint32, clientProtocol int32) (statusResponse, error) {
	texts := network.GetServerList()
	motdJSON, motdLegacy := texts.GetMotdJson(), texts.GetMotdLegacy()
	version := statusVersion{Name: texts.GetVersionNameLegacy(), Protocol: clientProtocol}
	switch {
	case network.GetMaintenance():
		motdJSON, motdLegacy = texts.GetMaintenanceMotdJson(), texts.GetMaintenanceMotdLegacy()
		version = statusVersion{Name: texts.GetMaintenanceVersionNameLegacy(), Protocol: incompatibleProtocol}
	case !supported(clientProtocol):
		version = statusVersion{Name: supportedVersions, Protocol: incompatibleProtocol}
	}
	description, err := descriptionFor(motdJSON, motdLegacy, clientProtocol)
	if err != nil {
		return statusResponse{}, err
	}
	return statusResponse{
		Version:     version,
		Players:     statusPlayers{Max: network.GetMaxPlayers(), Online: online},
		Description: description,
	}, nil
}

// descriptionFor is a MOTD as a client of clientProtocol reads it: the controller's component JSON from 1.16 on, its
// legacy text in a component before (D-0204), and an empty text before the controller has rendered any.
func descriptionFor(componentJSON, legacy string, clientProtocol int32) (json.RawMessage, error) {
	if clientProtocol < firstHexProtocol {
		body, err := json.Marshal(textComponent{Text: legacy})
		if err != nil {
			return nil, fmt.Errorf("%w: legacy MOTD: %w", errEncode, err)
		}
		return body, nil
	}
	if componentJSON == "" {
		return json.RawMessage(`{"text":""}`), nil
	}
	return json.RawMessage(componentJSON), nil
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
		return fmt.Errorf("%w: %w", errEncode, err)
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
