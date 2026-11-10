#!/bin/sh
# Reloads the users whenever their Secrets change, without restarting Valkey (D-0044): kubelet updates mounted
# Secrets in place, so the files are compared every few seconds.
set -eu

last=""
while true; do
  current="$(cat /secrets/root/* /secrets/users/* 2>/dev/null | cksum)"
  if [ "${current}" != "${last}" ]; then
    if /scripts/apply-acl.sh; then
      last="${current}"
    else
      echo "reloading the Valkey users failed; retrying" >&2
    fi
  fi
  # The container's probes check that this loop still runs.
  touch /tmp/acl-reloader.alive
  sleep 10
done
