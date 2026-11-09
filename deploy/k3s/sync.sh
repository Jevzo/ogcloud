#!/usr/bin/env bash
# Copies this checkout to /opt/ogcloud on a remote k3s host that builds its own images (D-0157): only files git tracks
# or would track, so git-ignored secrets and working files stay here; build output there survives. OGCLOUD_SSH holds
# the ssh arguments, e.g. "root@host -p 2222".
set -euo pipefail

read -ra ssh_args <<< "${OGCLOUD_SSH:?OGCLOUD_SSH holds the ssh arguments of the host}"

# macOS tar would add AppleDouble ._ files, which Helm then reads as templates.
git ls-files -co --exclude-standard -z \
  | COPYFILE_DISABLE=1 tar --no-xattrs --null -T - -czf - \
  | ssh "${ssh_args[@]}" 'set -euo pipefail
      rm -rf /tmp/ogcloud-src && mkdir -p /tmp/ogcloud-src
      tar -xzf - -C /tmp/ogcloud-src
      rsync -a --delete --exclude "**/build/" --exclude "**/.gradle/" --exclude "**/.terraform/" --exclude /.git/ \
        /tmp/ogcloud-src/ /opt/ogcloud/
      rm -rf /tmp/ogcloud-src'
