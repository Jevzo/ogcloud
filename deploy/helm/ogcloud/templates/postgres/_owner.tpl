{{/* Infisical's database and its owner, whose password is the bootstrap root's (D-0044), for initdb and recovery. */}}
{{- define "ogcloud.postgresInfisicalOwner" -}}
database: infisical
owner: infisical
secret:
  name: ogcloud-postgres-infisical
{{- end -}}
