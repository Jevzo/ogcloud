# Architecture overview

OgCloud 2 is a Kubernetes-native control plane for Minecraft networks. This page describes the
target design of the v2 rebuild; each section notes what is already built.

**Built so far:**

- Repository tooling: toolchain, build conventions, contract generators, CI.
- Contracts: event, node-gateway, template-agent, login and live-state Protobufs; the Kafka topic
  catalog; the runtime profile catalog (Paper 26.3, PandaSpigot 1.8.8, Velocity 4.3.0, forwarding
  in BungeeGuard mode).
- Control-plane and data-plane modules, each tested against a real dependency:

| Module | Contents |
| --- | --- |
| `control-plane/domain` | Pure rules: server state machine and lifecycle deadlines, autoscaler, drain and game states, permissions and rank durations, restart planning, retention, crash-loop breaker, pseudonymizer; the store ports |
| `control-plane/persistence` | PostgreSQL 18 schema (Flyway) and stores (Spring Data JDBC, `JdbcClient`) |
| `control-plane/livestate` | Valkey stores for servers, sessions, locks, cooldowns, crash failures and read models; Lua compare-and-set; Sentinel, ACL users and TLS with the private CA (`ogcloud.valkey.tls.ca-certificate-path`), applied to every Sentinel connection too |
| `control-plane/messaging` | Kafka publisher (idempotent, observed results), listener error handling with retries and dead-letter topics, SCRAM and TLS settings |
| `control-plane/storage` | S3 object store: multipart streaming uploads, presigned URLs, stale-upload cleanup |
| `control-plane/service-support` | Shared service plumbing (D-0088): Clock, UUIDv7 ids, secrets, default service configuration; golden-file test fixtures |
| `control-plane/api` | Owns Flyway, ProblemDetail errors, cursor pagination, audit interceptor (actor from the access token), trusted proxies (`ogcloud.web.trusted-proxies`, D-0108), OpenAPI contract (`contracts/openapi/openapi.yaml`, drift-tested). OIDC provider (Spring Authorization Server, JDBC storage): PKCE apps `ogcloud-web`, `ogcloud-desktop` (any loopback port), `ogcloud-mobile`; client credentials for machines; 5-minute `at+jwt` access tokens carrying `actor` and `permissions`; rotating refresh tokens for every app with reuse detection in Valkey; RS256 keys from mounted PEM files (one active kid, all published); login sessions in Valkey (Spring Session, indexed by user); cleanup of expired authorizations every 15 minutes. Users, custom roles and machine clients under `/api/v1/users`, `/roles`, `/oauth-clients`, `/me`; first owner via `/api/v1/setup` with a setup token from Infisical; permissions are owner-only; every handler declares its permission (fail closed). MFA: owners need TOTP or a passkey, passkeys sign in alone, TOTP secrets AES-256-GCM sealed; per-IP token buckets in Valkey (strict for sign-in endpoints). Groups (`/api/v1/groups`, soft delete and restore, restart; JVM flags must be plain flags, D-0143), network settings and EULA (`/api/v1/network`), jobs (`/api/v1/jobs`); changes are committed, then announced on `config.changed` / `job.requested`. Live servers and players from Valkey, server commands, transfers and console commands to Kafka, search, crash-loop status and resume. Permission groups and player ranks (`/api/v1/permission-groups`, `/api/v1/players/{uuid}/rank`). Template versions: streamed `.tar.gz` uploads into S3, download, promote or roll back, delete. NPCs (also edited in game: `npc.change` from the gateway, whose updates patch only the fields they name, D-0140, applied only within the sending server's group and audited as the player, D-0129), in-game group and network maintenance and rank changes from `admin.change` (D-0132), runtime profiles and bundle rollouts, audit and scaling logs, overview. Live updates over SSE (`/api/v1/events`) with single-use tickets from Valkey, sent from a queue per stream; each replica reads lifecycle, job progress, scaling and alert events with its own uncommitted consumer group and filters them by permission. Minecraft account link (`/api/v1/me/minecraft-link`): a one-try code in Valkey, shown in game through `account.link`; linked usernames follow Minecraft names from `player.activity` (shared consumer group). Privacy (`/api/v1/privacy`): JSON exports of players and users; erasure deletes their records and pseudonymises audit entries, job and template authors with an HMAC key from Infisical (D-0107) |
| `control-plane/controller` | Lease leader election; readiness gated on the schema version; metrics, traces, JSON logs; internal gRPC over TLS. Lifecycle: `LifecycleService` (compare-and-set transitions, `ServerLifecycleChanged` events), leader-only `Provisioner` (rate limit, PREPARING cap, EULA and crash-loop gates), `DeadlineSweeper`, `PodWatcher` (pod informer, cordoned-node drain), `CrashLoopBreaker`, `Reconciler` (adopts or deletes orphan pods, ends servers without pods, drops sessions on vanished proxies; runs on every leader change), `AutoscalerLoop` (domain autoscaler per group, cooldown in Valkey, scaling log in PostgreSQL, `ScalingDecided` events). Kafka consumers on every replica (one group per topic): server commands, heartbeats, game states, player activity, proxy player snapshots (restore lost sessions without replacing one a login created meanwhile; drop that proxy's unlisted sessions more than 30 s older than the snapshot, re-checked before removal, D-0130), config changes. Read models (node messages, D-0077, plus the group and permission-group lists for in-game queries, D-0132) with `node.broadcast` fan-out, rank expiry, login resolution over internal gRPC authenticated by TokenReview. `JobRunner` with resumable, fenced jobs: group and network restarts (rolling, in batches), group deletes, forwarding-token rotation, bundle rollouts. `RuntimeBundles`: third-party plugins from Modrinth (Via plugins for Velocity, PacketEvents for Paper and PandaSpigot, D-0137; pinned SHA-512), OgCloud plugins and generated configs in S3, manifests as `runtime-bundle` read models. `TemplateVersions`: pushes registered over internal gRPC, STATIC auto-promotion, `template` read models. `CleanupLoop` (D-0043): retention per category with a dry run and metrics. `ReadModelResync` rebuilds the config read models and online players' ranks every 5 minutes, replacing only the copy it read before PostgreSQL (Valkey compare-and-set), and broadcasts only differences (D-0100) |
| `data-plane/pkg` | One Go module (D-0110): positive-only config, JSON logs, metrics and traces, a shared process start with an admin port (health, metrics), reloading TLS files, Kafka (franz-go producer with a 5 s publish deadline (D-0121), per-replica broadcasts, client logs in slog), read-only Valkey (records, read models, Sentinel; corrupt records are skipped in lists), presigned S3 URLs, TokenReview with a verdict cache and audience check, a token-file gRPC credential, UUIDv7 ids and event metadata. Generated: the Valkey key layout (D-0111), pod labels, permission nodes, fixed Kafka keys, the template object layout, and the runtime catalog with loader overlays |
| `data-plane/bootstrap` | `bootstrap root` creates the secrets root and adds missing keys, never changing a value; `bootstrap infisical` checks its whole config first, bootstraps Infisical once, retries every later step in process, swaps the admin token for a Kubernetes-auth identity (D-0112), ensures the project, identities with their roles and generated secrets, and imports the seed when it changed, then deletes it; the admin credentials are never stored (D-0114) |
| `data-plane/gateway` | gRPC over TLS, with keepalive enforced from `contracts/session-keepalive.yaml`. Every call carries the pod's projected token (audience `ogcloud-gateway`), checked by TokenReview and bound to a live, controller-managed pod in `ogcloud-servers` through a pod informer. One session per server and node type with a bounded queue: a slow node is dropped, a reconnect replaces the old session. Hello is checked against the pod's role; Welcome comes from Valkey (network settings; for proxies every server with its group's maintenance). Each replica reads lifecycle, broadcasts, console commands, transfers, link codes, live channels and template pushes with its own uncommitted consumer group and routes each record only to the sessions that need it (D-0116); player-targeted records find the player's proxy and backend through the Valkey session. Transfers, console commands, link codes and template pushes are deliveries the node acknowledges (30 s timeout, metrics only). Inbound messages are checked against the node type: heartbeats, game states, player activity (proxy id forced), transfers, live publishes (64 KiB, 200/s, 64 subscriptions), player snapshots from proxies (D-0130), console commands, which only proxies send for an admin connected through them who holds `ogcloud.admin.command` (D-0115), and NPC edits, which only backends send for an admin playing on them who holds `ogcloud.admin.npc`; a new NPC gets its id from the gateway (D-0129), an update is a patch (D-0140). Admin requests (group or network maintenance, ranks) from an admin connected through the sending proxy with `ogcloud.admin.group`, `.network` or `.permission` are checked against the read models, answered with an `AdminRequestResult` and published on `admin.change` (D-0132). Per-node game metrics (players, TPS, MSPT, memory) come from heartbeats and vanish with the session. Queries (servers, players, a server's players, groups, profiles, permission groups, NPCs) answer from Valkey; `ResolveLogin` (proxies only) and template registration go to the controller's internal gRPC with the gateway's projected token for `ogcloud-controller`. Server commands get a `ServerCommandResult`: the plugin API may request servers and stop, kill or push only its own server, an admin on the sending proxy with `ogcloud.admin.server` may target any (D-0117). `TemplateAgentService` presigns the group's active template and the profile's runtime bundle, and uploads under `templates/<group>/<uuid>.tar.gz` |
| `data-plane/loadbalancer` | Minecraft TCP edge in front of Velocity. Membership is the RUNNING proxies in Valkey, refreshed every 2 s and nudged by `server.lifecycle` (a proxy that stops running leaves at once) and `node.broadcast`; heartbeats never add a proxy (v1 bug 1). Status pings are answered locally (MOTD, version, maintenance variants, max and online players). Logins outside the catalog's client range are refused; the rest go to the least-filled proxy with room, or round robin, with a PROXY v2 header and the handshake as sent. Bounded handshake parsing (fuzzed), per-IP limits, and PROXY headers required from trusted CIDRs only (D-0118) |
| `data-plane/template-loader` | `load` (init container): asks the gateway for the pod's assets, keeps a STATIC volume that already holds the active template (SHA-256 marker) and otherwise wipes and extracts the template while checking its hash, installs the runtime bundle files atomically, then applies the platform's overlays from the catalog (D-0119). `agent` (native sidecar): a TEMPLATE_AGENT session that packs `/data` without bundle files and churn on a `push_template` delivery, and on shutdown for STATIC servers, uploads it to a presigned URL and registers it; a promoted push updates the marker. Every operation on `/data` goes through `os.Root`, so archives and links cannot escape it |
| `integrations/sdk` | MIT plugin API (D-0131, D-0141): `OgCloud.server()`/`proxy()`, lookups, transfers and server requests as futures, live channels (a payload copy per call), game state, template push, runtime NPCs; interfaces and enums only, the public API dump `api/sdk.api` is checked on every build (D-0127) |
| `integrations/node-client` | The gateway client every plugin shares (D-0008): a TLS channel trusting the internal CA, the pod's projected token reread from its file, one session with Hello and Welcome, reconnects with backoff, keepalive pings (`contracts/session-keepalive.yaml`), acknowledged deliveries, server commands correlated with their results (D-0068), live subscriptions restored on every Welcome, and the query stub; a listener that throws costs only its message; settings come from `OGCLOUD_GATEWAY_*` |
| `integrations/node-core` | Platform-neutral plugin logic (D-0050, D-0132): the English message catalog with one-pass placeholders, network and group state from the Welcome and broadcasts (older versions ignored, deleted groups kept as tombstones), the proxy's backend directory and routing (strategies over network-wide player counts refreshed every 5 s, D-0142; full and draining backends; maintenance with the default group as fallback), the rank cache from the gateway's read models (failed loads retried every 30 s), chat heads and tablist teams, the runtimes (`ProxyRuntime`, `ServerRuntime`: heartbeats, logins with the proxy's own bypass check while the permission system is off, player activity and 60-second snapshots, transfers, link codes, console commands, maintenance kicks, admin notifications; every Welcome reloads ranks, NPCs and group flags), the SDK implementation with plugin callbacks on virtual threads and ordered live-channel deliveries, and `/ogcloud` with every v1 subcommand, whose gateway-checked changes carry the acting player |
| `integrations/npc` | NPCs for every backend (D-0136) on the PacketEvents plugin from the runtime bundle (D-0137): the group's managed NPCs from the read model and the SDK's runtime NPCs, shown to nearby players with title and subtitle lines (live player and server counts), turning to players, click transfers, and `/ogcloud npc`, whose changes go to the API (D-0129); packets for 1.8 and the current Paper line |
| `integrations/velocity` | Velocity plugin over node-core: backends registered under their server id with their protocol given to ViaVersion (D-0133), logins checked by the controller and refused when the proxy is full (D-0134), OgCloud's permissions through Velocity's provider, the tablist header and footer, `/ogcloud` (`/oc`), and `/ogcloud npc` passed through to backends; the proxy stops if OgCloud cannot start |
| `integrations/bukkit` | What Paper and PandaSpigot share (D-0138), built against the 1.8 API: the backend node (pod settings, gateway session, SDK, `/ogcloud` from plugin.yml), BungeeGuard tokens checked in the handshake from the mounted Secret (D-0065), OgCloud's nodes added to each player's permissions, tablist teams by weight, TPS and MSPT from tick events, and where players stand for the NPCs; a `BukkitLine` holds what differs per server line (threads, teleports, command trees, team looks) |
| `integrations/paper` | Paper 26.3 plugin over the bukkit module: Paper's region-aware schedulers and async teleports only, so Folia can run it (D-0135), rank chat heads as components, team prefixes and colours, max players from the group, NPC packets for the current line |
| `integrations/legacy-paper` | PandaSpigot 1.8.8 plugin over the bukkit module: the main thread, rank chat heads in the chat format, team prefixes cut to 16 characters, the group's player limit checked at login (1.8 cannot set max players), NPC packets for 1.8 |

Each adapter ships a Spring Boot auto-configuration (`ogcloud.valkey.*`, `ogcloud.kafka.*`, `ogcloud.s3.*`, the DataSource), so a service gets its ports by adding the dependency (D-0074).

## Components

```
Player ─TCP─▶ cloud LB (PROXY) ─▶ loadbalancer (Go) ─PROXY v2─▶ Velocity ─▶ Paper / Folia / PandaSpigot / Fabric / NeoForge
                                                            └──── gRPC + TLS + SA token ───┘
                                                                           ▼
Web / Electron / Expo ─HTTPS (same origin)─▶ public Gateway ─▶ api ─Kafka─▶ controller ◀─gRPC─ gateway
Admin ─WireGuard (optional)─▶ internal Gateway ─▶ Infisical, Grafana            controller ─▶ Kubernetes API
```

| Component | Language | Role |
| --- | --- | --- |
| `api` | Kotlin, Spring MVC on virtual threads | REST API, built-in OIDC provider (users and OAuth client credentials), RBAC, audit log, Server-Sent Events, template uploads, privacy export and erasure. Owns the database schema. Never talks to Kubernetes. Scales horizontally. |
| `controller` | Kotlin, Spring | Server lifecycle and autoscaling. The Lease-elected leader runs the singleton loops: autoscaler, reconciler, deadline sweeper, job runner, runtime bundles, cleanup and crash-loop breaker. Kafka consumers run on every replica. Publishes read models to Valkey and resolves player logins for the gateway. |
| `gateway` | Go | The only endpoint plugins and mods talk to: one authenticated gRPC stream per server and node type. Serves queries from read models, forwards commands to Kafka, fans out events and live channels, hands presigned URLs to the template agent, exports game metrics. Scales horizontally. |
| `loadbalancer` | Go | Minecraft-protocol-aware TCP edge in front of the Velocity proxies. Membership comes only from lifecycle state. Answers status pings (MOTD, maintenance, online count) and enforces the supported protocol range. |
| `template-loader` | Go | Pod init container that pulls a template and the runtime bundle, plus a push agent that uploads new template versions. |
| `bootstrap` | Go | Install and upgrade jobs that create the secrets root and set up Infisical. |
| Integrations | Kotlin/JVM | MIT-licensed SDK, a shaded gRPC node client, platform-neutral `node-core`, and thin adapters for Velocity, Paper (also on Folia, D-0135), PandaSpigot, Fabric and NeoForge. |
| Apps | TypeScript | Web dashboard (Vite, React, TanStack) and Electron desktop app; an Expo mobile app is an optional goal after 2.0 (D-0145). |

## Game server pods

The controller builds every game server pod (`PodSpecBuilder`); images (T-0508) and the chart (T-0601) follow this contract.

| Aspect | Contract |
| --- | --- |
| Namespace and identity | `ogcloud-servers`, service account `ogcloud-server` with no RBAC, `automountServiceAccountToken: false`, bare pod with `restartPolicy: Never`, name `<group>-<32 hex>` |
| Labels | `cloud.ogwars.io/{server-id,group,group-type,profile,role}`, `app.kubernetes.io/{name,managed-by}`; `role` is `backend` or `proxy` |
| Images | `<registry>/<platform>:<mc-version>-<platform-version>`, run as uid and gid 1000, read-only root filesystem; a group may override the image only within the allowed prefixes. Server images come from one multi-stage `images/server/Dockerfile` (D-0139): Temurin 25 on Ubuntu, the profile's pinned server jar checked against its SHA-256 and patched at build time (no Mojang download in pods), the GPL text and a source offer in `/licenses`; `start.sh` runs Java in `/data` with the catalog's JVM flags, then `OGCLOUD_JVM_FLAGS` (no globbing), and on proxies first writes the newest forwarding token to `OGCLOUD_FORWARDING_SECRET_FILE` for Velocity |
| Files | server files in `/data` (emptyDir, or the `static-<group>` volume for STATIC) with OgCloud's state in `/data/.ogcloud` (template marker, bundle list), `/tmp` (server and agent), the gateway token and the internal CA in `/var/run/secrets/ogcloud/gateway/{token,ca.crt}` (projected: audience `ogcloud-gateway`, ConfigMap `ogcloud-internal-ca`), forwarding tokens in `/var/run/secrets/ogcloud/forwarding` |
| Environment | `OGCLOUD_SERVER_ID`, `OGCLOUD_GROUP_ID`, `OGCLOUD_GROUP_TYPE`, `OGCLOUD_GATEWAY_ADDRESS`, `OGCLOUD_GATEWAY_TOKEN_FILE`, `OGCLOUD_GATEWAY_CA_FILE` in every container; the server also gets `OGCLOUD_JVM_FLAGS`, `OGCLOUD_MAX_PLAYERS` and `OGCLOUD_FORWARDING_TOKENS_FILE`, proxies `OGCLOUD_FORWARDING_SECRET_FILE`; the loader gets `OGCLOUD_PROFILE_ID`, the agent `OGCLOUD_PUSH_ON_SHUTDOWN` (true for STATIC) |
| Containers | `template-loader` (init, `load`), `template-agent` (native sidecar, `agent`), `server` (port 25565 `minecraft`, TCP readiness probe) |
| Scheduling | PriorityClass `ogcloud-proxy`, `ogcloud-default-group` or `ogcloud-game`; on Hetzner the game-pool toleration and node affinity; `safe-to-evict` is `false` while players are online |

## Contracts

| Contract | Source of truth | Consumers |
| --- | --- | --- |
| REST API | Spring controllers; `contracts/openapi/openapi.yaml` is generated and drift-checked | Generated TypeScript client |
| Kafka events, gRPC services | Protobuf in `contracts/proto`, checked with `buf lint` and `buf breaking` | Generated Go (committed) and JVM code (built) |
| Kafka topics | `contracts/kafka/topics.yaml` | Generated Go and Kotlin constants, Strimzi topics and users with ACLs |
| Runtime profiles | `contracts/runtime-profiles.yaml` | Generated accessors, loader overlays (incl. `eula.txt`), server port, protocol range, and `images/docker-bake.json` (one server image per profile: stage, jar pin, JVM flags); `codegen plugins` and `codegen servers` bump the bundle plugins and server builds in a weekly PR (D-0082, D-0139) |
| Valkey keys | `contracts/livestate/keys.yaml` | Generated Go and Kotlin key builders and TTLs (D-0111) |
| Pod labels | `contracts/pod-labels.yaml` | Generated Go and Kotlin constants: the controller sets them, the gateway binds tokens with them |
| Permission nodes | `contracts/permission-nodes.yaml` | Generated Go and Kotlin constants for the plugins and the gateway's admin checks |
| Fixed Kafka keys | `contracts/kafka/keys.yaml` | Generated Go and Kotlin constants (`network` subject, `all` nodes) |
| Template object layout | `contracts/storage/templates.yaml` | Generated Go and Kotlin constants for `templates/<group>/<uuid>.tar.gz` |
| Live-channel limits, node refusal reasons | `contracts/live-channels.yaml`, `contracts/node-errors.yaml` | Generated Go and Kotlin constants for the gateway and the integrations |
| Pod environment | `contracts/pod-env.yaml` | Generated Go and Kotlin names of the variables the controller sets and the loader and node client read |
| Session keepalive | `contracts/session-keepalive.yaml` | Generated Go and Kotlin timings: node pings, their timeout, the gateway's minimum |

Code generators live in `data-plane/pkg/cmd/codegen`; `mise run check:generated` fails when
committed output is stale. `mise run check:images` (and its CI job) builds every image, boots the server images with
OgCloud's plugin and the bundle plugins it depends on, starts each platform image, fails on critical CVEs in them,
and runs the JVM node client against the gateway image (T-0510): a `contracttest` build of the gateway that takes
pods from a file instead of TokenReview, on real Kafka and Valkey seeded with the Kotlin adapters. Only that test
image carries the tag.

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

- **One Helm chart** (`deploy/helm/ogcloud`) installs OgCloud and is reused everywhere (D-0019). Cluster-wide
  operators are separate releases pinned in `deploy/platform-deps.yaml`; Infisical and SeaweedFS are subcharts, so
  the secrets install graph runs in one release (D-0146):
  - `bootstrap root` (pre-install hook) writes Infisical's root; `bootstrap infisical` (post-install Job) creates
    the project, the `ogcloud-secrets-sync` identity, every generated secret, and publishes the identity ids.
  - The Secrets Operator logs in as that identity and syncs one Secret per workload from the chart's secret
    inventory: config trees for the Spring services, key files for the Go services, the Kafka and PostgreSQL user
    passwords, and `ogcloud-forwarding` in the game namespace.
  - cert-manager's private CA (a ClusterIssuer, D-0041) signs the gateway's and controller's certificates;
    trust-manager publishes the CA as ConfigMap `ogcloud-internal-ca` in both namespaces (D-0147).
  - Dependencies run in the release, each replaceable by an external endpoint (D-0021):
    - Strimzi `Kafka` on KRaft, with the generated topics and SCRAM users.
    - A CloudNativePG `Cluster` (PostgreSQL 18): Infisical's database from initdb, managed roles for the API and
      the controller.
    - Valkey as our own StatefulSet: standalone, or three pods that each run Valkey and Sentinel (D-0048). It is
      TLS only and reloads renewed certificates; the default user is off. App users come from a synced ACL file
      that a sidecar loads live (the gateway's and load balancer's users only read). On Sentinels, app users may
      only discover the primary.
  - api, controller, gateway and load balancer run restricted (uid 1000, read-only root, no automounted token)
    with only the RBAC they use; the public Gateway routes `/api`, `/oauth2`, `/.well-known`, `/login` and
    `/webauthn` to the API (D-0098).
  - `mise run check:chart` lints every value set in `ci/` and validates it with kubeconform against pinned
    schemas; Go render tests in `data-plane/deploytest` check the invariants (D-0148).
- **Images:** `mise run images:build` builds the artifacts with mise's toolchains (Spring boot jars unpacked into
  their layers, static Go binaries, the plugin jars) into `build/images`, then bakes every image from
  `images/docker-bake.json` (server images, generated from the catalog) and `images/docker-bake.hcl` (platform images).
  The api and controller run on distroless Java 25, the Go services on distroless static, all as uid 1000 (D-0128);
  the controller image carries `ogcloud-<platform>.jar` in `/opt/ogcloud/plugins` (D-0079). Tags are
  `<registry>/<name>:<platform-version>`, as the controller expects.
- **Local development:** minikube driven by Tilt (`mise run dev`).
- **Hetzner:** Terraform with the kube-hetzner module provisions the cluster and installs
  OgCloud through k3s HelmChart resources; an optional WireGuard box guards admin access.
- Game servers run in their own namespace on a dedicated, autoscaled node pool.
- Designed for 5,000 concurrent players and about 300 servers, with no singleton in the data path.
