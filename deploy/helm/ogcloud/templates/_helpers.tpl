{{/* Labels every object carries; components add app.kubernetes.io/component. */}}
{{- define "ogcloud.labels" -}}
app.kubernetes.io/name: ogcloud
app.kubernetes.io/instance: {{ .Release.Name }}
app.kubernetes.io/version: {{ .Chart.AppVersion | quote }}
app.kubernetes.io/managed-by: {{ .Release.Service }}
helm.sh/chart: {{ printf "%s-%s" .Chart.Name .Chart.Version | replace "+" "_" }}
{{- end -}}

{{/* Labels selecting one component's pods: (dict "root" $ "component" "api"). */}}
{{- define "ogcloud.selector" -}}
app.kubernetes.io/name: ogcloud
app.kubernetes.io/instance: {{ .root.Release.Name }}
app.kubernetes.io/component: {{ .component }}
{{- end -}}

{{- define "ogcloud.componentLabels" -}}
{{ include "ogcloud.labels" .root }}
app.kubernetes.io/component: {{ .component }}
{{- end -}}

{{/* An OgCloud image (D-0128): (dict "root" $ "name" "api"); the tag defaults to the platform version. */}}
{{- define "ogcloud.image" -}}
{{- printf "%s/%s:%s" .root.Values.image.registry .name (.root.Values.image.tag | default .root.Chart.AppVersion) -}}
{{- end -}}

{{/* The game namespace (D-0056). */}}
{{- define "ogcloud.serversNamespace" -}}
{{- .Values.namespaces.servers -}}
{{- end -}}

{{/* Pods run as uid 1000 with the restricted profile (D-0128, T-0603). */}}
{{- define "ogcloud.podSecurity" -}}
runAsNonRoot: true
runAsUser: 1000
runAsGroup: 1000
fsGroup: 1000
seccompProfile:
  type: RuntimeDefault
{{- end -}}

{{- define "ogcloud.containerSecurity" -}}
allowPrivilegeEscalation: false
readOnlyRootFilesystem: true
capabilities:
  drop: [ALL]
{{- end -}}

{{/*
A projected service-account token with the cluster CA, in place of the automounted one; a non-empty audience
binds the token to that service (D-0013).
*/}}
{{- define "ogcloud.apiServerAccess" -}}
projected:
  sources:
    - serviceAccountToken:
        path: token
        expirationSeconds: 3600
        {{- with .audience }}
        audience: {{ . }}
        {{- end }}
    - configMap:
        name: kube-root-ca.crt
        items:
          - key: ca.crt
            path: ca.crt
{{- end -}}

{{/* JDBC URL of the database, verified against the internal CA (D-0041). */}}
{{- define "ogcloud.jdbcUrl" -}}
{{- printf "jdbc:postgresql://%s:%v/%s?sslmode=verify-full&sslrootcert=/etc/ogcloud/ca/ca.crt" .Values.postgres.host .Values.postgres.port .Values.postgres.database -}}
{{- end -}}

{{/* host:port of the in-cluster Valkey or its Sentinels, or the external ones (D-0048). */}}
{{- define "ogcloud.valkeyAddresses" -}}
{{- $v := .Values.valkey -}}
{{- if and $v.inCluster $v.sentinel -}}
{{- range $i := until 3 }}
- ogcloud-valkey-{{ $i }}.ogcloud-valkey-headless.{{ $.Release.Namespace }}.svc:26379
{{- end }}
{{- else if $v.inCluster -}}
- ogcloud-valkey.{{ .Release.Namespace }}.svc:6379
{{- else -}}
{{ required "valkey.addresses is required for an external Valkey" $v.addresses | toYaml }}
{{- end -}}
{{- end -}}

{{/* The S3 API: SeaweedFS's Service, by a name that game pods resolve for presigned URLs too, or the external one. */}}
{{- define "ogcloud.s3Endpoint" -}}
{{- if .Values.s3.inCluster -}}
http://{{ include "ogcloud.seaweedfsService" . }}:8333
{{- else -}}
{{ required "s3.endpoint is required for an external S3" .Values.s3.endpoint }}
{{- end -}}
{{- end -}}

{{- define "ogcloud.seaweedfsService" -}}
{{ .Values.seaweedfs.fullnameOverride }}-all-in-one.{{ .Release.Namespace }}.svc
{{- end -}}

{{- define "ogcloud.valkeySentinelMaster" -}}
{{- if and .Values.valkey.inCluster .Values.valkey.sentinel -}}ogcloud{{- else -}}{{ .Values.valkey.sentinelMaster }}{{- end -}}
{{- end -}}

{{/* Spring settings both Kotlin services share, for one dependency user: (dict "root" $ "user" "api"). */}}
{{- define "ogcloud.springDependencies" -}}
{{- $v := .root.Values -}}
{{- $addresses := include "ogcloud.valkeyAddresses" .root | fromYamlArray -}}
{{- $master := include "ogcloud.valkeySentinelMaster" .root -}}
valkey:
  {{- if $master }}
  sentinel:
    master: {{ $master }}
    nodes: {{ toJson $addresses }}
  {{- else }}
  host: {{ (splitList ":" (first $addresses)) | first }}
  port: {{ (splitList ":" (first $addresses)) | last }}
  {{- end }}
  username: ogcloud-{{ .user }}
  tls:
    ca-certificate-path: /etc/ogcloud/ca/ca.crt
kafka:
  bootstrap-servers: {{ $v.kafka.bootstrapServers }}
  scram:
    username: ogcloud-{{ .user }}
  tls:
    ca-certificate-path: /etc/ogcloud/ca/ca.crt
s3:
  endpoint: {{ include "ogcloud.s3Endpoint" .root }}
  region: {{ $v.s3.region }}
  bucket: {{ $v.s3.bucket }}
  path-style: {{ $v.s3.pathStyle }}
{{- end -}}

{{/* Environment of the Go services for one dependency user: (dict "root" $ "user" "gateway" "s3" true). */}}
{{- define "ogcloud.goDependencies" -}}
{{- $v := .root.Values -}}
- name: OGCLOUD_TLS_CA_FILE
  value: /etc/ogcloud/ca/ca.crt
- name: OGCLOUD_KAFKA_BROKERS
  value: {{ $v.kafka.bootstrapServers | quote }}
- name: OGCLOUD_KAFKA_USERNAME
  value: ogcloud-{{ .user }}
- name: OGCLOUD_KAFKA_PASSWORD_FILE
  value: /var/run/secrets/ogcloud/secrets/kafka-password
- name: OGCLOUD_VALKEY_ADDRESSES
  value: {{ include "ogcloud.valkeyAddresses" .root | fromYamlArray | join "," | quote }}
- name: OGCLOUD_VALKEY_SENTINEL_MASTER
  value: {{ include "ogcloud.valkeySentinelMaster" .root | quote }}
- name: OGCLOUD_VALKEY_USERNAME
  value: ogcloud-{{ .user }}
- name: OGCLOUD_VALKEY_PASSWORD_FILE
  value: /var/run/secrets/ogcloud/secrets/valkey-password
{{- if .s3 }}
- name: OGCLOUD_S3_ENDPOINT
  value: {{ include "ogcloud.s3Endpoint" .root | quote }}
- name: OGCLOUD_S3_REGION
  value: {{ $v.s3.region | quote }}
- name: OGCLOUD_S3_BUCKET
  value: {{ $v.s3.bucket | quote }}
- name: OGCLOUD_S3_PATH_STYLE
  value: {{ $v.s3.pathStyle | quote }}
- name: OGCLOUD_S3_ACCESS_KEY_FILE
  value: /var/run/secrets/ogcloud/secrets/s3-access-key
- name: OGCLOUD_S3_SECRET_KEY_FILE
  value: /var/run/secrets/ogcloud/secrets/s3-secret-key
{{- end }}
{{- end -}}

{{/* Probes of the Spring services' actuator; the JVM gets 150 s to start. */}}
{{- define "ogcloud.springProbes" -}}
startupProbe:
  httpGet:
    path: /actuator/health/liveness
    port: http
  periodSeconds: 5
  failureThreshold: 30
livenessProbe:
  httpGet:
    path: /actuator/health/liveness
    port: http
  periodSeconds: 10
readinessProbe:
  httpGet:
    path: /actuator/health/readiness
    port: http
  periodSeconds: 5
{{- end -}}

{{/* Probes of the Go services' admin port (pkg/service). */}}
{{- define "ogcloud.goProbes" -}}
livenessProbe:
  httpGet:
    path: /healthz/live
    port: admin
  periodSeconds: 10
readinessProbe:
  httpGet:
    path: /healthz/ready
    port: admin
  periodSeconds: 5
{{- end -}}

{{/* The internal CA and a writable /tmp, which every platform pod mounts. */}}
{{- define "ogcloud.commonVolumes" -}}
- name: ca
  configMap:
    name: ogcloud-internal-ca
- name: tmp
  emptyDir: {}
{{- end -}}

{{- define "ogcloud.commonMounts" -}}
- name: ca
  mountPath: /etc/ogcloud/ca
  readOnly: true
- name: tmp
  mountPath: /tmp
{{- end -}}

{{/* The public host, from publicOrigin. */}}
{{- define "ogcloud.publicHost" -}}
{{- (urlParse (required "publicOrigin is required, e.g. https://cloud.example.com" .Values.publicOrigin)).host -}}
{{- end -}}

{{/* What every Valkey container mounts; config is shared, so the reloader writes the file Valkey reads. */}}
{{- define "ogcloud.valkeyMounts" -}}
- name: scripts
  mountPath: /scripts
  readOnly: true
- name: tls
  mountPath: /tls
  readOnly: true
- name: root
  mountPath: /secrets/root
  readOnly: true
- name: users
  mountPath: /secrets/users
  readOnly: true
- name: config
  mountPath: /etc/valkey
- name: tmp
  mountPath: /tmp
{{- end -}}
