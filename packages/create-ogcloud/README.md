# @ogcloud/setup

`@ogcloud/setup` is an interactive deployment CLI for OgCloud.

It helps users deploy and manage OgCloud on Kubernetes with minimal prerequisites and without cloning Helm charts manually.

Project repository: https://github.com/Jevzo/ogcloud

What it does:

- checks required tools (`kubectl`, `helm`, `npm`, `npx`)
- confirms active Kubernetes context before any operation
- downloads OgCloud Helm charts directly from GitHub (no `git` required)
- generates per-network values files with guided prompts
- deploys infra/platform/dashboard charts
- updates individual component image tags (`api`, `controller`, `loadbalancer`, `dashboard`)
- destroys an OgCloud deployment cleanly

## Prerequisites

- Kubernetes cluster access (`kubectl` configured)
- Helm 3+
- Node.js 18+ (`npm` / `npx`)

## Usage

```bash
npx @ogcloud/setup
npx @ogcloud/setup ogwars
npx @ogcloud/setup --generate-config ogwars
npx @ogcloud/setup --deploy ogwars
npx @ogcloud/setup --deploy ogwars --without-backing
npx @ogcloud/setup --update ogwars api 0.1.1
npx @ogcloud/setup --destroy ogwars
```

## Commands

- `--generate-config <network>`: creates/updates Helm override files and saved network state
- `--deploy <network>`: clean install; fails if target namespace already exists
- `--deploy <network> --without-backing`: skips infra chart and deploys platform/dashboard only
- `--update <network> <component> <tag>`: updates one component image tag and reapplies chart
- `--destroy <network>`: uninstalls releases and deletes namespace

## Local Files

Generated state and values are stored under:

- `~/.ogcloud-setup/state.json`
- `~/.ogcloud-setup/networks/<network>/values.infra.yaml`
- `~/.ogcloud-setup/networks/<network>/values.platform.yaml`
- `~/.ogcloud-setup/networks/<network>/values.dashboard.yaml`

Cached Helm chart source:

- `~/.ogcloud-setup/cache/Jevzo-ogcloud-main/helm`

## Notes

- On local clusters (for example Minikube), `LoadBalancer` services may require `minikube tunnel` for external IP assignment.

## Development

```bash
npm install
npm run build
npm run typecheck
node dist/setup.js --help
```