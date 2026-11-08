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
