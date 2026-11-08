{{- define "ogcloud.bootstrapConfig" -}}
namespace: {{ .Release.Namespace }}
# Infisical's own root (D-0044): never in Infisical itself, kept on uninstall.
rootSecrets:
  - name: ogcloud-infisical-root
    keys:
      ENCRYPTION_KEY: {generate: hex, bytes: 16}
      AUTH_SECRET: {generate: base64, bytes: 32}
      DB_PASSWORD: {generate: alnum, length: 40}
      VALKEY_PASSWORD: {generate: alnum, length: 40}
infisical:
  url: {{ .Values.infisical.url }}
  organization: {{ .Values.infisical.organization }}
  project: {{ .Values.infisical.project }}
  environment: {{ .Values.infisical.environment }}
  stateConfigMap: ogcloud-infisical-state
  identitiesSecret: ogcloud-infisical-identities
  serviceAccountTokenFile: /var/run/secrets/ogcloud/bootstrap/token
  kubernetes:
    host: {{ .Values.infisical.kubernetesHost }}
    caFile: /var/run/secrets/ogcloud/bootstrap/ca.crt
    reviewerTokenFile: /var/run/secrets/ogcloud/reviewer/token
  bootstrapIdentity:
    name: ogcloud-bootstrap
    serviceAccounts: [{{ .Release.Namespace }}/ogcloud-bootstrap]
  identities:
    - name: ogcloud-secrets-sync
      serviceAccounts: [{{ .Release.Namespace }}/ogcloud-secrets-sync]
      role: viewer
  secrets:
    {{- include "ogcloud.generatedSecrets" . | nindent 4 }}
  seed:
    secret: ogcloud-seed
    path: /seed
    adminEmailKey: INFISICAL_ADMIN_EMAIL
    adminPasswordKey: INFISICAL_ADMIN_PASSWORD
{{- end -}}
