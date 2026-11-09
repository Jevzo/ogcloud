#!/usr/bin/env bash
# Infisical's root key and bootstrap's state, which nothing in a cluster can recreate (D-0166, D-0167): the root
# Secrets (Infisical's encryption key and auth secret, its database and Valkey credentials), the identities, the
# state ConfigMap and the break-glass login that re-points Infisical at a new cluster (D-0173), all labelled by
# bootstrap, plus the backup target's credentials, which PostgreSQL's recovery reads before Infisical runs (D-0168).
# `export <file>` writes them for the operator's password manager; `import <file>` applies them to a fresh cluster
# before a restore's install. OGCLOUD_NAMESPACE names the release's.
set -euo pipefail

usage="usage: root-key.sh <export|import> <file>"
action=${1:?$usage}
file=${2:?$usage}
namespace=${OGCLOUD_NAMESPACE:-ogcloud}
selector=app.kubernetes.io/managed-by=ogcloud-bootstrap
backup_credentials=ogcloud-backup-s3

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
    kubectl --namespace "$namespace" get secrets --field-selector "metadata.name=$backup_credentials" \
      --output go-template="$template" >> "$file"
    if ! grep -q "^  name: $backup_credentials\$" "$file"; then
      echo "warning: no $backup_credentials in $namespace; a restore will need it (backup.enabled)" >&2
    fi
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
