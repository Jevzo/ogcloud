#!/bin/sh
# Readiness: Valkey answers over TLS as the admin user.
set -eu
# The scripts are mounted together at /scripts.
# shellcheck source=common.sh
. /scripts/common.sh
cli -h localhost -p "${1:-6379}" PING | grep -q PONG
