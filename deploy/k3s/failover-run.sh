#!/usr/bin/env bash
# The failover checks on the test server (T-0609, D-0175), started there as root from /opt/ogcloud with KUBECONFIG set
# and the images tagged OGCLOUD_TAG in k3s. It reinstalls with two controllers and Valkey behind Sentinel (as on
# Hetzner, D-0048), seeds, kills the controller leader and then the Valkey primary, and afterwards reinstalls the usual
# install. OGCLOUD_PUBLIC_ORIGIN is required; the Cloudflare token comes on stdin, as install.sh gets it.
set -euo pipefail

: "${OGCLOUD_TAG:?OGCLOUD_TAG names the images to install}"
origin=${OGCLOUD_PUBLIC_ORIGIN:?OGCLOUD_PUBLIC_ORIGIN is the public origin of the install}
work=build/k3s

IFS= read -r OGCLOUD_CLOUDFLARE_API_TOKEN || true
# Without it the reinstalls would drop the public certificate's Let's Encrypt issuer (D-0160).
if [ -z "$OGCLOUD_CLOUDFLARE_API_TOKEN" ]; then
  echo "the Cloudflare API token must come on stdin" >&2
  exit 2
fi
export OGCLOUD_CLOUDFLARE_API_TOKEN
export OGCLOUD_E2E_ORIGIN="$origin" OGCLOUD_E2E_KUBE_CONTEXT=default OGCLOUD_E2E_STATE="$PWD/$work/e2e"

mkdir -p "$work"
cat > "$work/failover.yaml" <<'EOF'
controller:
  replicas: 2
valkey:
  sentinel: true
EOF

restore() {
  local status=$?
  trap - EXIT
  echo "== Putting the usual install back"
  OGCLOUD_VALUES='' deploy/k3s/install.sh || status=1
  exit "$status"
}

trap restore EXIT
echo "== Installing with two controllers and Valkey behind Sentinel"
OGCLOUD_VALUES="$PWD/$work/failover.yaml" deploy/k3s/install.sh
pnpm install --frozen-lockfile
mise run seed
pnpm --filter @ogcloud/e2e run failover
