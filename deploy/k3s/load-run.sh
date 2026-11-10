#!/usr/bin/env bash
# One load run on the test server (T-0609, D-0175), started there as root from /opt/ogcloud with KUBECONFIG set and
# the images tagged OGCLOUD_TAG in k3s. While the proxies run offline only this host may reach the Minecraft port: the
# Service admits only this host and the pods' range, and ufw drops the public rule. It reinstalls with offline proxies
# and room for the load, seeds, runs the load, then reinstalls the public install and reopens the port only once
# every proxy runs in online mode again. OGCLOUD_PUBLIC_ORIGIN and OGCLOUD_LOAD_ADDRESS (this host's public address)
# are required; the Cloudflare token comes on stdin, as install.sh gets it.
set -euo pipefail

: "${OGCLOUD_TAG:?OGCLOUD_TAG names the images to install}"
origin=${OGCLOUD_PUBLIC_ORIGIN:?OGCLOUD_PUBLIC_ORIGIN is the public origin of the install}
address=${OGCLOUD_LOAD_ADDRESS:?OGCLOUD_LOAD_ADDRESS is the public address of this host}
work=build/k3s
pods_range=10.42.0.0/16
online_timeout_seconds=1200

IFS= read -r OGCLOUD_CLOUDFLARE_API_TOKEN || true
# Without it the reinstalls would drop the public certificate's Let's Encrypt issuer (D-0160).
if [ -z "$OGCLOUD_CLOUDFLARE_API_TOKEN" ]; then
  echo "the Cloudflare API token must come on stdin" >&2
  exit 2
fi
export OGCLOUD_CLOUDFLARE_API_TOKEN
export OGCLOUD_E2E_ORIGIN="$origin" OGCLOUD_E2E_MINECRAFT="$address:25565" OGCLOUD_E2E_KUBE_CONTEXT=default \
  OGCLOUD_E2E_STATE="$PWD/$work/e2e"

mkdir -p "$work"
# The bots all come from this host, and 30 backends at Paper's minimum (D-0177) outgrow the k3s quota.
cat > "$work/load.yaml" <<EOF
loadbalancer:
  service:
    sourceRanges: [$address/32, $pods_range]
  limits:
    openPerAddress: 1000
    newPerSecond: 200
    newBurst: 400
servers:
  quota:
    requests.cpu: "12"
    requests.memory: 56Gi
EOF

close_port() {
  ufw delete allow 25565/tcp >/dev/null
  ufw allow from "$address" to any port 25565 proto tcp comment "load run" >/dev/null
}

open_port() {
  ufw allow 25565/tcp comment Minecraft >/dev/null
  ufw delete allow from "$address" to any port 25565 proto tcp >/dev/null
}

# Every running proxy has the bundle's velocity.toml in online mode; a proxy on its way out counts against it.
proxies_online() {
  local pods pod
  pods=$(kubectl -n ogcloud-servers get pods -l cloud.ogwars.io/role=proxy --field-selector=status.phase=Running -o name)
  [ -n "$pods" ] || return 1
  for pod in $pods; do
    kubectl -n ogcloud-servers exec "$pod" -c server -- grep -qx 'online-mode = true' /data/velocity.toml || return 1
  done
}

await_online() {
  local deadline=$((SECONDS + online_timeout_seconds))
  until proxies_online; do
    if [ "$SECONDS" -ge "$deadline" ]; then
      echo "the proxies did not all run in online mode within ${online_timeout_seconds} s" >&2
      return 1
    fi
    sleep 10
  done
}

restore() {
  local status=$?
  trap - EXIT
  echo "== Putting the public install back"
  if OGCLOUD_PROXY_ONLINE_MODE=true OGCLOUD_VALUES='' deploy/k3s/install.sh && await_online; then
    open_port
    echo "Every proxy runs in online mode; the Minecraft port is public again"
  else
    echo "The restore failed, so the Minecraft port stays closed to the public" >&2
    status=1
  fi
  exit "$status"
}

trap restore EXIT
close_port
echo "== Installing with offline proxies and room for the load"
OGCLOUD_PROXY_ONLINE_MODE=false OGCLOUD_VALUES="$PWD/$work/load.yaml" deploy/k3s/install.sh
timeout 5 bash -c "</dev/tcp/$address/25565"
pnpm install --frozen-lockfile
mise run seed
pnpm --filter @ogcloud/e2e run load
