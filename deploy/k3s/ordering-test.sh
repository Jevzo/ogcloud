#!/usr/bin/env bash
# The k3s ordering test (T-0605, D-0159): a fresh k3d cluster at the k3s line kube-hetzner installs gets every
# HelmChart at once, rendered by the same Terraform module as Hetzner (D-0156), and must converge with no ordering.
# OGCLOUD_K3S_KEEP=true keeps the cluster afterwards; OGCLOUD_K3S_SKIP_IMAGES=true imports the images built before;
# OGCLOUD_K3S_E2E=true then runs the end-to-end suite on it (T-0606).
set -euo pipefail

cluster=ogcloud-k3s
work=build/k3s
registry=ogcloud-local
tag=k3s

mkdir -p "$work"
export KUBECONFIG="$PWD/$work/kubeconfig"

finish() {
  local status=$?
  for forward in $(jobs -p); do
    pkill -P "$forward" 2>/dev/null || true
    kill "$forward" 2>/dev/null || true
  done
  if [ "${OGCLOUD_K3S_KEEP:-}" != true ]; then
    k3d cluster delete "$cluster" >/dev/null 2>&1 || true
  fi
  exit "$status"
}
trap finish EXIT

# kubectl's port-forward ends for good after one broken connection, which refused players cause; Tilt loops it too.
forward() {
  local log=$1
  shift
  while true; do
    kubectl port-forward "$@" >>"$log" 2>&1 || true
    sleep 1
  done
}

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

# The origin carries the port the suite reaches Traefik on, so the dashboard's registered callback matches.
OGCLOUD_TAG=$tag OGCLOUD_PUBLIC_ORIGIN=https://ogcloud.localhost:8443 OGCLOUD_PROXY_ONLINE_MODE=false \
  deploy/k3s/install.sh

# The same documents again, as every later Terraform apply sends them: nothing that k3s rewrote may conflict.
kubectl apply --server-side -f "$work/manifest.yaml" >/dev/null
echo "The documents apply again without conflicts"

if [ "${OGCLOUD_K3S_E2E:-}" = true ]; then
  forward "$work/forward-https.log" --namespace traefik svc/traefik 8443:443 &
  forward "$work/forward-minecraft.log" --namespace ogcloud svc/ogcloud-loadbalancer 25565:25565 &
  # A fresh cluster has a fresh owner, so the suite's state from earlier runs must not be reused.
  rm -rf "$work/e2e"
  OGCLOUD_E2E_KUBE_CONTEXT="k3d-$cluster" OGCLOUD_E2E_STATE="$PWD/$work/e2e" mise run test:e2e
fi
