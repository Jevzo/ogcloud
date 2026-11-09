{{/*
Building blocks of the NetworkPolicies (D-0154). Peers are list items for `from` or `to`; rules are list items for
`ingress` or `egress`.
*/}}

{{/* Pods of one of our components in the release namespace: (dict "root" $ "component" "api"). */}}
{{- define "ogcloud.np.component" -}}
- podSelector:
    matchLabels:
      {{- include "ogcloud.selector" . | nindent 6 }}
{{- end -}}

{{/* Every pod of a namespace, by name. */}}
{{- define "ogcloud.np.namespace" -}}
- namespaceSelector:
    matchLabels:
      kubernetes.io/metadata.name: {{ . }}
{{- end -}}

{{/* Addresses outside the cluster and private networks: Mojang's session servers, Modrinth (D-0154). */}}
{{- define "ogcloud.np.public" -}}
- ipBlock:
    cidr: 0.0.0.0/0
    except: [10.0.0.0/8, 100.64.0.0/10, 127.0.0.0/8, 169.254.0.0/16, 172.16.0.0/12, 192.168.0.0/16]
- ipBlock:
    cidr: ::/0
    except: ["::1/128", "fc00::/7", "fe80::/10"]
{{- end -}}

{{- define "ogcloud.np.cidrs" -}}
{{- range . }}
- ipBlock:
    cidr: {{ . | quote }}
{{- end }}
{{- end -}}

{{/* Egress to CoreDNS, which every pod needs. */}}
{{- define "ogcloud.np.dns" -}}
- to:
    - namespaceSelector:
        matchLabels:
          kubernetes.io/metadata.name: kube-system
      podSelector:
        matchLabels:
          k8s-app: kube-dns
  ports:
    - {protocol: UDP, port: 53}
    - {protocol: TCP, port: 53}
{{- end -}}

{{- define "ogcloud.np.apiServer" -}}
{{- $api := .Values.networkPolicies.apiServer }}
- to:
    {{- include "ogcloud.np.cidrs" $api.cidrs | nindent 4 }}
  ports:
    {{- range $api.ports }}
    - {protocol: TCP, port: {{ . }}}
    {{- end }}
{{- end -}}

{{/* Egress to the S3 API: SeaweedFS in the cluster, or the external endpoint's addresses. */}}
{{- define "ogcloud.np.s3" -}}
{{- if .Values.s3.inCluster }}
- to:
    - namespaceSelector:
        matchLabels:
          kubernetes.io/metadata.name: {{ .Release.Namespace }}
      podSelector:
        matchLabels:
          app.kubernetes.io/name: seaweedfs
  ports:
    - {protocol: TCP, port: 8333}
{{- else }}
{{- if not .Values.networkPolicies.externalS3Cidrs }}
{{- fail "networkPolicies.externalS3Cidrs must name the external S3 endpoint's addresses" }}
{{- end }}
- to:
    {{- include "ogcloud.np.cidrs" .Values.networkPolicies.externalS3Cidrs | nindent 4 }}
  ports:
    - {protocol: TCP, port: 443}
{{- end }}
{{- end -}}

{{/* Egress to a dependency, in the cluster or external: (dict "root" $ "inCluster" bool "peers" yaml "ports" list). */}}
{{- define "ogcloud.np.dependency" -}}
- to:
    {{- if .inCluster }}
    {{- .peers | nindent 4 }}
    {{- else }}
    {{- include "ogcloud.np.cidrs" .root.Values.networkPolicies.externalCidrs | nindent 4 }}
    {{- end }}
  ports:
    {{- range .ports }}
    - {protocol: TCP, port: {{ . }}}
    {{- end }}
{{- end -}}

{{/* What the Spring and Go services share: Valkey, Kafka and, with monitoring, Tempo. */}}
{{- define "ogcloud.np.platformDependencies" -}}
{{- $v := .Values }}
{{ include "ogcloud.np.dependency" (dict "root" . "inCluster" $v.valkey.inCluster "ports" (list 6379 26379)
  "peers" (include "ogcloud.np.component" (dict "root" . "component" "valkey"))) }}
{{ include "ogcloud.np.dependency" (dict "root" . "inCluster" $v.kafka.inCluster "ports" (list 9093)
  "peers" (printf "- podSelector:\n    matchLabels:\n      strimzi.io/cluster: %s" $v.kafka.clusterName)) }}
{{ include "ogcloud.np.tempo" . }}
{{- end -}}

{{- define "ogcloud.np.postgres" -}}
{{- include "ogcloud.np.dependency" (dict "root" . "inCluster" .Values.postgres.inCluster "ports" (list .Values.postgres.port)
  "peers" "- podSelector:\n    matchLabels:\n      cnpg.io/cluster: ogcloud-postgres") }}
{{- end -}}

{{- define "ogcloud.np.tempo" -}}
{{- if .Values.monitoring.enabled }}
- to:
    {{- include "ogcloud.np.namespace" .Values.networkPolicies.namespaces.monitoring | nindent 4 }}
  ports:
    - {protocol: TCP, port: 4318}
{{- end }}
{{- end -}}

{{/* Prometheus scrapes a metrics port, with monitoring. */}}
{{- define "ogcloud.np.metrics" -}}
{{- if .root.Values.monitoring.enabled }}
- from:
    {{- include "ogcloud.np.namespace" .root.Values.networkPolicies.namespaces.monitoring | nindent 4 }}
  ports:
    - {protocol: TCP, port: {{ .port }}}
{{- end }}
{{- end -}}

{{/* Egress from game pods to the gateway in the release namespace. */}}
{{- define "ogcloud.np.gateway" -}}
- to:
    - namespaceSelector:
        matchLabels:
          kubernetes.io/metadata.name: {{ .Release.Namespace }}
      podSelector:
        matchLabels:
          {{- include "ogcloud.selector" (dict "root" . "component" "gateway") | nindent 10 }}
  ports:
    - {protocol: TCP, port: 9443}
{{- end -}}
