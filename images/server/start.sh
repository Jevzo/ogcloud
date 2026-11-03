#!/bin/sh
# Starts the server in /data (D-0119) with the image's JVM flags, then the group's, which win (D-0139).
set -eu
# Each variable holds space-separated flags, so word splitting is wanted.
# shellcheck disable=SC2086
exec java ${DEFAULT_JVM_FLAGS} ${OGCLOUD_JVM_FLAGS:-} ${SERVER_ARGS}
