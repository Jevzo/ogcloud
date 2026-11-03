# Platform images (D-0128) from the artifacts `mise run images:build` stages in build/images. REGISTRY and VERSION
# come from docker-bake.json, which is always passed first; tags follow the controller's image names.
group "platform" {
  targets = ["spring", "go"]
}

target "_platform" {
  context  = "images/platform"
  contexts = { artifacts = "build/images" }
}

target "spring" {
  name     = service
  matrix   = { service = ["api", "controller"] }
  inherits = ["_platform"]
  target   = service
  args     = { SERVICE = service }
  tags     = ["${REGISTRY}/${service}:${VERSION}"]
}

target "go" {
  name     = binary
  matrix   = { binary = ["gateway", "loadbalancer", "template-loader", "bootstrap"] }
  inherits = ["_platform"]
  target   = "go"
  args     = { BINARY = binary }
  tags     = ["${REGISTRY}/${binary}:${VERSION}"]
}
