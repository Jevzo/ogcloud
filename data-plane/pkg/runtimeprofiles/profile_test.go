package runtimeprofiles

import "testing"

func TestFind(t *testing.T) {
	tests := []struct {
		id       string
		want     Platform
		wantRole Role
		found    bool
	}{
		{"paper-26.3", PlatformPaper, RoleBackend, true},
		{"pandaspigot-1.8.8", PlatformPandaSpigot, RoleBackend, true},
		{"velocity-4.3.0", PlatformVelocity, RoleProxy, true},
		{"spigot-1.8", "", "", false},
	}
	for _, tt := range tests {
		t.Run(tt.id, func(t *testing.T) {
			profile, found := Find(tt.id)
			if found != tt.found || profile.Platform != tt.want || profile.Role != tt.wantRole {
				t.Errorf("Find(%q) = %+v, %v", tt.id, profile, found)
			}
		})
	}
}

func TestBackendProtocolsAreAdmitted(t *testing.T) {
	for _, profile := range All() {
		if profile.Role == RoleBackend && (profile.Protocol < MinClientProtocol || profile.Protocol > MaxClientProtocol) {
			t.Errorf("%s protocol %d outside %d..%d", profile.ID, profile.Protocol, MinClientProtocol, MaxClientProtocol)
		}
	}
}
