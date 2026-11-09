#!/bin/sh
# Probes: Valkey or Sentinel answers over TLS as the admin user on the given port; "reloader" checks that the ACL
# reloader's loop ran within the last minute.
set -eu
if [ "${1:-}" = reloader ]; then
  [ -n "$(find /tmp/acl-reloader.alive -mmin -1 2>/dev/null)" ]
  exit
fi
# The scripts are mounted together at /scripts.
# shellcheck source=common.sh
. /scripts/common.sh
cli -h localhost -p "${1:-6379}" PING | grep -q PONG
