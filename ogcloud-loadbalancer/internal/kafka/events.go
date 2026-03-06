package kafka

type MotdSettings struct {
	Global      string `json:"global"`
	Maintenance string `json:"maintenance"`
}

type VersionNameSettings struct {
	Global      string `json:"global"`
	Maintenance string `json:"maintenance"`
}

type NetworkUpdateEvent struct {
	MOTD                   MotdSettings        `json:"motd"`
	VersionName            VersionNameSettings `json:"versionName"`
	MaxPlayers             int                 `json:"maxPlayers"`
	DefaultGroup           string              `json:"defaultGroup"`
	Maintenance            bool                `json:"maintenance"`
	MaintenanceKickMessage string              `json:"maintenanceKickMessage"`
}

type ServerLifecycleEvent struct {
	ServerID    string `json:"serverId"`
	Group       string `json:"group"`
	Type        string `json:"type"`
	State       string `json:"state"`
	DisplayName string `json:"displayName"`
	PodName     string `json:"podName"`
	PodIP       string `json:"podIp"`
	Port        int    `json:"port"`
}

type ProxyHeartbeatEvent struct {
	ProxyID      string `json:"proxyId"`
	PodIP        string `json:"podIp"`
	Port         int    `json:"port"`
	PlayerCount  int    `json:"playerCount"`
	MaxPlayers   int    `json:"maxPlayers"`
	MemoryUsedMb int64  `json:"memoryUsedMb"`
}
