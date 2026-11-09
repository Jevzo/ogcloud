# shellcheck shell=bash
# Shared by the k3s scripts (sourced, not run): a k3d cluster at the pinned k3s with OgCloud's images, and
# port-forwards that outlive broken connections. KUBECONFIG names where the cluster's kubeconfig goes.

registry=ogcloud-local
tag=k3s

# k3d_cluster <name> [network]: a fresh single-node cluster at the k3s line the Kotlin and Go tests pin (D-0070,
# D-0113). k3s's own Traefik and Gateway API CRDs stay off, since platform-deps brings both.
k3d_cluster() {
  local name=$1 network=${2:-} image
  local args=(--servers 1 --agents 0 --no-lb --kubeconfig-update-default=false --kubeconfig-switch-context=false)
  if [ -n "$network" ]; then
    args+=(--network "$network")
  fi
  image=$(sed -n 's/^k3s=//p' testing/images.properties)
  k3d cluster delete "$name" >/dev/null 2>&1 || true
  k3d cluster create "$name" --image "$image" "${args[@]}" \
    --k3s-arg '--disable=traefik@server:0' --k3s-arg '--disable=gateway-api-crd@server:0' --wait --timeout 300s
  k3d kubeconfig get "$name" > "$KUBECONFIG"
  chmod 600 "$KUBECONFIG"
}

# k3d_images <name>: builds the images unless OGCLOUD_K3S_SKIP_IMAGES=true, then imports them. Platform images are
# tagged <tag>, server images <platform version>-<tag>; the contract-test gateway stays out.
k3d_images() {
  if [ "${OGCLOUD_K3S_SKIP_IMAGES:-}" != true ]; then
    REGISTRY=$registry VERSION=$tag mise run images:build
    export OGCLOUD_K3S_SKIP_IMAGES=true
  fi
  docker images --format '{{.Repository}}:{{.Tag}}' | grep -E "^$registry/[^:]+:([^:]*-)?$tag\$" | grep -v contracttest \
    | xargs k3d image import --cluster "$1"
}

# forward <log> <port-forward args>: kubectl's port-forward ends for good after one broken connection, which refused
# players cause, so it runs in a loop, as Tilt's do. Run it in the background.
forward() {
  local log=$1
  shift
  while true; do
    kubectl port-forward "$@" >>"$log" 2>&1 || true
    sleep 1
  done
}

# forward_ogcloud <work dir>: the public origin and the Minecraft edge on the ports the suite expects (testing/e2e).
forward_ogcloud() {
  forward "$1/forward-https.log" --namespace traefik svc/traefik 8443:443 &
  forward "$1/forward-minecraft.log" --namespace ogcloud svc/ogcloud-loadbalancer 25565:25565 &
}

stop_forwards() {
  local job
  for job in $(jobs -p); do
    pkill -P "$job" 2>/dev/null || true
    kill "$job" 2>/dev/null || true
  done
}
