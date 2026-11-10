# Releasing OgCloud

The whole platform shares one version (D-0029). Until 2.0 every release is a pre-release `2.0.0-alpha.N` (D-0193).
Each release publishes:

- every image for linux/amd64 and linux/arm64 (D-0194): the platform images as `ghcr.io/jevzo/ogcloud/<name>:<version>`
  and the server images as `ghcr.io/jevzo/ogcloud/<platform>:<platform version>-<version>`, each with an SBOM and
  provenance;
- the chart as `oci://ghcr.io/jevzo/ogcloud/charts/ogcloud` at `<version>`;
- the SDK as `io.ogwars.cloud:ogcloud-sdk` on Maven Central, for real releases only (D-0195, D-0198).

Images and the chart are signed keyless with cosign by the publish workflow (D-0197).

## Cutting a release

1. Every push to `v2` updates release-please's pull request, which bumps `.release-please-manifest.json` and
   `CHANGELOG.md` from the Conventional Commits since the last release.
2. Merging that pull request tags `v<version>` and creates the GitHub release. GitHub does not run CI on pull requests
   opened with the workflow token; start CI by hand on the pull request's branch if the release should be checked
   again first.
3. The release workflow then calls the publish workflow, which waits for your approval in the `release` environment
   (D-0196). One review approves the publishing and, for real releases, the SDK's job.
4. After approval it builds each architecture on its own runner, merges the images into one manifest list per tag,
   signs the lists and the chart, and verifies every signature, SBOM and provenance before it finishes.

Run `mise run test:upgrade` before merging the release pull request (D-0172).

## Verifying an artifact

```sh
IDENTITY='^https://github\.com/Jevzo/ogcloud/\.github/workflows/publish\.yml@refs/heads/(v2|main)$'
ISSUER=https://token.actions.githubusercontent.com
cosign verify --certificate-identity-regexp "$IDENTITY" --certificate-oidc-issuer "$ISSUER" ghcr.io/jevzo/ogcloud/api:<version>
cosign verify --certificate-identity-regexp "$IDENTITY" --certificate-oidc-issuer "$ISSUER" ghcr.io/jevzo/ogcloud/charts/ogcloud:<version>
docker buildx imagetools inspect ghcr.io/jevzo/ogcloud/api:<version> --format '{{json .SBOM}}'
```

## Dry run

Start the Release workflow by hand on `v2` (`gh workflow run release.yml --ref v2`) and approve it. It publishes the
images and the chart as `0.0.0-dryrun-<commit>`, verifies them, and uploads the SDK to the Central Portal as a
deployment that is never released: drop it under Deployments in the Portal afterwards. The weekly cleanup deletes the
dry run's images and chart.

## Cleanup

`GHCR cleanup` runs every Monday (D-0043, D-0196). It deletes dry-run tags, keeps the newest 10 pre-releases per package
and every real release, and deletes untagged images that no kept image refers to. GitHub runs schedules only from the
default branch, so until 2.0 the `Schedules` workflow on `main` starts it, together with the weekly plugin and server
bump (D-0199). Started by hand (`gh workflow run schedules.yml --ref main`), the cleanup only logs what it would delete
unless you untick the dry run; the bump runs either way and may open its pull request. GitHub never deletes a
package's last version, so a package that held only dry runs keeps one piece until a release lands in it.

## One-time setup

- **The `release` environment** names you as its required reviewer and admits `v2` and `main` only.
- **Maven Central:** the namespace `io.ogwars`, which covers the group `io.ogwars.cloud` (D-0036), is verified in the
  Central Portal through a DNS TXT record on `ogwars.io`. The environment holds a Portal user token as `MAVEN_CENTRAL_USERNAME` and
  `MAVEN_CENTRAL_PASSWORD`, and the ASCII-armoured private signing key (`gpg --export-secret-keys --armor <key id>`)
  as `SIGNING_KEY`, with its passphrase as `SIGNING_KEY_PASSWORD`. Publish the public key to `keys.openpgp.org`, where
  Central looks it up.
- **GHCR:** a package the workflow creates is private. Make each one public in its package settings, or clusters need
  pull credentials; there also check that the repository holds the Admin role under "Manage Actions access", which the
  cleanup needs to delete versions.
