{{/*
Every secret OgCloud keeps in Infisical (D-0044), defined once: bootstrap generates them (D-0114) and the
Secrets Operator copies them into the Secrets each workload mounts. Keys are unique across folders, so one
recursive source per project serves every target. The forwarding tokens have a project of their own (D-0152).
*/}}
{{- define "ogcloud.generatedSecrets" -}}
- {path: /, name: API_SETUP_TOKEN, generate: base64url, bytes: 32}
- {path: /, name: API_PSEUDONYM_KEY, generate: base64, bytes: 32}
- {path: /, name: API_TOTP_KEY, generate: base64, bytes: 32}
- {path: /, name: API_SIGNING_KEY, generate: rsa, bits: 3072}
{{- range $service := list "api" "controller" }}
- {path: /, name: POSTGRES_{{ upper $service }}_PASSWORD, generate: alnum, length: 40}
{{- end }}
{{- range $service := include "ogcloud.dependencyUsers" . | fromYamlArray }}
- {path: /, name: VALKEY_{{ upper $service }}_PASSWORD, generate: alnum, length: 40}
- {path: /, name: KAFKA_{{ upper $service }}_PASSWORD, generate: alnum, length: 40}
{{- end }}
- {path: /, name: S3_ACCESS_KEY, generate: alnum, length: 20}
- {path: /, name: S3_SECRET_KEY, generate: alnum, length: 40}
{{- if include "ogcloud.grafanaSso" . }}
- {path: /, name: GRAFANA_CLIENT_SECRET, generate: alnum, length: 48}
{{- end }}
- {project: {{ .Values.infisical.forwardingProject }}, path: /, name: TOKENS, generate: alnum, length: 48}
{{- end -}}

{{/* Services with their own Valkey and Kafka users; names match the generated KafkaUsers (ogcloud-<service>). */}}
{{- define "ogcloud.dependencyUsers" -}}
- api
- controller
- gateway
- loadbalancer
{{- end -}}

{{/*
What each Secret receives, by target name: Secret key -> Infisical key. Spring services read theirs as a
config tree, so their keys are property names.
*/}}
{{- define "ogcloud.syncedSecrets" -}}
ogcloud-api-config:
  namespace: {{ .Release.Namespace }}
  keys:
    {{- if include "ogcloud.grafanaSso" . }}
    ogcloud.auth.clients.grafana.client-secret: GRAFANA_CLIENT_SECRET
    {{- end }}
    ogcloud.auth.setup-token: API_SETUP_TOKEN
    ogcloud.privacy.pseudonym-key: API_PSEUDONYM_KEY
    ogcloud.auth.totp.keys.k1: API_TOTP_KEY
    spring.datasource.password: POSTGRES_API_PASSWORD
    ogcloud.valkey.password: VALKEY_API_PASSWORD
    ogcloud.kafka.scram.password: KAFKA_API_PASSWORD
    ogcloud.s3.access-key: S3_ACCESS_KEY
    ogcloud.s3.secret-key: S3_SECRET_KEY
ogcloud-api-signing:
  namespace: {{ .Release.Namespace }}
  keys:
    k1.pem: API_SIGNING_KEY
ogcloud-controller-config:
  namespace: {{ .Release.Namespace }}
  keys:
    spring.datasource.password: POSTGRES_CONTROLLER_PASSWORD
    ogcloud.valkey.password: VALKEY_CONTROLLER_PASSWORD
    ogcloud.kafka.scram.password: KAFKA_CONTROLLER_PASSWORD
    ogcloud.s3.access-key: S3_ACCESS_KEY
    ogcloud.s3.secret-key: S3_SECRET_KEY
ogcloud-gateway-secrets:
  namespace: {{ .Release.Namespace }}
  keys:
    valkey-password: VALKEY_GATEWAY_PASSWORD
    kafka-password: KAFKA_GATEWAY_PASSWORD
    s3-access-key: S3_ACCESS_KEY
    s3-secret-key: S3_SECRET_KEY
ogcloud-loadbalancer-secrets:
  namespace: {{ .Release.Namespace }}
  keys:
    valkey-password: VALKEY_LOADBALANCER_PASSWORD
    kafka-password: KAFKA_LOADBALANCER_PASSWORD
{{- range $service := include "ogcloud.dependencyUsers" . | fromYamlArray }}
ogcloud-kafka-{{ $service }}:
  namespace: {{ $.Release.Namespace }}
  keys:
    password: KAFKA_{{ upper $service }}_PASSWORD
{{- end }}
{{- range $service := list "api" "controller" }}
ogcloud-postgres-{{ $service }}:
  namespace: {{ $.Release.Namespace }}
  type: kubernetes.io/basic-auth
  # CloudNativePG retries a managed role only when its labelled Secret appears or changes.
  labels:
    cnpg.io/reload: "true"
  literals:
    username: ogcloud_{{ $service }}
  keys:
    password: POSTGRES_{{ upper $service }}_PASSWORD
{{- end }}
ogcloud-forwarding:
  namespace: {{ include "ogcloud.serversNamespace" . }}
  project: {{ .Values.infisical.forwardingProject }}
  keys:
    tokens: TOKENS
{{- if .Values.valkey.inCluster }}
ogcloud-valkey-users:
  namespace: {{ .Release.Namespace }}
  templates:
    users.acl: |
      {{- range $service := include "ogcloud.dependencyUsers" . | fromYamlArray }}
      user ogcloud-{{ $service }} on >{{ printf "{{ .VALKEY_%s_PASSWORD.Value }}" (upper $service) }} {{ include "ogcloud.valkeyRules" $service }}
      {{- end }}
{{- end }}
{{- if include "ogcloud.grafanaSso" . }}
{{- /* Grafana's sign-in through OgCloud (D-0153), as the environment its chart loads (deploy/platform-deps.yaml). */}}
{{- $origin := .Values.publicOrigin }}
ogcloud-grafana-oauth:
  namespace: {{ .Values.networkPolicies.namespaces.monitoring }}
  literals:
    GF_SERVER_ROOT_URL: {{ include "ogcloud.grafanaUrl" . }}
    GF_AUTH_GENERIC_OAUTH_ENABLED: "true"
    GF_AUTH_GENERIC_OAUTH_NAME: OgCloud
    GF_AUTH_GENERIC_OAUTH_CLIENT_ID: ogcloud-grafana
    GF_AUTH_GENERIC_OAUTH_SCOPES: openid profile email
    GF_AUTH_GENERIC_OAUTH_AUTH_URL: {{ $origin }}/oauth2/authorize
    GF_AUTH_GENERIC_OAUTH_TOKEN_URL: {{ $origin }}/oauth2/token
    GF_AUTH_GENERIC_OAUTH_API_URL: {{ $origin }}/oauth2/userinfo
    GF_AUTH_GENERIC_OAUTH_USE_PKCE: "true"
    GF_AUTH_GENERIC_OAUTH_USE_REFRESH_TOKEN: "true"
    GF_AUTH_GENERIC_OAUTH_ALLOW_SIGN_UP: "true"
    GF_AUTH_GENERIC_OAUTH_LOGIN_ATTRIBUTE_PATH: preferred_username
    GF_AUTH_GENERIC_OAUTH_EMAIL_ATTRIBUTE_PATH: email
    # Owners and admins administer Grafana, overview:read lets one look; everyone else is refused.
    GF_AUTH_GENERIC_OAUTH_ROLE_ATTRIBUTE_PATH: "contains(['owner', 'admin'], role) && 'Admin' || contains(permissions, 'overview:read') && 'Viewer' || ''"
    GF_AUTH_GENERIC_OAUTH_ROLE_ATTRIBUTE_STRICT: "true"
  keys:
    GF_AUTH_GENERIC_OAUTH_CLIENT_SECRET: GRAFANA_CLIENT_SECRET
{{- end }}
{{- if .Values.backup.enabled }}
{{- /* The backup target's credentials (D-0166), imported from the seed; the region is the plugin's to read too. */}}
ogcloud-backup-s3:
  namespace: {{ .Release.Namespace }}
  literals:
    REGION: {{ .Values.backup.s3.region | quote }}
  keys:
    ACCESS_KEY_ID: BACKUP_S3_ACCESS_KEY
    ACCESS_SECRET_KEY: BACKUP_S3_SECRET_KEY
{{- end }}
{{- if .Values.edge.tls.letsEncrypt.enabled }}
{{- /* Let's Encrypt's DNS-01 token (D-0160), imported from the seed; a ClusterIssuer reads cert-manager's namespace. */}}
ogcloud-cloudflare-dns:
  namespace: {{ .Values.certManager.namespace }}
  keys:
    api-token: CLOUDFLARE_API_TOKEN
{{- end }}
{{- if .Values.s3.inCluster }}
{{- /* SeaweedFS's S3 users: OgCloud's identity may use its bucket and nothing else. */}}
ogcloud-seaweedfs-s3:
  namespace: {{ .Release.Namespace }}
  templates:
    seaweedfs_s3_config: |
      {{- $bucket := .Values.s3.bucket }}
      {{- $identity := dict "name" "ogcloud" "credentials" (list (dict "accessKey" "{{ .S3_ACCESS_KEY.Value }}" "secretKey" "{{ .S3_SECRET_KEY.Value }}")) "actions" (list (printf "Read:%s" $bucket) (printf "Write:%s" $bucket) (printf "List:%s" $bucket) (printf "Tagging:%s" $bucket)) }}
      {{ dict "identities" (list $identity) | toJson }}
{{- end }}
{{- end -}}

{{/*
What each service may do in Valkey: the API and the controller write but never administer, the gateway and the
load balancer only read (D-0109). Sentinel clients check the primary they find with ROLE.
*/}}
{{- define "ogcloud.valkeyRules" -}}
{{- if has . (list "gateway" "loadbalancer") -}}
~* &* -@all +@read +@connection +info +role
{{- else -}}
~* &* +@all -flushall -flushdb -debug -config -shutdown -acl -monitor -replicaof -slaveof -failover -save -bgsave -bgrewriteaof -sync -psync
{{- end -}}
{{- end -}}
