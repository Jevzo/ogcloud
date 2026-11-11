# shellcheck shell=bash
# Sourced by install.sh: when a k3s HelmChart counts as installed. work names the directory with changed.txt (the
# charts whose documents changed) and jobs-before.txt (each chart job's uid before the apply).

# Done once the chart's job succeeded and its newest release is deployed, and for a changed chart only once that job
# is a new one. k3s may replace a job that succeeded, as when it retries a failed release as an upgrade, and the new
# one still runs the release's hooks.
installed() {
  local chart=$1 job prior
  : "${work:?install.sh sets work}"
  job=$(kubectl -n kube-system get job "helm-install-$chart" -o jsonpath='{.metadata.uid} {.status.succeeded}' 2>/dev/null) ||
    return 1
  [ "${job#* }" = 1 ] || return 1
  deployed "$chart" || return 1
  grep -qx "$chart" "$work/changed.txt" || return 0
  prior=$(sed -n "s/^helm-install-$chart //p" "$work/jobs-before.txt")
  [ "${job% *}" != "$prior" ]
}

# Whether the chart's newest Helm release is deployed, read from Helm's release Secrets in its target namespace.
deployed() {
  local chart=$1 namespace newest
  namespace=$(kubectl -n kube-system get helmchart "$chart" -o jsonpath='{.spec.targetNamespace}') || return 1
  newest=$(kubectl -n "${namespace:-kube-system}" get secrets -l "owner=helm,name=$chart" \
    -o jsonpath='{range .items[*]}{.metadata.labels.version} {.metadata.labels.status}{"\n"}{end}' | sort -n | tail -1)
  [ "${newest#* }" = deployed ]
}
