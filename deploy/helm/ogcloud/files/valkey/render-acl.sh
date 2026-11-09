#!/bin/sh
# Writes Valkey's users (D-0044): its own root users from the bootstrap root stage, then the app users that the
# Secrets Operator syncs from Infisical once it runs; until then only the root users exist.
set -eu
root=/secrets/root
target=/etc/valkey/users.acl
{
  echo "user default off"
  echo "user ogcloud-admin on >$(cat "${root}/ADMIN_PASSWORD") ~* &* +@all"
  echo "user ogcloud-replication on >$(cat "${root}/REPLICATION_PASSWORD") -@all +psync +replconf +ping"
  echo "user ogcloud-sentinel on >$(cat "${root}/SENTINEL_PASSWORD") allchannels -@all +multi +slaveof +replicaof +ping +exec +subscribe +config|rewrite +role +publish +info +client|setname +client|kill +script|kill"
  # The chart passes the rules app users get (APP_RULES), so Infisical is limited like them.
  echo "user infisical on >$(cat "${root}/INFISICAL_PASSWORD") ${APP_RULES}"
  if [ -f /secrets/users/users.acl ]; then
    cat /secrets/users/users.acl
  fi
} > "${target}.$$"
chmod 600 "${target}.$$"
# Valkey and its Sentinel render at the same time; each writes its own file and swaps it in whole.
mv "${target}.$$" "${target}"
