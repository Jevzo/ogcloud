{{- define "ogcloud.controllerConfig" -}}
{{- $pods := .Values.controller.pods -}}
spring:
  datasource:
    url: {{ include "ogcloud.jdbcUrl" . }}
    username: ogcloud_controller
  ssl:
    bundle:
      pem:
        grpc:
          keystore:
            certificate: /var/run/secrets/ogcloud-tls/tls.crt
            private-key: /var/run/secrets/ogcloud-tls/tls.key
ogcloud:
  controller:
    pods:
      image-registry: {{ .Values.image.registry }}
      platform-version: {{ .Values.image.tag | default .Chart.AppVersion }}
      gateway-address: ogcloud-gateway.{{ .Release.Namespace }}.svc:9443
      namespace: {{ include "ogcloud.serversNamespace" . }}
      allowed-image-prefixes: {{ toJson .Values.allowedImagePrefixes }}
      static-storage-size: {{ $pods.staticStorageSize }}
      {{- with $pods.staticStorageClass }}
      static-storage-class: {{ . }}
      {{- end }}
      {{- with $pods.gamePool }}
      game-pool:
        node-label: {{ .nodeLabel }}
        node-label-value: {{ .nodeLabelValue }}
        taint-key: {{ .taintKey }}
        taint-value: {{ .taintValue }}
      {{- end }}
      priority-classes:
        proxy: {{ .Values.priorityClasses.proxy.name }}
        default-group: {{ .Values.priorityClasses.defaultGroup.name }}
        game: {{ .Values.priorityClasses.game.name }}
    grpc-auth:
      allowed-callers:
        - system:serviceaccount:{{ .Release.Namespace }}:ogcloud-gateway
    cleanup:
      dry-run: {{ .Values.retention.dryRun }}
      retention:
        template-versions-to-keep: {{ .Values.retention.templateVersionsToKeep }}
        deleted-group-grace: {{ .Values.retention.deletedGroupGrace }}
        ages:
          audit-log: {{ .Values.retention.auditLog }}
          scaling-log: {{ .Values.retention.scalingLog }}
          finished-job: {{ .Values.retention.finishedJobs }}
          stale-credential: {{ .Values.retention.staleCredentials }}
  {{- include "ogcloud.springDependencies" (dict "root" . "user" "controller") | nindent 2 }}
{{- with .Values.controller.config }}
{{ toYaml . }}
{{- end }}
{{- end -}}
