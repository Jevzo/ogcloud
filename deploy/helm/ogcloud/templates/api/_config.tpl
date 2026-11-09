{{- define "ogcloud.apiConfig" -}}
spring:
  datasource:
    url: {{ include "ogcloud.jdbcUrl" . }}
    username: ogcloud_api
ogcloud:
  web:
    trusted-proxies: {{ .Values.api.trustedProxies | squote }}
  auth:
    issuer: {{ required "publicOrigin is required, e.g. https://cloud.example.com" .Values.publicOrigin }}
    signing:
      active-key-id: k1
    totp:
      active-key-id: k1
  minecraft:
    eula-accepted: {{ .Values.minecraft.eulaAccepted }}
  groups:
    allowed-image-prefixes: {{ toJson .Values.allowedImagePrefixes }}
  templates:
    max-upload-size: {{ .Values.api.templates.maxUploadSize }}
  {{- include "ogcloud.springDependencies" (dict "root" . "user" "api") | nindent 2 }}
{{- include "ogcloud.springMonitoring" . }}
{{- with .Values.api.config }}
{{ toYaml . }}
{{- end }}
{{- end -}}
