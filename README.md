<p align="center">
  <img src=".github/images/logo.webp" alt="OgCloud Logo" width="220" />
</p>

<p align="center">
  <strong>:cloud: Kubernetes-native infrastructure for running Minecraft networks.</strong>
</p>

<p align="center">
  <em>:rocket: Provision servers on demand. :globe_with_meridians: Route players intelligently. :hammer_and_wrench: Manage your whole network from one control plane.</em>
</p>

<p align="center">
  <img alt="Kubernetes" src="https://img.shields.io/badge/Kubernetes-Native-326CE5?style=for-the-badge&logo=kubernetes&logoColor=white" />
  <img alt="Spring" src="https://img.shields.io/badge/Spring%20%2B%20Kotlin-Control%20Plane-6DB33F?style=for-the-badge&logo=spring&logoColor=white" />
  <img alt="Go" src="https://img.shields.io/badge/Go-Load%20Balancer-00ADD8?style=for-the-badge&logo=go&logoColor=white" />
</p>

---

## :brain: What We Are

OgCloud is a Kubernetes-native control plane for Minecraft networks.

It gives you one platform for:

- :globe_with_meridians: network ingress and TCP routing
- :video_game: proxy and backend server lifecycle
- :chart_with_upwards_trend: autoscaling and maintenance workflows
- :package: templates and backing services
- :desktop_computer: dashboard + API operations
- :electric_plug: plugin-level integration APIs

Repository: https://github.com/Jevzo/ogcloud

## :test_tube: Development Status

OgCloud is still actively in development.

Current status: **beta and production-ready** for teams comfortable with Kubernetes operations and iterative releases.

Planned roadmap: support multiple Minecraft versions in a single cluster through protocol-based routing at the load
balancer, with plugin-side translation.

## :bulb: Why OgCloud

- :white_check_mark: Replace hand-maintained server fleets with managed lifecycle automation.
- :white_check_mark: Scale by group rules and player demand, not guesswork.
- :white_check_mark: Keep gameplay plugins connected to live cloud state.
- :white_check_mark: Update API/controller/loadbalancer/dashboard independently.
- :white_check_mark: Use one platform model from local testing to production clusters.

## :crossed_swords: Why The Alternatives Lose

Practical version: most stacks in this space still fall into one of three buckets:
- legacy host-first architecture,
- infrastructure primitives sold as a complete platform,
- or process orchestration with modern branding on top.

OgCloud wins from a controller + API + load balancer perspective because it is built as a real control-plane stack:

- :white_check_mark: Controller lifecycle behavior for scale, churn, and failure handling (not start/stop scripts).
- :white_check_mark: Full operational API surface for scriptable, observable network operations.
- :white_check_mark: Dedicated Minecraft-aware ingress with proxy heartbeat tracking and clean drain behavior.
- :white_check_mark: Kubernetes-native deployment workflows with Helm and setup automation.

Competitor reality check:

- **PoloCloud**: promising, but still feels like a moving target and adds platform overhead before operational confidence.
- **CloudNet**: historically important, but still rooted in node-centric, host-first architecture from an older era.
- **Shulker**: technically strong, but closer to a substrate than a complete opinionated platform; teams still assemble too much themselves.
- **SimpleCloud**: calls itself "simply the best" but their own positioning admits Kubernetes is "not possible right now" and the Docker push stalled out. That is legacy process management with cloud branding.

For serious Minecraft networks, the gap is clear: OgCloud is built as a modern control plane, while most alternatives are either legacy models or incomplete platform stories.

## :sparkles: Feature List

- :wheel_of_dharma: Kubernetes-native architecture for Minecraft workloads
- :compass: Dedicated Minecraft-protocol-aware load balancer
- :robot: Controller-driven autoscaling and server orchestration
- :computer: Dashboard + REST API for network operations
- :card_index_dividers: Template storage and distribution flow
- :mailbox_with_mail: Kafka + Redis + MongoDB integration
- :electric_plug: Paper and Velocity plugin APIs
- :bricks: Helm charts split by concern: infra, platform, dashboard
- :motorway: Planned: multi-version network support (single cluster, protocol-aware routing + plugin translation)

---

## :zap: Quick Install Guide

### :white_check_mark: Prerequisites

- `kubectl` configured for your target cluster
- `helm`
- Node.js 18+ (`npm` / `npx`)

### :rocket: Fastest Path (Recommended)

If you want the easiest setup: this is it.
Spin up the wizard, answer a few prompts, and you are deploying in minutes.

```bash
npx @ogcloud/setup
```

### :compass: Straight Command Path

```bash
npx @ogcloud/setup --generate-config ogwars
npx @ogcloud/setup --deploy ogwars
```

### :toolbox: Common Commands

```bash
npx @ogcloud/setup --generate-config <network>
npx @ogcloud/setup --deploy <network>
npx @ogcloud/setup --deploy <network> --without-backing
npx @ogcloud/setup --update <network> <dashboard|api|loadbalancer|controller> <tag>
npx @ogcloud/setup --destroy <network>
```

### :file_cabinet: Generated Local State

- `~/.ogcloud-setup/state.json`
- `~/.ogcloud-setup/networks/<network>/values.infra.yaml`
- `~/.ogcloud-setup/networks/<network>/values.platform.yaml`
- `~/.ogcloud-setup/networks/<network>/values.dashboard.yaml`

---

## :earth_africa: Public Endpoints

A typical OgCloud deployment exposes:

- `mc.yourserver.io` -> Minecraft ingress through OgCloud load balancer
- `https://api.yourserver.io` -> control-plane API
- `https://dashboard.yourserver.io` -> admin dashboard

---

## :electric_plug: Plugin APIs

OgCloud exposes APIs for both Paper and Velocity plugins.

Use these artifacts as `compileOnly` or `provided` dependencies in your plugin. The OgCloud Paper or Velocity plugin
provides the runtime classes on the server.

GitHub Packages still requires authentication for Gradle and Maven downloads. Configure `gpr.user` and `gpr.key` in
your Gradle properties or export `GITHUB_ACTOR` and `GITHUB_TOKEN`.

### Gradle Repository

```kotlin
repositories {
    maven {
        url = uri("https://maven.pkg.github.com/jevzo/ogcloud")
        credentials {
            username = project.findProperty("gpr.user") as String? ?: System.getenv("GITHUB_ACTOR")
            password = project.findProperty("gpr.key") as String? ?: System.getenv("GITHUB_TOKEN")
        }
    }
}
```

### Paper Dependency

```kotlin
dependencies {
    compileOnly("io.ogwars.cloud:ogcloud-paper-plugin:LATEST_VERSION")
}
```

```xml
<dependency>
  <groupId>io.ogwars.cloud</groupId>
  <artifactId>ogcloud-paper-plugin</artifactId>
  <version>LATEST_VERSION</version>
  <scope>provided</scope>
</dependency>
```

### Velocity Dependency

```kotlin
dependencies {
    compileOnly("io.ogwars.cloud:ogcloud-velocity-plugin:LATEST_VERSION")
}
```

```xml
<dependency>
  <groupId>io.ogwars.cloud</groupId>
  <artifactId>ogcloud-velocity-plugin</artifactId>
  <version>LATEST_VERSION</version>
  <scope>provided</scope>
</dependency>
```

### Paper Example: Lobby Switcher Using `getPlayerGroup`

This routes players to a different lobby group depending on their resolved permission group.

```kotlin
class LobbyCommand : CommandExecutor {
    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        val player = sender as? Player ?: return true
        val cloud = OgCloudServerAPI.get()

        val permissionGroup = cloud.getPlayerGroup(player.uniqueId)
        val targetLobbyGroup = if (permissionGroup?.name.equals("staff", ignoreCase = true)) {
            "staff-lobby"
        } else {
            "lobby"
        }

        cloud.transferPlayerToGroup(player.uniqueId, targetLobbyGroup)
            .thenRun { player.sendMessage("Sending you to $targetLobbyGroup...") }
            .exceptionally {
                player.sendMessage("Failed to switch lobby.")
                null
            }

        return true
    }
}
```

### Paper Example: Match Flow + Warm Spare Request

```kotlin
val cloud = OgCloudServerAPI.get()

cloud.setGameState(GameState.INGAME)

cloud.requestServer("minigame").thenAccept { serverInfo ->
    logger.info("Warm spare requested: {} in group {}", serverInfo.id, serverInfo.group)
}
```

### Velocity Example: Route On Join With `getPlayerGroup`

```kotlin
class AutoRouteListener {
    @Subscribe
    fun onJoin(event: PostLoginEvent) {
        val player = event.player
        val cloud = OgCloudProxyAPI.get()

        val playerGroup = cloud.getPlayerGroup(player.uniqueId)
        val destinationGroup = if (playerGroup?.name.equals("vip", ignoreCase = true)) {
            "vip-lobby"
        } else {
            "lobby"
        }

        cloud.transferPlayerToGroup(player.uniqueId, destinationGroup)
            .exceptionally {
                logger.error("Failed to route {} to {}", player.username, destinationGroup, it)
                null
            }
    }
}
```

---

## :sos: Discord And Help

- Community and support: **[OgCloud | Support and Development](https://discord.gg/pE9gCBm822)**
- Source and issues: https://github.com/Jevzo/ogcloud

---

## :heart: Sponsoring

If OgCloud helps your network, sponsoring supports faster development and support capacity.

- [GitHub Sponsors](https://github.com/sponsors/Jevzo)

---

## :hammer_and_wrench: Local Development

From repo root:

```powershell
.\build.ps1 api
.\build.ps1 controller
.\build.ps1 dashboard
```

Docker build + push examples:

```powershell
.\build.ps1 docker api -p
.\build.ps1 docker controller -p
.\build.ps1 docker loadbalancer -p
.\build.ps1 docker dashboard -p
```

---

## :package: Publish `@ogcloud/setup`

```bash
cd helper/ogcloud-setup-cli
npm install
npm run build
npm version patch
npm publish --access public
```

After publish:

```bash
npx @ogcloud/setup
```
