#!/usr/bin/env bash
# The k3s ordering test (T-0605, D-0159): a fresh k3d cluster at the k3s line kube-hetzner installs gets every
# HelmChart at once, rendered by the same Terraform module as Hetzner (D-0156), and must converge with no ordering.
# OGCLOUD_K3S_KEEP=true keeps the cluster afterwards; OGCLOUD_K3S_SKIP_IMAGES=true imports the images built before;
# OGCLOUD_K3S_E2E=true then runs the end-to-end suite on it (T-0606).
set -euo pipefail
# shellcheck source=deploy/k3s/lib.sh
source deploy/k3s/lib.sh

cluster=ogcloud-k3s
work=build/k3s

mkdir -p "$work"
export KUBECONFIG="$PWD/$work/kubeconfig"

finish() {
  local status=$?
  stop_forwards
  if [ "${OGCLOUD_K3S_KEEP:-}" != true ]; then
    k3d cluster delete "$cluster" >/dev/null 2>&1 || true
  fi
  exit "$status"
}
trap finish EXIT

k3d_cluster "$cluster"
k3d_images "$cluster"

# The origin carries the port the suite reaches Traefik on, so the dashboard's registered callback matches.
OGCLOUD_TAG=$tag OGCLOUD_PUBLIC_ORIGIN=https://ogcloud.localhost:8443 OGCLOUD_PROXY_ONLINE_MODE=false \
  deploy/k3s/install.sh

# The same documents again, as every later Terraform apply sends them: nothing that k3s rewrote may conflict.
kubectl apply --server-side -f "$work/manifest.yaml" >/dev/null
echo "The documents apply again without conflicts"

if [ "${OGCLOUD_K3S_E2E:-}" = true ]; then
  forward_ogcloud "$work"
  # A fresh cluster has a fresh owner, so the suite's state from earlier runs must not be reused.
  rm -rf "$work/e2e"
  OGCLOUD_E2E_KUBE_CONTEXT="k3d-$cluster" OGCLOUD_E2E_STATE="$PWD/$work/e2e" mise run test:e2e
fi
