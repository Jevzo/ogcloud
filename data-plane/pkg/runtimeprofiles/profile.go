// Package runtimeprofiles exposes the runtime profile catalog generated from
// contracts/runtime-profiles.yaml (D-0006): which server platforms OgCloud runs and how.
package runtimeprofiles

// Platform is the server software a profile runs.
type Platform string

// Supported platforms.
const (
	PlatformPaper       Platform = "paper"
	PlatformPandaSpigot Platform = "pandaspigot"
	PlatformVelocity    Platform = "velocity"
	PlatformFabric      Platform = "fabric"
	PlatformNeoForge    Platform = "neoforge"
)

// Role says whether a profile runs game servers or proxies.
type Role string

// Profile roles.
const (
	RoleBackend Role = "backend"
	RoleProxy   Role = "proxy"
)

// Profile is one runnable server platform version.
type Profile struct {
	ID       string
	Platform Platform
	Role     Role
	// Version is the platform's own version, e.g. "26.3" for Paper or "4.3.0" for Velocity.
	Version string
	// Protocol is the native Minecraft protocol number of a backend; 0 for proxies.
	Protocol    int
	JavaVersion int
}

// Find returns the profile with the given id.
func Find(id string) (Profile, bool) {
	for _, profile := range All() {
		if profile.ID == id {
			return profile, true
		}
	}
	return Profile{}, false
}
