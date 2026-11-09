{{- /*
One Valkey (D-0021): our own StatefulSet, standalone or three pods that each run Valkey and a Sentinel (D-0048),
starting with pod 0 as the primary. TLS from the internal CA (D-0041); users are reloaded live (D-0044).
Parameters: root, name, component, sentinel, rootSecret, usersSecret (empty for none), storage, resources.
*/}}
{{- define "ogcloud.valkeyInstance" -}}
{{- $root := .root }}
{{- $name := .name }}
{{- $labels := dict "root" $root "component" .component }}
{{- $replicas := ternary 3 1 .sentinel }}
{{- $headless := printf "%s-headless.%s.svc.cluster.local" $name $root.Release.Namespace }}
apiVersion: cert-manager.io/v1
kind: Certificate
metadata:
  name: {{ $name }}-tls
  namespace: {{ $root.Release.Namespace }}
  labels:
    {{- include "ogcloud.componentLabels" $labels | nindent 4 }}
spec:
  secretName: {{ $name }}-tls
  duration: 2160h
  renewBefore: 360h
  privateKey:
    algorithm: ECDSA
    size: 256
    rotationPolicy: Always
    # Java's PEM keystores, Kafka's among them, read only PKCS#8 keys.
    encoding: PKCS8
  dnsNames:
    - localhost
    - {{ $name }}
    - {{ $name }}.{{ $root.Release.Namespace }}.svc
    - {{ $name }}.{{ $root.Release.Namespace }}.svc.cluster.local
    - "*.{{ $name }}-headless.{{ $root.Release.Namespace }}.svc"
    - "*.{{ $headless }}"
  ipAddresses:
    - 127.0.0.1
  issuerRef:
    kind: ClusterIssuer
    name: ogcloud-internal
---
{{- /* Sentinels and replicas reach each other by name, also before they are ready. */}}
apiVersion: v1
kind: Service
metadata:
  name: {{ $name }}-headless
  namespace: {{ $root.Release.Namespace }}
  labels:
    {{- include "ogcloud.componentLabels" $labels | nindent 4 }}
spec:
  clusterIP: None
  publishNotReadyAddresses: true
  selector:
    {{- include "ogcloud.selector" $labels | nindent 4 }}
  ports:
    - name: valkey
      port: 6379
    - name: sentinel
      port: 26379
---
apiVersion: v1
kind: Service
metadata:
  name: {{ $name }}
  namespace: {{ $root.Release.Namespace }}
  labels:
    {{- include "ogcloud.componentLabels" $labels | nindent 4 }}
spec:
  selector:
    {{- include "ogcloud.selector" $labels | nindent 4 }}
  ports:
    - name: valkey
      port: 6379
---
apiVersion: apps/v1
kind: StatefulSet
metadata:
  name: {{ $name }}
  namespace: {{ $root.Release.Namespace }}
  labels:
    {{- include "ogcloud.componentLabels" $labels | nindent 4 }}
spec:
  serviceName: {{ $name }}-headless
  replicas: {{ $replicas }}
  selector:
    matchLabels:
      {{- include "ogcloud.selector" $labels | nindent 6 }}
  template:
    metadata:
      labels:
        {{- include "ogcloud.selector" $labels | nindent 8 }}
      annotations:
        checksum/scripts: {{ ($root.Files.Glob "files/valkey/*.sh").AsConfig | sha256sum }}
    spec:
      automountServiceAccountToken: false
      # Service links would set e.g. OGCLOUD_VALKEY_PORT=tcp://…, which Spring binds to ogcloud.valkey.port.
      enableServiceLinks: false
      priorityClassName: {{ $root.Values.priorityClasses.platform.name }}
      securityContext:
        {{- include "ogcloud.podSecurity" $root | nindent 8 }}
      {{- $env := list
        (dict "name" "HEADLESS_SERVICE" "value" $headless)
        (dict "name" "STATEFULSET" "value" $name)
        (dict "name" "REPLICAS" "value" (toString $replicas))
        (dict "name" "SENTINEL_MASTER" "value" (ternary (include "ogcloud.valkeySentinelMaster" $root) "" .sentinel))
        (dict "name" "APP_RULES" "value" (include "ogcloud.valkeyRules" "api")) }}
      containers:
        - name: valkey
          image: {{ $root.Values.valkey.image }}
          command: [/scripts/start-valkey.sh]
          env:
            {{- toYaml $env | nindent 12 }}
          ports:
            - name: valkey
              containerPort: 6379
          readinessProbe:
            exec:
              command: [/scripts/probe.sh, "6379"]
            periodSeconds: 5
            timeoutSeconds: 6
          livenessProbe:
            exec:
              command: [/scripts/probe.sh, "6379"]
            periodSeconds: 10
            timeoutSeconds: 6
            failureThreshold: 6
          securityContext:
            {{- include "ogcloud.containerSecurity" $root | nindent 12 }}
          resources:
            {{- toYaml .resources | nindent 12 }}
          volumeMounts:
            {{- include "ogcloud.valkeyMounts" (ne .usersSecret "") | nindent 12 }}
            - name: data
              mountPath: /data
        {{- if .sentinel }}
        - name: sentinel
          image: {{ $root.Values.valkey.image }}
          command: [/scripts/start-sentinel.sh]
          env:
            {{- toYaml $env | nindent 12 }}
          ports:
            - name: sentinel
              containerPort: 26379
          readinessProbe:
            exec:
              command: [/scripts/probe.sh, "26379"]
            periodSeconds: 5
            timeoutSeconds: 6
          livenessProbe:
            exec:
              command: [/scripts/probe.sh, "26379"]
            periodSeconds: 10
            timeoutSeconds: 6
            failureThreshold: 6
          securityContext:
            {{- include "ogcloud.containerSecurity" $root | nindent 12 }}
          resources:
            requests:
              cpu: 20m
              memory: 32Mi
            limits:
              memory: 64Mi
          volumeMounts:
            {{- include "ogcloud.valkeyMounts" (ne .usersSecret "") | nindent 12 }}
            - name: sentinel
              mountPath: /etc/sentinel
        {{- end }}
        {{- /* Loads changed users without a restart (D-0044). */}}
        - name: acl-reloader
          image: {{ $root.Values.valkey.image }}
          command: [/scripts/reload-acl.sh]
          env:
            {{- toYaml $env | nindent 12 }}
          readinessProbe:
            exec:
              command: [/scripts/probe.sh, reloader]
            periodSeconds: 10
          livenessProbe:
            exec:
              command: [/scripts/probe.sh, reloader]
            initialDelaySeconds: 30
            periodSeconds: 30
          securityContext:
            {{- include "ogcloud.containerSecurity" $root | nindent 12 }}
          resources:
            requests:
              cpu: 10m
              memory: 16Mi
            limits:
              memory: 32Mi
          volumeMounts:
            {{- include "ogcloud.valkeyMounts" (ne .usersSecret "") | nindent 12 }}
      volumes:
        - name: scripts
          configMap:
            name: ogcloud-valkey-scripts
            defaultMode: 0555
        - name: tls
          secret:
            secretName: {{ $name }}-tls
        - name: root
          secret:
            secretName: {{ .rootSecret }}
        {{- if .usersSecret }}
        - name: users
          secret:
            secretName: {{ .usersSecret }}
            optional: true
        {{- end }}
        - name: config
          emptyDir: {}
        - name: sentinel
          emptyDir: {}
        - name: tmp
          emptyDir: {}
  volumeClaimTemplates:
    - metadata:
        name: data
      spec:
        accessModes: [ReadWriteOnce]
        {{- with .storage.storageClass }}
        storageClassName: {{ . }}
        {{- end }}
        resources:
          requests:
            storage: {{ .storage.size }}
{{- end -}}
