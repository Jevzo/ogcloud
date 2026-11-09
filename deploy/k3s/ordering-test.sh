#!/usr/bin/env bash
# The k3s ordering test (T-0605, D-0159): a fresh k3d cluster at the k3s line kube-hetzner installs gets every
# HelmChart at once, rendered by the same Terraform module as Hetzner (D-0156), and must converge with no ordering.
# OGCLOUD_K3S_KEEP=true keeps the cluster afterwards; OGCLOUD_K3S_SKIP_IMAGES=true imports the images built before.
set -euo pipefail

cluster=ogcloud-k3s
work=build/k3s
registry=ogcloud-local
tag=k3s

mkdir -p "$work"
export KUBECONFIG="$PWD/$work/kubeconfig"

finish() {
  local status=$?
  if [ "${OGCLOUD_K3S_KEEP:-}" != true ]; then
    k3d cluster delete "$cluster" >/dev/null 2>&1 || true
  fi
  exit "$status"
}
trap finish EXIT

# The k3s line the Kotlin and Go tests pin (D-0070, D-0113); k3s's own Traefik and Gateway API CRDs stay off, since
# platform-deps brings both.
k3s_image=$(sed -n 's/^k3s=//p' testing/images.properties)
k3d cluster delete "$cluster" >/dev/null 2>&1 || true
k3d cluster create "$cluster" --image "$k3s_image" --servers 1 --agents 0 --no-lb \
  --k3s-arg '--disable=traefik@server:0' --k3s-arg '--disable=gateway-api-crd@server:0' \
  --kubeconfig-update-default=false --kubeconfig-switch-context=false --wait --timeout 300s
k3d kubeconfig get "$cluster" > "$KUBECONFIG"
chmod 600 "$KUBECONFIG"

if [ "${OGCLOUD_K3S_SKIP_IMAGES:-}" != true ]; then
  REGISTRY=$registry VERSION=$tag mise run images:build
fi
# Platform images are tagged <tag>, server images <platform version>-<tag>; the contract-test gateway stays out.
docker images --format '{{.Repository}}:{{.Tag}}' | grep -E "^$registry/[^:]+:([^:]*-)?$tag\$" | grep -v contracttest \
  | xargs k3d image import --cluster "$cluster"

OGCLOUD_TAG=$tag deploy/k3s/install.sh
