#!/usr/bin/env bash
# The k3s ordering test (T-0605, D-0159): a fresh k3d cluster at the k3s line kube-hetzner installs gets every
# HelmChart at once, rendered by the same Terraform module as Hetzner (D-0156), and must converge with no ordering.
# OGCLOUD_K3S_KEEP=true keeps the cluster afterwards; OGCLOUD_K3S_SKIP_IMAGES=true imports the images built before.
set -euo pipefail

cluster=ogcloud-k3s
work=build/k3s
registry=ogcloud-local
tag=k3s
timeout_seconds=2700

mkdir -p "$work"
export KUBECONFIG="$PWD/$work/kubeconfig"
export TF_DATA_DIR="$PWD/$work/terraform"

diagnose() {
  echo "== HelmCharts and their jobs"
  kubectl -n kube-system get helmcharts,jobs || true
  echo "== Pods that are not running or done"
  kubectl get pods -A --field-selector=status.phase!=Running,status.phase!=Succeeded || true
  for job in $(kubectl -n kube-system get jobs -o name 2>/dev/null | grep helm-install); do
    if [ "$(kubectl -n kube-system get "$job" -o jsonpath='{.status.succeeded}')" != 1 ]; then
      echo "== $job"
      kubectl -n kube-system logs "$job" --tail=40 || true
    fi
  done
}

finish() {
  local status=$?
  if [ "$status" -ne 0 ]; then
    diagnose
  fi
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

rm -f "$work"/ogcloud-*.tgz
helm package deploy/helm/ogcloud --destination "$work" >/dev/null
chart_archive=$(ls "$work"/ogcloud-*.tgz)

# Infisical's first admin (D-0114), made up for this cluster only.
umask 077
cat > "$work/values.yaml" <<EOF
image:
  registry: $registry
  tag: $tag
  pullPolicy: Never
bootstrap:
  seed:
    INFISICAL_ADMIN_EMAIL: admin@ogcloud.localhost
    INFISICAL_ADMIN_PASSWORD: $(openssl rand -hex 24)
EOF

terraform -chdir=deploy/terraform/render init -input=false >/dev/null
terraform -chdir=deploy/terraform/render apply -input=false -auto-approve -state="$PWD/$work/render.tfstate" \
  -var target=k3s -var "chart_archive=$PWD/$chart_archive" \
  -var "values_files=[\"$PWD/deploy/helm/ogcloud/ci/minikube.yaml\",\"$PWD/deploy/k3s/values.yaml\",\"$PWD/$work/values.yaml\"]" \
  >/dev/null
terraform -chdir=deploy/terraform/render output -state="$PWD/$work/render.tfstate" -raw manifest > "$work/manifest.yaml"

start=$(date +%s)
deadline=$((start + timeout_seconds))
elapsed() { echo "$(($(date +%s) - start))s"; }
remaining() { echo "$((deadline - $(date +%s)))s"; }

# Everything at once, as kube-hetzner applies its kustomizations; server-side, since the CRDs outgrow the annotation
# client-side apply keeps.
kubectl apply --server-side -f "$work/manifest.yaml" >/dev/null

for chart in $(kubectl -n kube-system get helmcharts -o jsonpath='{.items[*].metadata.name}'); do
  until [ "$(kubectl -n kube-system get job "helm-install-$chart" -o jsonpath='{.status.succeeded}' 2>/dev/null)" = 1 ]; do
    if [ "$(kubectl -n kube-system get helmchart "$chart" -o jsonpath='{.status.conditions[?(@.type=="Failed")].status}')" = True ]; then
      echo "HelmChart $chart failed after $(elapsed)"
      exit 1
    fi
    if [ "$(date +%s)" -ge "$deadline" ]; then
      echo "HelmChart $chart was not installed within ${timeout_seconds}s"
      exit 1
    fi
    sleep 10
  done
  echo "HelmChart $chart installed after $(elapsed)"
done

kubectl -n ogcloud wait --for=condition=Ready kafka/ogcloud --timeout="$(remaining)"
kubectl -n ogcloud wait --for=condition=Ready cluster.postgresql.cnpg.io/ogcloud-postgres --timeout="$(remaining)"
kubectl -n ogcloud wait --for=condition=Available deployment --all --timeout="$(remaining)"
for statefulset in $(kubectl -n ogcloud get statefulsets -o name); do
  kubectl -n ogcloud rollout status "$statefulset" --timeout="$(remaining)"
done
kubectl -n ogcloud wait --for=condition=Ready pod --all --field-selector=status.phase!=Succeeded --timeout="$(remaining)"
echo "OgCloud converged on k3s after $(elapsed)"
