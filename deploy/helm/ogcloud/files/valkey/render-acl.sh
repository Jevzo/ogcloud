#!/bin/sh
# Writes Valkey's users (D-0044): its own root users from the bootstrap root stage, then the app users that the
# Secrets Operator syncs from Infisical once it runs; until then only the root users exist.
set -eu
root=/secrets/root
target=/etc/valkey/users.acl
# A root user exists where the root Secret has its password: Infisical's own Valkey has no replication or
# Sentinel users, the app Valkey no Infisical user (D-0151).
root_user() {
  if [ -f "${root}/$2" ]; then
    echo "user $1 on >$(cat "${root}/$2") $3"
  fi
}
{
  echo "user default off"
  echo "user ogcloud-admin on >$(cat "${root}/ADMIN_PASSWORD") ~* &* +@all"
  root_user ogcloud-replication REPLICATION_PASSWORD "-@all +psync +replconf +ping"
  root_user ogcloud-sentinel SENTINEL_PASSWORD "allchannels -@all +multi +slaveof +replicaof +ping +exec +subscribe +config|rewrite +role +publish +info +client|setname +client|kill +script|kill"
  # The chart passes the rules app users get (APP_RULES), so Infisical is limited like them.
  root_user infisical INFISICAL_PASSWORD "${APP_RULES}"
  if [ -f /secrets/users/users.acl ]; then
    cat /secrets/users/users.acl
  fi
} > "${target}.$$"
chmod 600 "${target}.$$"
# Valkey and its Sentinel render at the same time; each writes its own file and swaps it in whole.
mv "${target}.$$" "${target}"
