#!/usr/bin/env bash
# Installs or upgrades OgCloud from this checkout on a single-node k3s cluster that Terraform does not provision
# (D-0156, D-0157): every HelmChart applied at once, then it waits until everything converges. KUBECONFIG names the
# cluster, which already has the images tagged OGCLOUD_TAG; OGCLOUD_PUBLIC_ORIGIN sets the dashboard's origin, OGCLOUD_CLOUDFLARE_API_TOKEN gets its
# certificate from Let's Encrypt (D-0160), OGCLOUD_PROXY_ONLINE_MODE=false lets bots join private test clusters
# (D-0162), OGCLOUD_VALUES adds values files and OGCLOUD_SEED_FILES seed files (YAML maps of seed keys, D-0188), each
# separated by spaces.
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
cat > "$work/install.yaml" <<EOF
image:
  registry: ogcloud-local
  tag: $tag
  # Only the cluster's own image store has them.
  pullPolicy: Never
${OGCLOUD_PUBLIC_ORIGIN:+publicOrigin: $OGCLOUD_PUBLIC_ORIGIN}
minecraft:
  proxyOnlineMode: ${OGCLOUD_PROXY_ONLINE_MODE:-true}
EOF
# Infisical's first admin (D-0114), made up once per checkout; it reaches the cluster as the seed Secret, never as a
# value (D-0188), and bootstrap deletes that after the import.
if [ ! -f "$work/seed.yaml" ]; then
  printf 'INFISICAL_ADMIN_EMAIL: admin@ogcloud.localhost\nINFISICAL_ADMIN_PASSWORD: %s\n' "$(openssl rand -hex 24)" \
    > "$work/seed.yaml"
fi

values_files="\"$PWD/deploy/helm/ogcloud/ci/minikube.yaml\",\"$PWD/deploy/k3s/values.yaml\",\"$PWD/$work/install.yaml\""
seed_files="\"$PWD/$work/seed.yaml\""
rm -f "$work/letsencrypt.yaml" "$work/cloudflare-seed.yaml"
if [ -n "${OGCLOUD_CLOUDFLARE_API_TOKEN:-}" ]; then
  printf 'edge:\n  tls:\n    letsEncrypt:\n      enabled: true\n' > "$work/letsencrypt.yaml"
  printf 'CLOUDFLARE_API_TOKEN: %s\n' "$OGCLOUD_CLOUDFLARE_API_TOKEN" > "$work/cloudflare-seed.yaml"
  values_files="$values_files,\"$PWD/$work/letsencrypt.yaml\""
  seed_files="$seed_files,\"$PWD/$work/cloudflare-seed.yaml\""
fi
for extra in ${OGCLOUD_VALUES:-}; do
  values_files="$values_files,\"$extra\""
done
for extra in ${OGCLOUD_SEED_FILES:-}; do
  seed_files="$seed_files,\"$extra\""
done
terraform -chdir=deploy/terraform/render init -input=false >/dev/null
terraform -chdir=deploy/terraform/render apply -input=false -auto-approve -state="$PWD/$work/render.tfstate" \
  -var target=k3s -var "chart_archive=$PWD/$chart_archive" -var "values_files=[$values_files]" \
  -var "seed_files=[$seed_files]" >/dev/null
# The rendered manifest holds the token as well; the file of it alone is not needed after the render.
rm -f "$work/cloudflare-seed.yaml"
terraform -chdir=deploy/terraform/render output -state="$PWD/$work/render.tfstate" -raw manifest > "$work/manifest.yaml"

start=$(date +%s)
deadline=$((start + timeout_seconds))
elapsed() { echo "$(($(date +%s) - start))s"; }
remaining() { echo "$((deadline - $(date +%s)))s"; }

# On an upgrade a chart reruns only when its documents change, and its finished job would pass the wait: so the
# charts that change (their HelmChart, or OgCloud's values Secret) wait for the job that replaces theirs.
# A fixed differ, so its headers parse below whatever KUBECTL_EXTERNAL_DIFF the caller set. A first install has no
# finished jobs to tell apart, and the seed's namespace does not exist yet, which a dry run cannot get past.
set +e
if kubectl get namespace ogcloud >/dev/null 2>&1; then
  KUBECTL_EXTERNAL_DIFF="diff -u -N" kubectl diff --server-side -f "$work/manifest.yaml" > "$work/diff.txt" 2>&1
  status=$?
else
  : > "$work/diff.txt"
  status=0
fi
set -e
if [ "$status" -gt 1 ]; then
  cat "$work/diff.txt"
  echo "kubectl diff failed; on a cluster whose CRDs Helm created, see docs/runbooks/upgrades.md"
  exit 1
fi
if [ "$status" -eq 1 ] && ! grep -q '^diff -u -N ' "$work/diff.txt"; then
  echo "kubectl diff reported changes in a form this script cannot read, so it would not know which charts to wait for"
  exit 1
fi
sed -n -e 's|^diff .*/helm\.cattle\.io\.v1\.HelmChart\.kube-system\.\([^ /]*\)$|\1|p' \
  -e 's|^diff .*/v1\.Secret\.kube-system\.ogcloud-values$|ogcloud|p' "$work/diff.txt" | sort -u > "$work/changed.txt"
kubectl -n kube-system get jobs -o go-template='{{range .items}}{{.metadata.name}} {{.metadata.uid}}{{"\n"}}{{end}}' \
  > "$work/jobs-before.txt"

# Everything at once, as kube-hetzner applies its kustomizations; server-side, since the CRDs outgrow the annotation
# client-side apply keeps.
kubectl apply --server-side -f "$work/manifest.yaml" >/dev/null

# Done once the chart's job succeeded, and for a changed chart only once that job is a new one.
installed() {
  local chart=$1 job prior
  job=$(kubectl -n kube-system get job "helm-install-$chart" -o jsonpath='{.metadata.uid} {.status.succeeded}' 2>/dev/null) ||
    return 1
  [ "${job#* }" = 1 ] || return 1
  grep -qx "$chart" "$work/changed.txt" || return 0
  prior=$(sed -n "s/^helm-install-$chart //p" "$work/jobs-before.txt")
  [ "${job% *}" != "$prior" ]
}

for chart in $(kubectl -n kube-system get helmcharts -o jsonpath='{.items[*].metadata.name}'); do
  until installed "$chart"; do
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

# Ready only once its operator has seen the current spec: after an upgrade the condition is still the old one's.
ready() {
  local resource=$1 generation
  generation=$(kubectl -n ogcloud get "$resource" -o jsonpath='{.metadata.generation}')
  if [ -n "$(kubectl -n ogcloud get "$resource" -o jsonpath='{.status.observedGeneration}')" ]; then
    kubectl -n ogcloud wait --for=jsonpath='{.status.observedGeneration}'="$generation" "$resource" --timeout="$(remaining)"
  fi
  kubectl -n ogcloud wait --for=condition=Ready "$resource" --timeout="$(remaining)"
}
ready kafka/ogcloud
ready cluster.postgresql.cnpg.io/ogcloud-postgres
# Rollouts, not Available, which the replaced pods still report during an upgrade.
for workload in $(kubectl -n ogcloud get deployments,statefulsets -o name); do
  kubectl -n ogcloud rollout status "$workload" --timeout="$(remaining)"
done
kubectl -n ogcloud wait --for=condition=Ready pod --all --field-selector=status.phase!=Succeeded --timeout="$(remaining)"
echo "OgCloud converged on k3s after $(elapsed)"
