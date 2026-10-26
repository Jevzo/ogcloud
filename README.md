# OgCloud

A Kubernetes-native control plane for Minecraft networks: it starts, scales and
stops game servers and proxies automatically, routes every player to the right
server, and ships the network features owners otherwise assemble from plugins.

> **Status:** OgCloud 2 is being rebuilt from scratch on the `v2` branch.
> OgCloud 1.x lives on `main` until the 2.0 release.

## Repository layout

| Path | Contents |
| --- | --- |
| `contracts/` | Protobuf events and gRPC services, Kafka topics, runtime profiles, generated OpenAPI |
| `control-plane/` | Kotlin: API and controller plus shared domain and adapter modules |
| `data-plane/` | Go: node gateway, load balancer, template loader, bootstrap |
| `integrations/` | JVM plugins and mods (Velocity, Paper, PandaSpigot, Fabric, NeoForge) and the SDK |
| `apps/`, `packages/` | Web, desktop and mobile apps and shared TypeScript packages |
| `images/` | Server container images |
| `deploy/` | Helm chart, Terraform modules and local development config |
| `docs/` | Architecture, runbooks and the docs site |

## Development

Every tool version is pinned in `mise.toml` and installed with [mise](https://mise.jdx.dev):

```sh
mise install      # JDK, Go, Node, pnpm, Terraform, Helm, minikube, Tilt, linters
mise run check    # all quality gates
```

## License

OgCloud is licensed under the [OgCloud License v1.0](LICENSE). The developer
SDK, public APIs and generated clients are licensed under [MIT](LICENSE-MIT).
