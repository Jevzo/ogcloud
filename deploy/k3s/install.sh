#!/usr/bin/env bash
# Installs OgCloud from this checkout on a single-node k3s cluster that Terraform does not provision (D-0156, D-0157):
# every HelmChart applied at once, then it waits until everything converges. KUBECONFIG names the cluster, which
# holds no OgCloud yet (an earlier install's finished jobs would pass the wait) and already has the images tagged
# OGCLOUD_TAG; OGCLOUD_PUBLIC_ORIGIN sets the dashboard's origin, and OGCLOUD_CLOUDFLARE_API_TOKEN gets its
# certificate from Let's Encrypt (D-0160).
set -euo pipefail

tag=${OGCLOUD_TAG:?OGCLOUD_TAG names the images to install}
work=build/k3s
timeout_seconds=2700

mkdir -p "$work"
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
diagnose_on_failure() {
  local status=$?
  if [ "$status" -ne 0 ]; then
    diagnose
  fi
  exit "$status"
}
trap diagnose_on_failure EXIT

rm -f "$work"/ogcloud-*.tgz
helm package deploy/helm/ogcloud --destination "$work" >/dev/null
chart_archive=$(ls "$work"/ogcloud-*.tgz)

umask 077
cat > "$work/images.yaml" <<EOF
image:
  registry: ogcloud-local
  tag: $tag
  # Only the cluster's own image store has them.
  pullPolicy: Never
${OGCLOUD_PUBLIC_ORIGIN:+publicOrigin: $OGCLOUD_PUBLIC_ORIGIN}
EOF
# Infisical's first admin (D-0114), made up once per checkout; bootstrap deletes the seed after use.
if [ ! -f "$work/seed.yaml" ]; then
  printf 'bootstrap:\n  seed:\n    INFISICAL_ADMIN_EMAIL: admin@ogcloud.localhost\n    INFISICAL_ADMIN_PASSWORD: %s\n' \
    "$(openssl rand -hex 24)" > "$work/seed.yaml"
fi

values_files="\"$PWD/deploy/helm/ogcloud/ci/minikube.yaml\",\"$PWD/deploy/k3s/values.yaml\",\"$PWD/$work/images.yaml\",\"$PWD/$work/seed.yaml\""
if [ -n "${OGCLOUD_CLOUDFLARE_API_TOKEN:-}" ]; then
  printf 'edge:\n  tls:\n    letsEncrypt:\n      enabled: true\nbootstrap:\n  seed:\n    CLOUDFLARE_API_TOKEN: %s\n' \
    "$OGCLOUD_CLOUDFLARE_API_TOKEN" > "$work/letsencrypt.yaml"
  values_files="$values_files,\"$PWD/$work/letsencrypt.yaml\""
fi
terraform -chdir=deploy/terraform/render init -input=false >/dev/null
terraform -chdir=deploy/terraform/render apply -input=false -auto-approve -state="$PWD/$work/render.tfstate" \
  -var target=k3s -var "chart_archive=$PWD/$chart_archive" -var "values_files=[$values_files]" >/dev/null
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
