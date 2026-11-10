#!/usr/bin/env bash
# The restore drill (T-0607, D-0166 to D-0168): OgCloud on a fresh k3d cluster backs up into a SeaweedFS outside it,
# the cluster is deleted, and a new one restores PostgreSQL, the templates and Infisical from the backup and the
# exported root key. Then the owner signs in, the groups and templates are back, and bots join.
# OGCLOUD_K3S_KEEP=true keeps the restored cluster and the backup target; OGCLOUD_K3S_SKIP_IMAGES=true imports the
# images built before.
set -euo pipefail

# shellcheck source=deploy/k3s/lib.sh
source deploy/k3s/lib.sh

cluster=ogcloud-restore
# k3d's nodes and the backup target share it, as a cluster and an off-site bucket share a network.
network=ogcloud-restore
backups=ogcloud-restore-backups
bucket=ogcloud-backups
region=us-east-1
work=build/k3s
state="$PWD/$work/e2e"

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
    docker rm --force "$backups" >/dev/null 2>&1 || true
    docker network rm "$network" >/dev/null 2>&1 || true
  fi
  exit "$status"
}
trap finish EXIT

e2e() {
  mise x -- pnpm --filter @ogcloud/e2e "$@"
}

docker rm --force "$backups" >/dev/null 2>&1 || true
k3d cluster delete "$cluster" >/dev/null 2>&1 || true
docker network rm "$network" >/dev/null 2>&1 || true
docker network create "$network" >/dev/null

access_key=$(openssl rand -hex 12)
secret_key=$(openssl rand -hex 24)
# SeaweedFS reads its users from a mounted file; the drill's throwaway target has nothing else to protect.
printf '{"identities":[{"name":"backup","credentials":[{"accessKey":"%s","secretKey":"%s"}],"actions":["Read","Write","List","Tagging","Admin"]}]}\n' \
  "$access_key" "$secret_key" > "$work/backup-identities.json"
docker run --detach --name "$backups" --network "$network" \
  --volume "$PWD/$work/backup-identities.json:/etc/seaweedfs/s3.json:ro" \
  "$(sed -n 's/^seaweedfs=//p' testing/images.properties)" \
  server -dir=/data -s3 -s3.port=8333 -s3.config=/etc/seaweedfs/s3.json >/dev/null
deadline=$((SECONDS + 120))
until docker exec "$backups" sh -c "echo 's3.bucket.create -name $bucket' | weed shell" 2>/dev/null | grep -q "created bucket"; do
  if [ "$SECONDS" -ge "$deadline" ]; then
    echo "the backup target's SeaweedFS did not create bucket $bucket within 120s" >&2
    docker logs --tail 40 "$backups" >&2
    exit 1
  fi
  sleep 2
done
address=$(docker inspect --format "{{(index .NetworkSettings.Networks \"$network\").IPAddress}}" "$backups")
# Only these files hold secrets; a umask for the whole script would also reach the image build.
touch "$work/backup.yaml" "$work/backup-seed.yaml"
chmod 600 "$work/backup.yaml" "$work/backup-seed.yaml"
cat > "$work/backup.yaml" <<EOF
backup:
  enabled: true
  s3:
    endpoint: http://$address:8333
    region: $region
    bucket: $bucket
    cidrs: [$address/32]
    port: 8333
    pathStyle: true
EOF
# The target's keys come with the seed, never as values (D-0188).
cat > "$work/backup-seed.yaml" <<EOF
BACKUP_S3_ACCESS_KEY: $access_key
BACKUP_S3_SECRET_KEY: $secret_key
EOF
# The restored cluster archives under a name of its own, so it never writes over the backup it came from.
cat > "$work/restore.yaml" <<EOF
restore:
  fromServer: ogcloud-postgres
backup:
  serverName: ogcloud-postgres-restored
EOF

install() {
  OGCLOUD_TAG=$tag OGCLOUD_PUBLIC_ORIGIN=https://ogcloud.localhost:8443 OGCLOUD_PROXY_ONLINE_MODE=false \
    OGCLOUD_VALUES="$*" OGCLOUD_SEED_FILES="$PWD/$work/backup-seed.yaml" deploy/k3s/install.sh
  forward_ogcloud "$work"
}

echo "== The cluster that is lost"
k3d_cluster "$cluster" "$network"
k3d_images "$cluster"
install "$PWD/$work/backup.yaml"
rm -rf "$state"
mise run seed
e2e run restore snapshot

kubectl -n ogcloud wait --for=jsonpath='{.status.conditions[?(@.type=="ContinuousArchiving")].status}'=True \
  cluster.postgresql.cnpg.io/ogcloud-postgres --timeout=10m
kubectl -n ogcloud apply --filename - <<EOF
apiVersion: postgresql.cnpg.io/v1
kind: Backup
metadata:
  name: restore-drill
spec:
  cluster:
    name: ogcloud-postgres
  method: plugin
  pluginConfiguration:
    name: barman-cloud.cloudnative-pg.io
EOF
kubectl -n ogcloud create job --from=cronjob/ogcloud-backup-templates restore-drill-templates
kubectl -n ogcloud wait --for=jsonpath='{.status.phase}'=completed backup/restore-drill --timeout=15m
kubectl -n ogcloud wait --for=condition=Complete job/restore-drill-templates --timeout=15m
OGCLOUD_NAMESPACE=ogcloud deploy/backup/root-key.sh export "$work/root-key.yaml"

echo "== The restored cluster"
stop_forwards
k3d cluster delete "$cluster" >/dev/null
k3d_cluster "$cluster" "$network"
k3d_images "$cluster"
OGCLOUD_NAMESPACE=ogcloud deploy/backup/root-key.sh import "$work/root-key.yaml"
install "$PWD/$work/backup.yaml" "$PWD/$work/restore.yaml"
e2e run restore verify
e2e exec node --test src/scenarios/joins.test.ts
echo "The restore drill passed"
