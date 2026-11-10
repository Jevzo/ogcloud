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

{{/* Infisical's API, through its TLS sidecar (D-0149). */}}
{{- define "ogcloud.infisicalUrl" -}}
https://ogcloud-infisical.{{ .Release.Namespace }}.svc:8443
{{- end -}}

{{/* Where people open Infisical's UI: the internal Gateway when there is one (D-0045), else the Service. */}}
{{- define "ogcloud.infisicalSiteUrl" -}}
{{- if .Values.admin.gateway.enabled -}}
https://{{ required "admin.gateway.infisicalHost is required" .Values.admin.gateway.infisicalHost }}
{{- else -}}
{{ include "ogcloud.infisicalUrl" . }}
{{- end -}}
{{- end -}}

{{/* Grafana signs in through OgCloud (D-0153) where it has a real URL: on the internal Gateway, with monitoring. */}}
{{- define "ogcloud.grafanaSso" -}}
{{- if and .Values.monitoring.enabled .Values.admin.gateway.enabled }}true{{ end -}}
{{- end -}}

{{- define "ogcloud.grafanaUrl" -}}
https://{{ required "admin.gateway.grafanaHost is required with monitoring" .Values.admin.gateway.grafanaHost }}
{{- end -}}

{{/* The audience of tokens the controller shows Infisical, which the API server refuses. */}}
{{- define "ogcloud.infisicalAudience" -}}
ogcloud-infisical
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

{{/* Trace export of the Spring services with the monitoring stack (D-0025, D-0072); off without it. */}}
{{- define "ogcloud.springMonitoring" -}}
{{- with .Values.monitoring }}
{{- if .enabled }}
management:
  tracing:
    export:
      enabled: true
    sampling:
      probability: {{ .traceSampling }}
  opentelemetry:
    tracing:
      export:
        otlp:
          endpoint: {{ .otlpEndpoint }}
{{- end }}
{{- end }}
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
{{ include "ogcloud.s3Env" (dict "prefix" "OGCLOUD_S3" "endpoint" (include "ogcloud.s3Endpoint" .root) "region" $v.s3.region "bucket" $v.s3.bucket "pathStyle" $v.s3.pathStyle "keyFiles" "s3") }}
{{- end }}
{{- with $v.monitoring }}
{{- if .enabled }}
# Traces go to Tempo, sampled like the Spring services' (D-0025); the SDK reads the sampler itself.
- name: OTEL_EXPORTER_OTLP_ENDPOINT
  value: {{ .otlpEndpoint | quote }}
- name: OTEL_TRACES_SAMPLER
  value: parentbased_traceidratio
- name: OTEL_TRACES_SAMPLER_ARG
  value: {{ .traceSampling | quote }}
{{- end }}
{{- end }}
{{- end -}}

{{/* Probes of the Spring services' actuator on their management port (D-0190); the JVM gets 150 s to start. */}}
{{- define "ogcloud.springProbes" -}}
startupProbe:
  httpGet:
    path: /actuator/health/liveness
    port: admin
  periodSeconds: 5
  failureThreshold: 30
livenessProbe:
  httpGet:
    path: /actuator/health/liveness
    port: admin
  periodSeconds: 10
readinessProbe:
  httpGet:
    path: /actuator/health/readiness
    port: admin
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

{{/* The public host, from publicOrigin, without a port such as minikube's port-forward adds. */}}
{{- define "ogcloud.publicHost" -}}
{{- $host := (urlParse (required "publicOrigin is required, e.g. https://cloud.example.com" .Values.publicOrigin)).host -}}
{{- regexReplaceAll ":[0-9]+$" $host "" -}}
{{- end -}}

{{/*
What every Valkey container mounts, given whether it has synced users; config is shared, so the reloader writes the
file Valkey reads.
*/}}
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
{{- if . }}
- name: users
  mountPath: /secrets/users
  readOnly: true
{{- end }}
- name: config
  mountPath: /etc/valkey
- name: tmp
  mountPath: /tmp
{{- end -}}

{{/* The public certificates' issuer: the chart's Let's Encrypt ClusterIssuer (D-0160), or the one edge.tls.issuer names. */}}
{{- define "ogcloud.publicIssuerRef" -}}
{{- if .Values.edge.tls.letsEncrypt.enabled -}}
kind: ClusterIssuer
name: ogcloud-letsencrypt
{{- else -}}
kind: {{ .Values.edge.tls.issuer.kind }}
name: {{ required "edge.tls.issuer.name is required" .Values.edge.tls.issuer.name }}
{{- end -}}
{{- end -}}

{{/*
An S3 bucket for the Go services' objects.FromConfigPrefix: (dict "prefix" "endpoint" "region" "bucket" "pathStyle"
"keyFiles"), the key files being <keyFiles>-access-key and <keyFiles>-secret-key in the mounted secrets.
*/}}
{{- define "ogcloud.s3Env" -}}
- name: {{ .prefix }}_ENDPOINT
  value: {{ .endpoint | quote }}
- name: {{ .prefix }}_REGION
  value: {{ .region | quote }}
- name: {{ .prefix }}_BUCKET
  value: {{ .bucket | quote }}
- name: {{ .prefix }}_PATH_STYLE
  value: {{ .pathStyle | quote }}
- name: {{ .prefix }}_ACCESS_KEY_FILE
  value: /var/run/secrets/ogcloud/secrets/{{ .keyFiles }}-access-key
- name: {{ .prefix }}_SECRET_KEY_FILE
  value: /var/run/secrets/ogcloud/secrets/{{ .keyFiles }}-secret-key
{{- end -}}

{{/*
A Spring service's application.yaml: the chart's settings with the install's own (api.config, controller.config)
merged on top, so a key both name appears once, as Spring requires, and the install's value wins.
*/}}
{{- define "ogcloud.springConfig" -}}
{{- $settings := fromYaml .settings }}
{{- if hasKey $settings "Error" }}
{{- fail (printf "the chart's Spring settings do not parse: %s" $settings.Error) }}
{{- end }}
{{- toYaml (mergeOverwrite $settings (deepCopy .extra)) }}
{{- end -}}

{{/*
What every OgCloud pod's spec starts with: its service account if it has one, no automounted token, the platform's
priority (but with "withoutPriority", as bootstrap's root stage, which runs before the PriorityClasses exist) and the
restricted pod security. Service links stay off: they put a variable per Service into each container, such as
OGCLOUD_VALKEY_PORT=tcp://…, which OgCloud's own settings would read. (dict "root" $ ["serviceAccount" name]
["restartPolicy" policy] ["withoutPriority" bool])
*/}}
{{- define "ogcloud.platformPod" -}}
{{ with .serviceAccount }}serviceAccountName: {{ . }}
{{ end -}}
automountServiceAccountToken: false
enableServiceLinks: false
{{- with .restartPolicy }}
restartPolicy: {{ . }}
{{- end }}
{{- if not .withoutPriority }}
priorityClassName: {{ .root.Values.priorityClasses.platform.name }}
{{- end }}
securityContext:
  {{- include "ogcloud.podSecurity" .root | nindent 2 }}
{{- end -}}

{{/*
A server certificate from the internal CA (D-0041): ECDSA, renewed two weeks before its 90 days end, its key PKCS#8,
which Kafka's PEM keystores need and every other server accepts. Its names are "names", then each of "services" in
its three forms, then "wildcards". (dict "root" $ "name" secret "labels" componentLabels-dict ["names" list]
["services" list] ["wildcards" list] ["ipAddresses" list])
*/}}
{{- define "ogcloud.internalCertificate" -}}
{{- $namespace := .root.Release.Namespace }}
{{- $names := default list .names }}
{{- range default list .services }}
{{- $names = concat $names (list . (printf "%s.%s.svc" . $namespace) (printf "%s.%s.svc.cluster.local" . $namespace)) }}
{{- end }}
{{- $names = concat $names (default list .wildcards) -}}
apiVersion: cert-manager.io/v1
kind: Certificate
metadata:
  name: {{ .name }}
  namespace: {{ $namespace }}
  labels:
    {{- include "ogcloud.componentLabels" .labels | nindent 4 }}
spec:
  secretName: {{ .name }}
  duration: 2160h
  renewBefore: 360h
  privateKey:
    algorithm: ECDSA
    size: 256
    rotationPolicy: Always
    encoding: PKCS8
  dnsNames:
    {{- toYaml $names | nindent 4 }}
  {{- with .ipAddresses }}
  ipAddresses:
    {{- toYaml . | nindent 4 }}
  {{- end }}
  issuerRef:
    kind: ClusterIssuer
    name: ogcloud-internal
{{- end -}}
