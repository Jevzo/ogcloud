#!/bin/sh
# Prints Valkey's users as Sentinel users with the same passwords. Clients log in to Sentinels with their own
# users, so those may only discover the primary; only the admin and the Sentinels themselves manage Sentinel.
set -eu
discovery="allchannels -@all +@connection -@admin -@dangerous +info +role +subscribe +psubscribe +unsubscribe +punsubscribe"
discovery="${discovery} +sentinel|get-master-addr-by-name +sentinel|sentinels +sentinel|replicas +sentinel|slaves +sentinel|master +sentinel|masters"
grep '^user ' /etc/valkey/users.acl | grep -v '^user default ' | while read -r _ name state password _; do
  case "${name}" in
    ogcloud-admin | ogcloud-sentinel) rules="allchannels +@all" ;;
    ogcloud-replication) continue ;;
    *) rules="${discovery}" ;;
  esac
  echo "user ${name} ${state} ${password} ${rules}"
done
