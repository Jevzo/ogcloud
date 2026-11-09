{{/*
The backup command's pod (D-0166): both buckets' credentials and the backups' retention in days, running
(dict "root" $ "subcommand" "templates" or "restore-templates").
*/}}
{{- define "ogcloud.backupPod" -}}
automountServiceAccountToken: false
# Service links would set e.g. OGCLOUD_VALKEY_PORT=tcp://…, which Spring binds to ogcloud.valkey.port.
enableServiceLinks: false
restartPolicy: OnFailure
priorityClassName: {{ .root.Values.priorityClasses.platform.name }}
securityContext:
  {{- include "ogcloud.podSecurity" .root | nindent 2 }}
containers:
  - name: backup
    image: {{ include "ogcloud.image" (dict "root" .root "name" "backup") }}
    imagePullPolicy: {{ .root.Values.image.pullPolicy }}
    args: [{{ .subcommand }}]
    env:
      {{- include "ogcloud.s3Env" (dict "prefix" "OGCLOUD_S3" "endpoint" (include "ogcloud.s3Endpoint" .root) "region" .root.Values.s3.region "bucket" .root.Values.s3.bucket "pathStyle" .root.Values.s3.pathStyle "keyFiles" "s3") | nindent 6 }}
      {{- include "ogcloud.s3Env" (dict "prefix" "OGCLOUD_BACKUP_S3" "endpoint" .root.Values.backup.s3.endpoint "region" .root.Values.backup.s3.region "bucket" .root.Values.backup.s3.bucket "pathStyle" .root.Values.backup.s3.pathStyle "keyFiles" "backup") | nindent 6 }}
      - name: OGCLOUD_BACKUP_RETENTION_DAYS
        value: {{ trimSuffix "d" .root.Values.retention.backups | quote }}
    securityContext:
      {{- include "ogcloud.containerSecurity" .root | nindent 6 }}
    resources:
      {{- toYaml .root.Values.backup.templates.resources | nindent 6 }}
    volumeMounts:
      - name: secrets
        mountPath: /var/run/secrets/ogcloud/secrets
        readOnly: true
      - name: tmp
        mountPath: /tmp
volumes:
  - name: secrets
    secret:
      secretName: ogcloud-backup-secrets
  - name: tmp
    emptyDir: {}
{{- end -}}
