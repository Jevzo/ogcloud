package profiles

import (
	"encoding/json"
	"fmt"
	"strings"
)

// Bake variables the image build sets; the tags follow the controller's image names (PodSpecBuilder).
const (
	bakeRegistry   = "REGISTRY"
	bakeVersion    = "VERSION"
	bakeDefault    = "default"
	bakeContext    = "images/server"
	defaultImageNS = "ghcr.io/jevzo/ogcloud"
	devVersion     = "dev"
)

type bakeFile struct {
	Variable map[string]bakeVariable `json:"variable"`
	Group    map[string]bakeGroup    `json:"group"`
	Target   map[string]bakeTarget   `json:"target"`
}

type bakeVariable struct {
	Default string `json:"default"`
}

type bakeGroup struct {
	Targets []string `json:"targets"`
}

type bakeTarget struct {
	Context string            `json:"context"`
	Target  string            `json:"target"`
	Args    map[string]string `json:"args"`
	Tags    []string          `json:"tags"`
}

// RenderBake writes a docker buildx bake file with one server image per profile (D-0139): the Dockerfile's stage
// for the platform, the pinned jar and the platform's JVM flags as build args.
func RenderBake(c Catalog) ([]byte, error) {
	file := bakeFile{
		Variable: map[string]bakeVariable{bakeRegistry: {defaultImageNS}, bakeVersion: {devVersion}},
		Group:    map[string]bakeGroup{bakeDefault: {}},
		Target:   map[string]bakeTarget{},
	}
	targets := make([]string, 0, len(c.Profiles))
	for _, p := range c.Profiles {
		name := BakeTarget(p.ID)
		targets = append(targets, name)
		file.Target[name] = bakeTarget{
			Context: bakeContext,
			Target:  p.Platform,
			Args: map[string]string{
				"SERVER_URL":    p.Server.URL,
				"SERVER_SHA256": p.Server.SHA256,
				"SERVER_SOURCE": p.Server.Source,
				"JVM_FLAGS":     strings.Join(c.JVMFlags[p.Platform], " "),
			},
			Tags: []string{fmt.Sprintf("${%s}/%s:%s-${%s}", bakeRegistry, p.Platform, p.Version, bakeVersion)},
		}
	}
	file.Group[bakeDefault] = bakeGroup{Targets: targets}
	encoded, err := json.MarshalIndent(file, "", "  ")
	if err != nil {
		return nil, fmt.Errorf("encode bake file: %w", err)
	}
	return append(encoded, '\n'), nil
}

// BakeTarget is a profile's target name; bake names allow no dots.
func BakeTarget(profileID string) string {
	return strings.ReplaceAll(profileID, ".", "_")
}
