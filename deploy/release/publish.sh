#!/usr/bin/env bash
# Publishes a release from the release workflow (D-0193 to D-0197); REGISTRY names the registry, VERSION the release.
#   push-images <metadata.json>           this machine's architecture of every published image, by digest, with an SBOM
#                                         and provenance; `mise run images:stage` runs first
#   merge-images <refs> <metadata.json>…  one manifest list per tag from every architecture, signed; refs gain each one
#   push-chart <refs>                     the chart at VERSION, pushed next to the images and signed
#   verify <refs>…                        each signature, and every image's SBOM and provenance for each platform
#   packages                              the registry's package names for the images and the chart, comma-separated
set -euo pipefail
cd "$(dirname "$0")/../.."

: "${REGISTRY:?REGISTRY names the registry, e.g. ghcr.io/jevzo/ogcloud}"

readonly SOURCE=https://github.com/Jevzo/ogcloud
readonly CHART=$REGISTRY/charts/ogcloud
# The SBOM generator BuildKit runs; its floating default tag would make the SBOM depend on the day of the build.
readonly SBOM_SCANNER=docker/buildkit-syft-scanner:1.12.1@sha256:873a7458c3c8ac8e32c7b796c10e1600b07b963d2aa000a72a2d97aaaa2d6315
# Keyless signatures (D-0197) carry the signing workflow as their identity.
readonly IDENTITY='^https://github\.com/Jevzo/ogcloud/\.github/workflows/publish\.yml@refs/heads/(v2|main)$'
readonly ISSUER=https://token.actions.githubusercontent.com
readonly PLATFORMS=(linux/amd64 linux/arm64)
# Never the test group (T-0510).
readonly GROUPS_PUBLISHED=(default platform)
readonly BAKE=(docker buildx bake -f images/docker-bake.json -f images/docker-bake.hcl)
readonly RELEASE=(go run ./data-plane/pkg/cmd/release)

fail() {
  echo "publish: $*" >&2
  exit 1
}

need_version() {
  [[ -n ${VERSION:-} ]] || fail "VERSION names the release"
}

sign() {
  local ref=$1 refs=$2
  cosign sign --yes "$ref"
  echo "$ref" >>"$refs"
}

push_images() {
  local metadata=$1 args arg outputs=()
  need_version
  mkdir -p "$(dirname "$metadata")"
  args=$("${BAKE[@]}" --print "${GROUPS_PUBLISHED[@]}" | "${RELEASE[@]}" push-args)
  while IFS= read -r arg; do
    outputs+=("$arg")
  done <<<"$args"
  "${BAKE[@]}" "${outputs[@]}" \
    --set "*.labels.org.opencontainers.image.source=$SOURCE" \
    --set "*.labels.org.opencontainers.image.version=$VERSION" \
    --sbom="generator=$SBOM_SCANNER" --provenance=mode=max \
    --metadata-file "$metadata" "${GROUPS_PUBLISHED[@]}"
}

merge_images() {
  local refs=$1 plan tag repository sources images digest
  shift
  need_version
  [[ $# -gt 0 ]] || fail "merge-images needs each architecture's metadata"
  plan=$("${BAKE[@]}" --print "${GROUPS_PUBLISHED[@]}" | "${RELEASE[@]}" manifests "$@")
  while read -r tag repository sources; do
    read -ra images <<<"$sources"
    docker buildx imagetools create --tag "$tag" "${images[@]}"
    digest=$(docker buildx imagetools inspect "$tag" --format '{{.Manifest.Digest}}')
    [[ $digest == sha256:* ]] || fail "no digest for $tag: $digest"
    sign "$repository@$digest" "$refs"
  done <<<"$plan"
}

push_chart() {
  local refs=$1 package pushed digest
  need_version
  package=$(dirname "$refs")/chart
  mkdir -p "$package"
  helm package deploy/helm/ogcloud --version "$VERSION" --app-version "$VERSION" --destination "$package"
  pushed=$(helm push "$package/ogcloud-$VERSION.tgz" "oci://${CHART%/*}" 2>&1) || fail "helm push: $pushed"
  echo "$pushed"
  digest=$(sed -n 's/^Digest: *//p' <<<"$pushed")
  [[ $digest == sha256:* ]] || fail "helm push printed no digest"
  sign "$CHART@$digest" "$refs"
}

# The platforms an image's attestations of one kind (SBOM, Provenance) cover, space-separated.
attested() {
  docker buildx imagetools inspect "$1" --format "{{range \$platform, \$_ := .$2}}{{\$platform}} {{end}}"
}

verify() {
  local refs ref kind platform covered count=0
  for refs in "$@"; do
    while read -r ref; do
      [[ -n $ref ]] || continue
      cosign verify --certificate-identity-regexp "$IDENTITY" --certificate-oidc-issuer "$ISSUER" "$ref" >/dev/null ||
        fail "$ref is not signed by the release workflow"
      if [[ $ref != "$CHART@"* ]]; then
        for kind in SBOM Provenance; do
          covered=" $(attested "$ref" "$kind") "
          for platform in "${PLATFORMS[@]}"; do
            [[ $covered == *" $platform "* ]] || fail "$ref has no $kind for $platform"
          done
        done
      fi
      echo "verified $ref"
      count=$((count + 1))
    done <"$refs"
  done
  [[ $count -gt 0 ]] || fail "nothing to verify"
}

packages() {
  local repositories repository names=()
  repositories=$("${BAKE[@]}" --print "${GROUPS_PUBLISHED[@]}" | "${RELEASE[@]}" repositories)
  # A package is named by its path below the owner, e.g. ghcr.io/jevzo/ogcloud/api is ogcloud/api.
  for repository in $repositories "$CHART"; do
    names+=("${repository#"${REGISTRY%/*}/"}")
  done
  (
    IFS=,
    echo "${names[*]}"
  )
}

command=${1:-}
shift || true
case $command in
  push-images) [[ $# -eq 1 ]] || fail "usage: push-images <metadata.json>"; push_images "$1" ;;
  merge-images) [[ $# -ge 1 ]] || fail "usage: merge-images <refs> <metadata.json>…"; merge_images "$@" ;;
  push-chart) [[ $# -eq 1 ]] || fail "usage: push-chart <refs>"; push_chart "$1" ;;
  verify) [[ $# -ge 1 ]] || fail "usage: verify <refs>…"; verify "$@" ;;
  packages) [[ $# -eq 0 ]] || fail "usage: packages"; packages ;;
  *) fail "usage: publish.sh push-images|merge-images|push-chart|verify|packages …" ;;
esac
