#!/bin/sh
# Reloads the users whenever their Secrets change, without restarting Valkey (D-0044): kubelet updates mounted
# Secrets in place, so the files are compared every few seconds.
set -eu
# The scripts are mounted together at /scripts.
# shellcheck source=common.sh
. /scripts/common.sh

apply() {
  /scripts/render-acl.sh || return 1
  cli -h localhost -p 6379 ACL LOAD >/dev/null || return 1
  if [ -n "${SENTINEL_MASTER:-}" ]; then
    /scripts/sentinel-users.sh | while read -r _ name rules; do
      # shellcheck disable=SC2086 # The rules are separate ACL arguments.
      cli -h localhost -p 26379 ACL SETUSER "${name}" reset ${rules} >/dev/null || exit 1
    done
  fi
}

last=""
while true; do
  current="$(cat /secrets/root/* /secrets/users/* 2>/dev/null | cksum)"
  if [ "${current}" != "${last}" ]; then
    if apply; then
      last="${current}"
    else
      echo "reloading the Valkey users failed; retrying" >&2
    fi
  fi
  sleep 10
done
