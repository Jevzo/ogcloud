package kafka

import "sync"

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
}

type NetworkState struct {
	mu                     sync.RWMutex
	motd                   string
	motdMaintenance        string
	versionName            string
	versionNameMaintenance string
	maxPlayers             int
	maintenance            bool
	proxyPlayerCounts      map[string]int
}

func NewNetworkState() *NetworkState {
	return &NetworkState{
		motd:                   "An OgCloud Server",
		motdMaintenance:        "&cMaintenance",
		versionName:            "OgCloud",
		versionNameMaintenance: "Maintenance",
		maxPlayers:             100,
		proxyPlayerCounts:      make(map[string]int),
	}
}

func (ns *NetworkState) ApplyAPISettings(settings APINetworkResponse) {
	ns.mu.Lock()
	defer ns.mu.Unlock()
	ns.motd = settings.MOTD.Global
	ns.motdMaintenance = settings.MOTD.Maintenance
	ns.versionName = settings.VersionName.Global
	ns.versionNameMaintenance = settings.VersionName.Maintenance
	ns.maxPlayers = settings.MaxPlayers
	ns.maintenance = settings.Maintenance
}

func (ns *NetworkState) update(event NetworkUpdateEvent) {
	ns.mu.Lock()
	defer ns.mu.Unlock()
	ns.motd = event.MOTD.Global
	ns.motdMaintenance = event.MOTD.Maintenance
	ns.versionName = event.VersionName.Global
	ns.versionNameMaintenance = event.VersionName.Maintenance
	ns.maxPlayers = event.MaxPlayers
	ns.maintenance = event.Maintenance
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

func (ns *NetworkState) GetProtocolVersion() int {
	ns.mu.RLock()
	defer ns.mu.RUnlock()
	if ns.maintenance {
		return -1
	}
	return ProtocolVersion1_21_11
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
