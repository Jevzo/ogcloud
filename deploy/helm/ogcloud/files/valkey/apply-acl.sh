#!/bin/sh
# One pass of the users' reload (reload-acl.sh): renders the ACL file and has Valkey load it, and its Sentinel take the
# same users. A file Valkey rejects is put back to the one it runs, so the disk never holds users Valkey refused.
set -eu
# The scripts are mounted together at /scripts.
# shellcheck source=common.sh
. /scripts/common.sh
target=/etc/valkey/users.acl
accepted="${target}.accepted"

cp "${target}" "${accepted}"
/scripts/render-acl.sh
if ! answer="$(cli -h localhost -p 6379 ACL LOAD 2>&1)"; then
  mv "${accepted}" "${target}"
  echo "Valkey refused the new users and keeps the ones it has: ${answer}" >&2
  exit 1
fi
rm -f "${accepted}"
if [ -n "${SENTINEL_MASTER:-}" ]; then
  /scripts/sentinel-users.sh | while read -r _ name rules; do
    # shellcheck disable=SC2086 # The rules are separate ACL arguments.
    cli -h localhost -p 26379 ACL SETUSER "${name}" reset ${rules} >/dev/null || exit 1
  done
fi
