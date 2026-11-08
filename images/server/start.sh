#!/bin/sh
# Starts the server in /data (D-0119) with the image's JVM flags, then the group's, which win (D-0139).
# No globbing: the flags are split into words below, and a `*` in one must stay as typed (D-0143).
set -euf
# Proxies forward with the newest token (D-0065); Velocity reads it once, from a file of its own.
if [ -n "${OGCLOUD_FORWARDING_SECRET_FILE:-}" ]; then
  token=$(grep -v '^[[:space:]]*$' "${OGCLOUD_FORWARDING_TOKENS_FILE}" | tail -n 1)
  if [ -z "${token}" ]; then
    echo "no forwarding token in ${OGCLOUD_FORWARDING_TOKENS_FILE}" >&2
    exit 1
  fi
  mkdir -p "$(dirname "${OGCLOUD_FORWARDING_SECRET_FILE}")"
  (umask 077 && printf '%s' "${token}" > "${OGCLOUD_FORWARDING_SECRET_FILE}")
fi
# Each variable holds space-separated flags, so word splitting is wanted.
# shellcheck disable=SC2086
exec java ${DEFAULT_JVM_FLAGS} ${OGCLOUD_JVM_FLAGS:-} ${SERVER_ARGS}
