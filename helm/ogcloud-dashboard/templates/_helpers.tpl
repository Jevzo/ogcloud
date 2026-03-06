{{- define "ogcloud-dashboard.namespace" -}}
{{- .Values.namespace.name | default "ogcloud" -}}
{{- end -}}
