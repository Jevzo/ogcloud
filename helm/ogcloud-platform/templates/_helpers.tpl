{{- define "ogcloud-platform.namespace" -}}
{{- .Values.namespace.name | default "ogcloud" -}}
{{- end -}}
