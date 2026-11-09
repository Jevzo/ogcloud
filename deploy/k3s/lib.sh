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

built=" "

# k3d_images <name> [tag] [checkout]: builds the checkout's images (this one by default) tagged <tag> once per run,
# unless OGCLOUD_K3S_SKIP_IMAGES=true, then imports them. Platform images are tagged <tag>, server images
# <platform version>-<tag>; the contract-test gateway stays out.
k3d_images() {
  local name=$1 version=${2:-$tag} checkout=${3:-.}
  if [ "${OGCLOUD_K3S_SKIP_IMAGES:-}" != true ] && [[ $built != *" $version "* ]]; then
    (cd "$checkout" && REGISTRY=$registry VERSION=$version mise run images:build)
    built="$built$version "
  fi
  docker images --format '{{.Repository}}:{{.Tag}}' | grep -E "^$registry/[^:]+:([^:]*-)?$version\$" \
    | grep -v contracttest | xargs k3d image import --cluster "$name"
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
# A port another process holds, such as a forward left over from an earlier run, would serve the wrong cluster.
forward_ogcloud() {
  local port deadline
  for port in 8443 25565; do
    # Forwards stopped a moment ago may still be closing.
    deadline=$((SECONDS + 10))
    while [ "$SECONDS" -lt "$deadline" ]; do
      (exec 3<>"/dev/tcp/127.0.0.1/$port") 2>/dev/null || continue 2
      sleep 1
    done
    echo "port $port is taken, maybe by a port-forward an earlier run left behind" >&2
    return 1
  done
  forward "$1/forward-https.log" --namespace traefik svc/traefik 8443:443 &
  forward "$1/forward-minecraft.log" --namespace ogcloud svc/ogcloud-loadbalancer 25565:25565 &
  await_origin
}

# await_origin: until the public origin answers through its forward, which reconnects when Traefik's pod changes.
await_origin() {
  local deadline=$((SECONDS + 300))
  until curl -fsk --resolve ogcloud.localhost:8443:127.0.0.1 -o /dev/null \
    https://ogcloud.localhost:8443/.well-known/openid-configuration; do
    if [ "$SECONDS" -ge "$deadline" ]; then
      echo "the origin did not answer through its forward within 300 s" >&2
      return 1
    fi
    sleep 1
  done
}

stop_forwards() {
  local job
  for job in $(jobs -p); do
    pkill -P "$job" 2>/dev/null || true
    kill "$job" 2>/dev/null || true
  done
}
