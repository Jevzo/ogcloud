# Upgrading OgCloud

An upgrade changes up to three things: CRDs, the cluster-wide operators and charts in
`deploy/platform-deps.yaml`, and OgCloud itself (its images and chart). `mise run test:upgrade` installs the
previous version on k3d, upgrades it to the checkout and runs the end-to-end suite; run it before every release.

## Before you start

1. Take a PostgreSQL backup, because migrations cannot be undone:

   ```sh
   kubectl -n ogcloud apply -f - <<EOF
   apiVersion: postgresql.cnpg.io/v1
   kind: Backup
   metadata:
     name: before-upgrade
   spec:
     cluster:
       name: ogcloud-postgres
     method: plugin
     pluginConfiguration:
       name: barman-cloud.cloudnative-pg.io
   EOF
   kubectl -n ogcloud wait --for=jsonpath='{.status.phase}'=completed backup/before-upgrade --timeout=15m
   ```

2. Make sure your copy of `mise run backup:root-key` is current. It changes only when bootstrap adds root keys or
   identities, but a restore without it cannot decrypt Infisical.

## Order

Upgrade in this order: CRDs, then the operators, then OgCloud.

- **Terraform and k3s** apply everything at once and converge through retries (D-0159). The CRD manifests are plain
  documents, so the API server takes them before any helm job runs.
- **Tilt** installs `platform-deps.yaml` in its order and the chart last.
- **Plain Helm**: apply the manifests in `platform-deps.yaml` server-side, then `helm upgrade` each chart in file
  order, then OgCloud's.

## CRDs

Helm never changes the CRDs a chart keeps in `crds/`. For the charts whose CRDs OgCloud uses (Strimzi, and the
Prometheus operator with monitoring), `platform-deps.yaml` pins the CRDs as a manifest that `follows` the chart
(D-0171). When Renovate bumps one of those charts, CI fails until the manifest is bumped too: take the new release's
CRD file, and set its `url`, `sha256` and `follows.version`.

A cluster installed before these manifests existed has CRDs that Helm created. The first apply then conflicts over
their fields, so take them over once:

```sh
kubectl apply --server-side --force-conflicts -f <the manifest's url>
```

## Operators and dependencies

- **Strimzi:** upgrade the operator first. Raise `kafka.version` in OgCloud's values only to a Kafka version the new
  operator supports, and raise the Kafka test image with it (D-0113). Strimzi then rolls the brokers one by one.
- **CloudNativePG:** a new operator restarts the instances, switching the primary over first. A minor PostgreSQL
  version is a new `postgres.image`. A major version (D-0067) is a new image of the next major: CloudNativePG
  upgrades the data in place while the database is down. Take the backup above first.
- **cert-manager and trust-manager** render their CRDs from templates, so `helm upgrade` changes them.

## Infisical

The new Infisical image migrates its database when it starts. With one replica (`infisical.replicas`, the default)
it is unavailable for a short time; with two, as on Hetzner, the pods are replaced one at a time and the old one may
fail requests against the migrated schema until it is gone. Workloads keep the Secrets the operator already synced. Migrations cannot be undone, so a rollback means
restoring PostgreSQL. Its root Secrets never change on an upgrade.

## OgCloud

- The API runs the Flyway migrations when it starts (D-0009). The controller stays unready until the schema has the
  version it was built for, so it never runs against an old schema.
- New plugins or configs in a runtime bundle make the controller replace that profile's servers through a rollout
  job (D-0079). New server images matter only for profiles that name them.
- Upgrading from 2.0.0-alpha.1 or older moves every in-game text from `&` colour codes to the tag format (D-0200,
  D-0201): migration V5 rewrites the stored network settings, permission groups and NPC titles and subtitles, turns
  the groups' name colours into name formats with `{name}` (D-0203), and the `%name%` placeholders of the tablist
  and NPC lines into `{name}`. Expect during the rollout:
  - V5 drops `permission_groups.name_color` (D-0206). Until the new API and controller pods replace the old ones,
    the old pods' permission-group reads fail, logins included, so players cannot join for that minute or two.
  - The load balancer shows an empty MOTD until the new controller renders the server list's texts, and warns once.
  - Servers that still run the older plugins show the new texts' tags as written until the bundle rollout replaces
    them.
- Rolling the chart back with Helm is safe only if no migration ran. Otherwise, restore the backup from above as in
  the restore drill (`mise run test:restore`).
- The seed (Infisical's first admin, the Cloudflare token, the backup target's keys) is the Secret `ogcloud-seed`
  that the installer applies, never a chart value (D-0188). A release installed before that kept the seed in its
  values, so Helm's release history (`sh.helm.release.v1.ogcloud.*` in the release namespace) still holds it until
  those revisions roll out of the history; delete the old revisions once the upgrade has converged.
