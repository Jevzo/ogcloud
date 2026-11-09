#!/bin/sh
# Starts this pod's Sentinel (D-0048). Sentinel rewrites its file, so it lives in an emptyDir.
set -eu
# The scripts are mounted together at /scripts.
# shellcheck source=common.sh
. /scripts/common.sh
/scripts/render-acl.sh
sentinel_password="$(cat /secrets/root/SENTINEL_PASSWORD)"
conf=/etc/sentinel/sentinel.conf
{
  cat <<CONF
port 0
tls-port 26379
tls-cert-file /tls/tls.crt
tls-key-file /tls/tls.key
tls-ca-cert-file /tls/ca.crt
tls-auth-clients no
tls-replication yes
tls-auto-reload-interval ${TLS_RELOAD_SECONDS}
sentinel announce-hostnames yes
sentinel resolve-hostnames yes
sentinel announce-ip ${fqdn}
sentinel monitor ${SENTINEL_MASTER} $(current_primary) 6379 2
sentinel auth-user ${SENTINEL_MASTER} ogcloud-sentinel
sentinel auth-pass ${SENTINEL_MASTER} ${sentinel_password}
sentinel sentinel-user ogcloud-sentinel
sentinel sentinel-pass ${sentinel_password}
sentinel down-after-milliseconds ${SENTINEL_MASTER} 5000
sentinel failover-timeout ${SENTINEL_MASTER} 60000
sentinel parallel-syncs ${SENTINEL_MASTER} 1
user default off
CONF
  /scripts/sentinel-users.sh
} > "${conf}"
chmod 600 "${conf}"
exec valkey-server "${conf}" --sentinel
