{{- define "ogcloud-infra.namespace" -}}
{{- .Values.namespace.name | default "ogcloud" -}}
{{- end -}}
