#!/bin/sh
# Starts Valkey (D-0048): TLS only from the internal CA (D-0041), reloaded when renewed; users from the ACL file;
# keyspace events for the API's indexed sessions (D-0095). With Sentinel, a pod rejoins as a replica of the
# primary the sentinels name.
set -eu
# The scripts are mounted together at /scripts.
# shellcheck source=common.sh
. /scripts/common.sh
/scripts/render-acl.sh
conf=/etc/valkey/valkey.conf
cat > "${conf}" <<CONF
port 0
tls-port 6379
tls-cert-file /tls/tls.crt
tls-key-file /tls/tls.key
tls-ca-cert-file /tls/ca.crt
tls-auth-clients no
tls-replication yes
tls-auto-reload-interval ${TLS_RELOAD_SECONDS}
aclfile /etc/valkey/users.acl
notify-keyspace-events Egx
dir /data
appendonly yes
CONF
if [ -n "${SENTINEL_MASTER:-}" ]; then
  {
    echo "masteruser ogcloud-replication"
    echo "masterauth $(cat /secrets/root/REPLICATION_PASSWORD)"
    echo "replica-announce-ip ${fqdn}"
  } >> "${conf}"
  primary="$(current_primary)"
  if [ "${primary}" != "${fqdn}" ]; then
    echo "replicaof ${primary} 6379" >> "${conf}"
  fi
fi
chmod 600 "${conf}"
exec valkey-server "${conf}"
