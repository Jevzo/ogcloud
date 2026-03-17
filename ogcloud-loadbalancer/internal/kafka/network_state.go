package kafka

import (
	"strings"
	"sync"
)

type APIMotdSettings struct {
	Global      string `json:"global"`
	Maintenance string `json:"maintenance"`
}

type APIVersionNameSettings struct {
	Global      string `json:"global"`
	Maintenance string `json:"maintenance"`
}

type APINetworkResponse struct {
	MOTD                   APIMotdSettings        `json:"motd"`
	VersionName            APIVersionNameSettings `json:"versionName"`
	MaxPlayers             int                    `json:"maxPlayers"`
	DefaultGroup           string                 `json:"defaultGroup"`
	Maintenance            bool                   `json:"maintenance"`
	MaintenanceKickMessage string                 `json:"maintenanceKickMessage"`
	General                *GeneralSettings       `json:"general,omitempty"`
}

type NetworkState struct {
	mu                     sync.RWMutex
	motd                   string
	motdMaintenance        string
	versionName            string
	versionNameMaintenance string
	maxPlayers             int
	maintenance            bool
	proxyRoutingStrategy   string
	proxyPlayerCounts      map[string]int
}

func NewNetworkState() *NetworkState {
	return &NetworkState{
		motd:                   "An OgCloud Server",
		motdMaintenance:        "&cMaintenance",
		versionName:            "OgCloud",
		versionNameMaintenance: "Maintenance",
		maxPlayers:             100,
		proxyRoutingStrategy:   proxyRoutingStrategyLoadBased,
		proxyPlayerCounts:      make(map[string]int),
	}
}

func (ns *NetworkState) ApplyAPISettings(settings APINetworkResponse) {
	ns.mu.Lock()
	defer ns.mu.Unlock()
	ns.applySettingsLocked(
		settings.MOTD.Global,
		settings.MOTD.Maintenance,
		settings.VersionName.Global,
		settings.VersionName.Maintenance,
		settings.MaxPlayers,
		settings.Maintenance,
		settings.General,
	)
}

func (ns *NetworkState) update(event NetworkUpdateEvent) {
	ns.mu.Lock()
	defer ns.mu.Unlock()
	ns.applySettingsLocked(
		event.MOTD.Global,
		event.MOTD.Maintenance,
		event.VersionName.Global,
		event.VersionName.Maintenance,
		event.MaxPlayers,
		event.Maintenance,
		event.General,
	)
}

func (ns *NetworkState) GetMOTD() string {
	ns.mu.RLock()
	defer ns.mu.RUnlock()
	if ns.maintenance {
		return ns.motdMaintenance
	}
	return ns.motd
}

func (ns *NetworkState) GetVersionName() string {
	ns.mu.RLock()
	defer ns.mu.RUnlock()
	if ns.maintenance {
		return ns.versionNameMaintenance
	}
	return ns.versionName
}

func (ns *NetworkState) GetMaxPlayers() int {
	ns.mu.RLock()
	defer ns.mu.RUnlock()
	return ns.maxPlayers
}

func (ns *NetworkState) ResolveStatusVersion(clientProtocolVersion int32) (string, int) {
	ns.mu.RLock()
	defer ns.mu.RUnlock()
	if ns.maintenance {
		return ns.versionNameMaintenance, -1
	}
	if IsSupportedClientProtocolVersion(clientProtocolVersion) {
		return ns.versionName, int(clientProtocolVersion)
	}
	return supportedVersionName, -1
}

func (ns *NetworkState) GetOnlinePlayers() int {
	ns.mu.RLock()
	defer ns.mu.RUnlock()
	total := 0
	for _, count := range ns.proxyPlayerCounts {
		total += count
	}
	return total
}

func (ns *NetworkState) UpdateProxyPlayerCount(proxyID string, count int) {
	ns.mu.Lock()
	defer ns.mu.Unlock()
	ns.proxyPlayerCounts[proxyID] = count
}

func (ns *NetworkState) RemoveProxyPlayerCount(proxyID string) {
	ns.mu.Lock()
	defer ns.mu.Unlock()
	delete(ns.proxyPlayerCounts, proxyID)
}

func (ns *NetworkState) GetProxyRoutingStrategy() string {
	ns.mu.RLock()
	defer ns.mu.RUnlock()
	return ns.proxyRoutingStrategy
}

func (ns *NetworkState) applySettingsLocked(
	motd string,
	motdMaintenance string,
	versionName string,
	versionNameMaintenance string,
	maxPlayers int,
	maintenance bool,
	general *GeneralSettings,
) {
	ns.motd = motd
	ns.motdMaintenance = motdMaintenance
	ns.versionName = versionName
	ns.versionNameMaintenance = versionNameMaintenance
	ns.maxPlayers = maxPlayers
	ns.maintenance = maintenance
	if general != nil {
		ns.proxyRoutingStrategy = normalizeProxyRoutingStrategy(general.ProxyRoutingStrategy)
	}
}

func normalizeProxyRoutingStrategy(value string) string {
	switch strings.ToUpper(strings.TrimSpace(value)) {
	case proxyRoutingStrategyRoundRobin:
		return proxyRoutingStrategyRoundRobin
	case proxyRoutingStrategyLoadBased:
		return proxyRoutingStrategyLoadBased
	default:
		return proxyRoutingStrategyLoadBased
	}
}

const (
	proxyRoutingStrategyRoundRobin = "ROUND_ROBIN"
	proxyRoutingStrategyLoadBased  = "LOAD_BASED"
)
