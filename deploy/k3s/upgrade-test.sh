#!/usr/bin/env bash
# The upgrade test (T-0608, D-0172): N-1 is installed on a fresh k3d cluster from a git worktree, seeded with its own
# suite, then upgraded to this checkout by applying its documents as the next Terraform apply would; the data must
# survive and the whole end-to-end suite must pass. OGCLOUD_UPGRADE_FROM names N-1 (default: the newest v2 tag, else
# origin/v2); one that is this checkout fails unless OGCLOUD_UPGRADE_ALLOW_SAME=true (D-0191). OGCLOUD_K3S_KEEP=true
# keeps the cluster and the worktree; OGCLOUD_K3S_SKIP_IMAGES=true imports images built before.
set -euo pipefail

# shellcheck source=deploy/k3s/lib.sh
source deploy/k3s/lib.sh

cluster=ogcloud-upgrade
work=build/k3s
old="$PWD/build/upgrade/n-1"
old_tag=n-1
state="$PWD/$work/e2e"

from=${OGCLOUD_UPGRADE_FROM:-$(git describe --tags --abbrev=0 --match 'v2.*' 2>/dev/null || echo origin/v2)}
# A test from HEAD to HEAD crosses no version and would pass on nothing (D-0191).
if [ "$(git rev-parse "$from^{commit}")" = "$(git rev-parse HEAD)" ] && [ "${OGCLOUD_UPGRADE_ALLOW_SAME:-}" != true ]; then
  echo "N-1 ($from) is this checkout, so the upgrade would change nothing; name another ref in OGCLOUD_UPGRADE_FROM," \
    "or set OGCLOUD_UPGRADE_ALLOW_SAME=true to run it anyway" >&2
  exit 2
fi

mkdir -p "$work"
export KUBECONFIG="$PWD/$work/kubeconfig"
export OGCLOUD_E2E_KUBE_CONTEXT="k3d-$cluster" OGCLOUD_E2E_STATE="$state"

finish() {
  local status=$?
  stop_forwards
  if [ "$status" -ne 0 ]; then
    save_logs "$work/logs"
  fi
  if [ "${OGCLOUD_K3S_KEEP:-}" != true ]; then
    k3d cluster delete "$cluster" >/dev/null 2>&1 || true
    git worktree remove --force "$old" >/dev/null 2>&1 || true
  fi
  exit "$status"
}
trap finish EXIT

install() {
  OGCLOUD_TAG=$1 OGCLOUD_PUBLIC_ORIGIN=https://ogcloud.localhost:8443 OGCLOUD_PROXY_ONLINE_MODE=false \
    OGCLOUD_VALUES=$k3d_values deploy/k3s/install.sh
}

echo "== N-1: $from ($(git rev-parse --short "$from^{commit}"))"
git worktree remove --force "$old" >/dev/null 2>&1 || true
git worktree add --detach "$old" "$from" >/dev/null
mise trust --quiet "$old/mise.toml"
(cd "$old" && mise x -- pnpm install --frozen-lockfile >/dev/null)
k3d_cluster "$cluster"
k3d_images "$cluster" "$old_tag" "$old"
(cd "$old" && install "$old_tag")
forward_ogcloud "$work"
rm -rf "$state"
(cd "$old" && mise run seed)
mise x -- pnpm --filter @ogcloud/e2e run restore snapshot

echo "== Upgrade to $(git rev-parse --short HEAD)"
k3d_images "$cluster"
# The same Terraform inputs: the seed N-1 was installed with.
cp "$old/$work/seed.yaml" "$work/seed.yaml"
install "$tag"
# The upgrade replaced the pods behind the forwards, which notice only when the next connection breaks.
stop_forwards
forward_ogcloud "$work"
mise x -- pnpm --filter @ogcloud/e2e run restore verify
mise run test:e2e
echo "The upgrade from $from passed"
