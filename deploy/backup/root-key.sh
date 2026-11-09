#!/usr/bin/env bash
# Infisical's root key and bootstrap's state, which nothing in a cluster can recreate (D-0166, D-0167): the root
# Secrets (Infisical's encryption key and auth secret, its database and Valkey credentials), the identities and the
# state ConfigMap, all labelled by bootstrap. `export <file>` writes them for the operator's password manager;
# `import <file>` applies them to a fresh cluster before a restore's install. OGCLOUD_NAMESPACE names the release's.
set -euo pipefail

usage="usage: root-key.sh <export|import> <file>"
action=${1:?$usage}
file=${2:?$usage}
namespace=${OGCLOUD_NAMESPACE:-ogcloud}
selector=app.kubernetes.io/managed-by=ogcloud-bootstrap

# Only what a restore needs: no uid, resourceVersion or timestamps of the old cluster.
# shellcheck disable=SC2016 # A kubectl go-template: its $ are not the shell's.
template='{{range .items}}---
apiVersion: {{.apiVersion}}
kind: {{.kind}}
metadata:
  name: {{.metadata.name}}
  labels:
{{- range $key, $value := .metadata.labels}}
    {{$key}}: {{printf "%q" $value}}
{{- end}}
{{- with .metadata.annotations}}
  annotations:
{{- range $key, $value := .}}
    {{$key}}: {{printf "%q" $value}}
{{- end}}
{{- end}}
{{- with .type}}
type: {{.}}
{{- end}}
data:
{{- range $key, $value := .data}}
  {{printf "%q" $key}}: {{printf "%q" $value}}
{{- end}}
{{end}}'

case "$action" in
  export)
    umask 077
    mkdir -p "$(dirname "$file")"
    kubectl --namespace "$namespace" get secrets,configmaps --selector "$selector" --output go-template="$template" > "$file"
    count=$(grep -c '^kind:' "$file" || true)
    if [ "$count" -eq 0 ]; then
      echo "nothing labelled $selector in $namespace" >&2
      exit 1
    fi
    echo "Exported $count objects to $file; keep it with the cluster's other credentials."
    ;;
  import)
    kubectl create namespace "$namespace" --dry-run=client --output yaml | kubectl apply --filename - >/dev/null
    kubectl --namespace "$namespace" apply --filename "$file"
    ;;
  *)
    echo "$usage" >&2
    exit 2
    ;;
esac
