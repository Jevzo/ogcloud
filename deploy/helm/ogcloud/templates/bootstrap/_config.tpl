{{- define "ogcloud.bootstrapConfig" -}}
namespace: {{ .Release.Namespace }}
# Infisical's own root (D-0044): never in Infisical itself, kept on uninstall.
rootSecrets:
  - name: ogcloud-infisical-root
    keys:
      ENCRYPTION_KEY: {generate: hex, bytes: 16}
      AUTH_SECRET: {generate: base64, bytes: 32}
  # Infisical's own Valkey (D-0151): its admin and Infisical's user.
  - name: ogcloud-infisical-valkey-root
    keys:
      ADMIN_PASSWORD: {generate: alnum, length: 40}
      INFISICAL_PASSWORD: {generate: alnum, length: 40}
  {{- if .Values.valkey.inCluster }}
  # The app Valkey's own users, which exist before Infisical can sync the app users.
  - name: ogcloud-valkey-root
    keys:
      ADMIN_PASSWORD: {generate: alnum, length: 40}
      REPLICATION_PASSWORD: {generate: alnum, length: 40}
      SENTINEL_PASSWORD: {generate: alnum, length: 40}
  {{- end }}
  # Infisical's database owner, which CloudNativePG's initdb creates before Infisical can sync anything.
  - name: ogcloud-postgres-infisical
    type: kubernetes.io/basic-auth
    keys:
      username: {value: infisical}
      password: {generate: alnum, length: 40}
infisical:
  url: {{ include "ogcloud.infisicalUrl" . }}
  caFile: /etc/ogcloud/ca/ca.crt
  organization: {{ .Values.infisical.organization }}
  project: {{ .Values.infisical.project }}
  projects: [{{ .Values.infisical.forwardingProject }}]
  environment: {{ .Values.infisical.environment }}
  stateConfigMap: ogcloud-infisical-state
  identitiesSecret: ogcloud-infisical-identities
  serviceAccountTokenFile: /var/run/secrets/ogcloud/bootstrap/token
  # Bootstrap's Universal Auth credential for after a restore, exported with the root key (D-0173).
  breakGlassSecret: ogcloud-infisical-break-glass
  # Every identity gets this cluster's API CA on every run (D-0173); only the controller's audience-bound tokens need
  # the reviewer's (D-0167).
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
      roles:
        {{ .Values.infisical.project }}: viewer
        {{ .Values.infisical.forwardingProject }}: viewer
    # Rotates the forwarding tokens (D-0078) and sees nothing else (D-0152).
    - name: ogcloud-controller
      serviceAccounts: [{{ .Release.Namespace }}/ogcloud-controller]
      roles:
        {{ .Values.infisical.forwardingProject }}: member
      audience: {{ include "ogcloud.infisicalAudience" . }}
  secrets:
    {{- include "ogcloud.generatedSecrets" . | nindent 4 }}
  seed:
    secret: ogcloud-seed
    path: /seed
    adminEmailKey: INFISICAL_ADMIN_EMAIL
    adminPasswordKey: INFISICAL_ADMIN_PASSWORD
    # What only the seed brings and this install syncs; bootstrap fails while Infisical lacks one.
    requiredKeys:
      {{- if .Values.edge.tls.letsEncrypt.enabled }}
      - CLOUDFLARE_API_TOKEN
      {{- end }}
      {{- if .Values.backup.enabled }}
      - BACKUP_S3_ACCESS_KEY
      - BACKUP_S3_SECRET_KEY
      {{- end }}
{{- end -}}
