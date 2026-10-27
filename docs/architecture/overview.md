# Architecture overview

OgCloud 2 is a Kubernetes-native control plane for Minecraft networks. This page describes the
target design of the v2 rebuild; each section notes what is already built.

**Built so far:**

- Repository tooling: toolchain, build conventions, contract generators, CI.
- Contracts: event, node-gateway, template-agent, login and live-state Protobufs; the Kafka topic
  catalog; the runtime profile catalog (Paper 26.3, PandaSpigot 1.8.8, Velocity 4.3.0, forwarding
  in BungeeGuard mode).
- Control-plane libraries, each tested against a real dependency:

| Module | Contents |
| --- | --- |
| `control-plane/domain` | Pure rules: server state machine and lifecycle deadlines, autoscaler, drain and game states, permissions and rank durations, restart planning, retention, crash-loop breaker, pseudonymizer; the store ports |
| `control-plane/persistence` | PostgreSQL 18 schema (Flyway) and stores (Spring Data JDBC, `JdbcClient`) |
| `control-plane/livestate` | Valkey stores for servers, sessions, locks, cooldowns, crash failures and read models; Lua compare-and-set; Sentinel and ACL support |
| `control-plane/messaging` | Kafka publisher (idempotent, observed results), listener error handling with retries and dead-letter topics, SCRAM and TLS settings |
| `control-plane/storage` | S3 object store: multipart streaming uploads, presigned URLs, stale-upload cleanup |
| `control-plane/service-support` | Shared service plumbing (D-0088): Clock, UUIDv7 ids, secrets, default service configuration; golden-file test fixtures |
| `control-plane/api` | Owns Flyway, ProblemDetail errors, cursor pagination, audit interceptor (actor from the access token), trusted proxies, OpenAPI contract (`contracts/openapi/openapi.yaml`, drift-tested). OIDC provider (Spring Authorization Server, JDBC storage): PKCE apps `ogcloud-web`, `ogcloud-desktop` (any loopback port), `ogcloud-mobile`; client credentials for machines; 5-minute `at+jwt` access tokens carrying `actor` and `permissions`; rotating refresh tokens for every app with reuse detection in Valkey; RS256 keys from mounted PEM files (one active kid, all published); login sessions in Valkey (Spring Session, indexed by user); cleanup of expired authorizations every 15 minutes. Users, custom roles and machine clients under `/api/v1/users`, `/roles`, `/oauth-clients`, `/me`; first owner via `/api/v1/setup` with a setup token from Infisical; permissions are owner-only; every handler declares its permission (fail closed). MFA: owners need TOTP or a passkey, passkeys sign in alone, TOTP secrets AES-256-GCM sealed; per-IP token buckets in Valkey (strict for sign-in endpoints). Groups (`/api/v1/groups`, soft delete and restore, restart), network settings and EULA (`/api/v1/network`), jobs (`/api/v1/jobs`); changes are committed, then announced on `config.changed` / `job.requested`. Live servers and players from Valkey, server commands, transfers and console commands to Kafka, search, crash-loop status and resume. Permission groups and player ranks (`/api/v1/permission-groups`, `/api/v1/players/{uuid}/rank`). Template versions: streamed `.tar.gz` uploads into S3, download, promote or roll back, delete. NPCs, runtime profiles and bundle rollouts, audit and scaling logs, overview. Live updates over SSE (`/api/v1/events`) with single-use tickets from Valkey; each replica reads lifecycle, job progress, scaling and alert events with its own uncommitted consumer group and filters them by permission |

Each adapter ships a Spring Boot auto-configuration (`ogcloud.valkey.*`, `ogcloud.kafka.*`, `ogcloud.s3.*`, the DataSource), so a service gets its ports by adding the dependency (D-0074).

| `control-plane/controller` | Lease leader election; readiness gated on the schema version; metrics, traces, JSON logs; internal gRPC over TLS. Lifecycle: `LifecycleService` (compare-and-set transitions, `ServerLifecycleChanged` events), leader-only `Provisioner` (rate limit, PREPARING cap, EULA and crash-loop gates), `DeadlineSweeper`, `PodWatcher` (pod informer, cordoned-node drain), `CrashLoopBreaker`, `Reconciler` (adopts or deletes orphan pods, ends servers without pods, drops sessions on vanished proxies; runs on every leader change), `AutoscalerLoop` (domain autoscaler per group, cooldown in Valkey, scaling log in PostgreSQL, `ScalingDecided` events). Kafka consumers on every replica (one group per topic): server commands, heartbeats, game states, player activity, config changes. Read models (node messages, D-0077) with `node.broadcast` fan-out, rank expiry, login resolution over internal gRPC authenticated by TokenReview. `JobRunner` with resumable, fenced jobs: group and network restarts (rolling, in batches), group deletes, forwarding-token rotation, bundle rollouts. `RuntimeBundles`: Via plugins from Modrinth (pinned SHA-512), OgCloud plugins and generated configs in S3, manifests as `runtime-bundle` read models. `TemplateVersions`: pushes registered over internal gRPC, STATIC auto-promotion, `template` read models. `CleanupLoop` (D-0043): retention per category with a dry run and metrics. `ReadModelResync` rebuilds every read model each 5 minutes and broadcasts only differences (D-0100) |

## Components

```
Player ─TCP─▶ cloud LB (PROXY) ─▶ loadbalancer (Go) ─PROXY v2─▶ Velocity ─▶ Paper / PandaSpigot / Fabric / NeoForge
                                                            └──── gRPC + TLS + SA token ───┘
                                                                           ▼
Web / Electron / Expo ─HTTPS (same origin)─▶ public Gateway ─▶ api ─Kafka─▶ controller ◀─gRPC─ gateway
Admin ─WireGuard (optional)─▶ internal Gateway ─▶ Infisical, Grafana            controller ─▶ Kubernetes API
```

| Component | Language | Role |
| --- | --- | --- |
| `api` | Kotlin, Spring MVC on virtual threads | REST API, built-in OIDC provider (users and OAuth client credentials), RBAC, audit log, Server-Sent Events, template uploads, privacy export and erasure. Owns the database schema. Never talks to Kubernetes. Scales horizontally. |
| `controller` | Kotlin, Spring | Server lifecycle and autoscaling. The Lease-elected leader runs the singleton loops: autoscaler, reconciler, deadline sweeper, job runner, runtime bundles, cleanup and crash-loop breaker. Kafka consumers run on every replica. Publishes read models to Valkey and resolves player logins for the gateway. |
| `gateway` | Go | The only endpoint plugins and mods talk to: one authenticated gRPC stream per pod. Serves queries from read models, forwards commands to Kafka, fans out events and live channels, hands presigned URLs to the template agent, exports game metrics. Scales horizontally. |
| `loadbalancer` | Go | Minecraft-protocol-aware TCP edge in front of the Velocity proxies. Membership comes only from lifecycle state. Answers status pings (MOTD, maintenance, online count) and enforces the supported protocol range. |
| `template-loader` | Go | Pod init container that pulls a template and the runtime bundle, plus a push agent that uploads new template versions. |
| `bootstrap` | Go | Install and upgrade jobs that create the secrets root and set up Infisical. |
| Integrations | Kotlin/JVM | MIT-licensed SDK, a shaded gRPC node client, platform-neutral `node-core`, and thin adapters for Velocity, Paper, PandaSpigot, Fabric and NeoForge. |
| Apps | TypeScript | Web dashboard (Vite, React, TanStack), Electron desktop app, Expo mobile app. |

## Game server pods

The controller builds every game server pod (`PodSpecBuilder`); images (T-0508) and the chart (T-0601) follow this contract.

| Aspect | Contract |
| --- | --- |
| Namespace and identity | `ogcloud-servers`, service account `ogcloud-server` with no RBAC, `automountServiceAccountToken: false`, bare pod with `restartPolicy: Never`, name `<group>-<32 hex>` |
| Labels | `cloud.ogwars.io/{server-id,group,group-type,profile,role}`, `app.kubernetes.io/{name,managed-by}`; `role` is `backend` or `proxy` |
| Images | `<registry>/<platform>:<mc-version>-<platform-version>`, run as uid and gid 1000, read-only root filesystem; a group may override the image only within the allowed prefixes |
| Files | server files in `/data` (emptyDir, or the `static-<group>` volume for STATIC), `/tmp`, the gateway token in `/var/run/secrets/ogcloud/gateway/token` (projected, audience `ogcloud-gateway`), forwarding tokens in `/var/run/secrets/ogcloud/forwarding` |
| Environment | `OGCLOUD_SERVER_ID`, `OGCLOUD_GROUP_ID`, `OGCLOUD_GATEWAY_ADDRESS`, `OGCLOUD_GATEWAY_TOKEN_FILE`, `OGCLOUD_JVM_FLAGS`; the loader also gets `OGCLOUD_TEMPLATE_VERSION_ID`, the agent `OGCLOUD_PUSH_ON_SHUTDOWN` (true for STATIC) |
| Containers | `template-loader` (init, `load`), `template-agent` (native sidecar, `agent`), `server` (port 25565 `minecraft`, TCP readiness probe) |
| Scheduling | PriorityClass `ogcloud-proxy`, `ogcloud-default-group` or `ogcloud-game`; on Hetzner the game-pool toleration and node affinity; `safe-to-evict` is `false` while players are online |

## Contracts

| Contract | Source of truth | Consumers |
| --- | --- | --- |
| REST API | Spring controllers; `contracts/openapi/openapi.yaml` is generated and drift-checked | Generated TypeScript client |
| Kafka events, gRPC services | Protobuf in `contracts/proto`, checked with `buf lint` and `buf breaking` | Generated Go (committed) and JVM code (built) |
| Kafka topics | `contracts/kafka/topics.yaml` | Generated Go and Kotlin constants, Strimzi topics and users with ACLs |
| Runtime profiles | `contracts/runtime-profiles.yaml` | Generated accessors, images, overlays, protocol range |

Code generators live in `data-plane/pkg/cmd/codegen`; `mise run check:generated` fails when
committed output is stale.

## Data stores

| Store | Holds |
| --- | --- |
| PostgreSQL 18 (CloudNativePG) | Groups, network settings, permission groups, players, NPCs, users and OAuth data, audit and scaling logs, jobs, template versions, runtime artifacts |
| Valkey | Live server records and player sessions (Protobuf `ServerRecord`/`PlayerSession` with a revision for compare-and-set), locks, cooldowns, crash-loop failures, read models. Every key except read models has a TTL or self-healing index. |
| Kafka (Strimzi) | Commands and events between services; retention 7 days, dead letters 14 days |
| S3 (Hetzner Object Storage or SeaweedFS) | Templates and runtime bundles |
| Infisical | Every cluster secret except its own root |

Old data is deleted automatically under one retention policy (template versions, logs, tokens,
backups, orphaned objects).

## Security

- Workloads authenticate with projected Kubernetes service-account tokens; game pods hold no
  infrastructure credentials.
- In-cluster traffic uses TLS from a cert-manager private CA.
- Secrets live in Infisical and are synced into Kubernetes Secrets by its operator.
- People and machines use the API's built-in OIDC provider. Access tokens are short-lived JWTs
  whose permissions come from one role per user (or a machine client's scopes); refresh tokens
  rotate, and reusing an old one revokes the whole login.
- Owners must use a second factor (TOTP or passkey); sign-in endpoints are rate-limited per client
  address.
- Admin surfaces (Kubernetes API, Infisical, Grafana) are reachable only over an optional WireGuard
  VPN, or by port-forward behind an IP allowlist.

## Deployment

- **One Helm chart** installs OgCloud and is reused everywhere.
- **Local development:** minikube driven by Tilt (`mise run dev`).
- **Hetzner:** Terraform with the kube-hetzner module provisions the cluster and installs
  OgCloud through k3s HelmChart resources; an optional WireGuard box guards admin access.
- Game servers run in their own namespace on a dedicated, autoscaled node pool.
- Designed for 5,000 concurrent players and about 300 servers, with no singleton in the data path.
