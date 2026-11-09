#!/bin/sh
# Shared by the Valkey scripts: this pod's address, and a valkey-cli that speaks TLS as the admin user.
# shellcheck disable=SC2034 # The sourcing scripts use these.

fqdn="$(hostname).${HEADLESS_SERVICE}"
# cert-manager renews the certificate in place; Valkey reads it again this often.
TLS_RELOAD_SECONDS="${TLS_RELOAD_SECONDS:-3600}"
admin_password="$(cat /secrets/root/ADMIN_PASSWORD)"

# Gives up after a few seconds, so a peer that is gone or not resolvable yet never stalls a start.
cli() {
  timeout 5 valkey-cli -e --tls --cacert /tls/ca.crt --user ogcloud-admin --pass "${admin_password}" --no-auth-warning "$@"
}

# The primary the sentinels name, or pod 0 when none answers yet (the first start).
current_primary() {
  replica=0
  while [ "${replica}" -lt "${REPLICAS}" ]; do
    sentinel="${STATEFULSET}-${replica}.${HEADLESS_SERVICE}"
    answer="$(cli -h "${sentinel}" -p 26379 SENTINEL get-master-addr-by-name "${SENTINEL_MASTER}" 2>/dev/null | head -n 1 || true)"
    if [ -n "${answer}" ]; then
      echo "${answer}"
      return
    fi
    replica=$((replica + 1))
  done
  echo "${STATEFULSET}-0.${HEADLESS_SERVICE}"
}
