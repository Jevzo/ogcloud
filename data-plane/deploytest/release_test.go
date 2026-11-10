package deploytest

import (
	"os"
	"path/filepath"
	"strings"
	"testing"
)

const (
	publishScript = "../../deploy/release/publish.sh"
	registry      = "ghcr.io/jevzo/ogcloud"
	listDigest    = "sha256:1111111111111111111111111111111111111111111111111111111111111111"
	chartDigest   = "sha256:2222222222222222222222222222222222222222222222222222222222222222"
	amd64Digest   = "sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
	arm64Digest   = "sha256:bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"
)

// fakeDocker answers what publish.sh asks of docker from files in $FAKE and logs the manifest lists it creates.
const fakeDocker = `case "$*" in
  *"bake"*"--print"*) cat "$FAKE/bake.json" ;;
  *"imagetools create"*) echo "$*" >>"$FAKE/created" ;;
  *"Manifest.Digest"*) echo "` + listDigest + `" ;;
  *".SBOM"*) cat "$FAKE/SBOM" ;;
  *".Provenance"*) cat "$FAKE/Provenance" ;;
  *) echo "unexpected docker $*" >&2; exit 1 ;;
esac
`

// fakeCosign signs everything, verifies what $FAKE/unsigned does not name, and logs its calls.
const fakeCosign = `echo "$*" >>"$FAKE/cosign.log"
case "$1" in
  verify) for ref; do :; done; ! grep -qxF "$ref" "$FAKE/unsigned" 2>/dev/null ;;
esac
`

const bakePrint = `{"group": {"default": {"targets": ["api", "paper-26_3"]}}, "target": {
  "api": {"tags": ["` + registry + `/api:2.0.0-alpha.1"]},
  "paper-26_3": {"tags": ["` + registry + `/paper:26.3-2.0.0-alpha.1"]}}}`

func publishEnv(env []string, version string) []string {
	env = append(env, "REGISTRY="+registry)
	if version != "" {
		env = append(env, "VERSION="+version)
	}
	return env
}

func readFake(t *testing.T, dir, name string) string {
	t.Helper()
	content, err := os.ReadFile(filepath.Join(dir, name))
	if err != nil {
		t.Fatal(err)
	}
	return string(content)
}

func architectureMetadata(digest string) string {
	return `{"buildx.build.warnings": [], "api": {"image.name": "` + registry + `/api", "containerimage.digest": "` + digest + `"},
  "paper-26_3": {"image.name": "` + registry + `/paper", "containerimage.digest": "` + digest + `"}}`
}

func TestPublishMergesEveryArchitectureIntoOneSignedListPerTag(t *testing.T) {
	dir, env := fakes(t, map[string]string{"docker": fakeDocker, "cosign": fakeCosign}, map[string]string{
		"bake.json": bakePrint, "amd64.json": architectureMetadata(amd64Digest), "arm64.json": architectureMetadata(arm64Digest),
	})
	refs := filepath.Join(dir, "refs.txt")
	if out, err := runScript(t, publishEnv(env, "2.0.0-alpha.1"), publishScript, "merge-images", refs,
		filepath.Join(dir, "amd64.json"), filepath.Join(dir, "arm64.json")); err != nil {
		t.Fatalf("merge-images failed: %v\n%s", err, out)
	}
	created := readFake(t, dir, "created")
	for _, image := range []string{"api", "paper"} {
		tag := map[string]string{"api": "2.0.0-alpha.1", "paper": "26.3-2.0.0-alpha.1"}[image]
		want := "buildx imagetools create --tag " + registry + "/" + image + ":" + tag + " " +
			registry + "/" + image + "@" + amd64Digest + " " + registry + "/" + image + "@" + arm64Digest
		if !strings.Contains(created, want+"\n") {
			t.Errorf("no manifest list %q in\n%s", want, created)
		}
	}
	signed := registry + "/api@" + listDigest + "\n" + registry + "/paper@" + listDigest + "\n"
	if got := readFake(t, dir, "refs.txt"); got != signed {
		t.Errorf("refs = %q, want %q", got, signed)
	}
	if cosign := readFake(t, dir, "cosign.log"); !strings.Contains(cosign, "sign --yes "+registry+"/paper@"+listDigest) {
		t.Errorf("the lists were not signed by digest:\n%s", cosign)
	}
}

func TestPublishSignsTheChartByThePushedDigest(t *testing.T) {
	helm := `case "$1" in
  package) ;;
  push) echo "$*" >"$FAKE/pushed"; printf 'Pushed: x\nDigest: ` + chartDigest + `\n' ;;
  *) exit 1 ;;
esac
`
	dir, env := fakes(t, map[string]string{"helm": helm, "cosign": fakeCosign}, nil)
	refs := filepath.Join(dir, "refs.txt")
	if out, err := runScript(t, publishEnv(env, "2.0.0-alpha.1"), publishScript, "push-chart", refs); err != nil {
		t.Fatalf("push-chart failed: %v\n%s", err, out)
	}
	if pushed := readFake(t, dir, "pushed"); !strings.Contains(pushed, "ogcloud-2.0.0-alpha.1.tgz oci://"+registry+"/charts") {
		t.Errorf("the chart went elsewhere: %s", pushed)
	}
	if got, want := readFake(t, dir, "refs.txt"), registry+"/charts/ogcloud@"+chartDigest+"\n"; got != want {
		t.Errorf("refs = %q, want %q", got, want)
	}
}

func TestPublishVerifiesSignaturesAndEveryPlatformsAttestations(t *testing.T) {
	image, chart := registry+"/api@"+listDigest, registry+"/charts/ogcloud@"+chartDigest
	both := "linux/amd64 linux/arm64 "
	tests := []struct {
		name, sbom, provenance, unsigned, wantErr string
	}{
		{name: "signed and attested", sbom: both, provenance: both},
		{name: "an SBOM missing", sbom: "linux/amd64 ", provenance: both, wantErr: "has no SBOM for linux/arm64"},
		{name: "provenance missing", sbom: both, provenance: "", wantErr: "has no Provenance for linux/amd64"},
		{name: "an unsigned image", sbom: both, provenance: both, unsigned: image, wantErr: "is not signed"},
		{name: "an unsigned chart", sbom: both, provenance: both, unsigned: chart, wantErr: "is not signed"},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			dir, env := fakes(t, map[string]string{"docker": fakeDocker, "cosign": fakeCosign}, map[string]string{
				"SBOM": tt.sbom, "Provenance": tt.provenance, "unsigned": tt.unsigned,
				"images.txt": image + "\n", "chart.txt": chart + "\n",
			})
			out, err := runScript(t, publishEnv(env, ""), publishScript, "verify",
				filepath.Join(dir, "images.txt"), filepath.Join(dir, "chart.txt"))
			if tt.wantErr == "" {
				if err != nil || !strings.Contains(out, "verified "+chart) {
					t.Fatalf("verify failed: %v\n%s", err, out)
				}
				return
			}
			if err == nil || !strings.Contains(out, tt.wantErr) {
				t.Fatalf("verify = %v, want a failure containing %q:\n%s", err, tt.wantErr, out)
			}
		})
	}
}

func TestPublishVerifyRefusesAnEmptyRelease(t *testing.T) {
	dir, env := fakes(t, map[string]string{"docker": fakeDocker, "cosign": fakeCosign}, map[string]string{"refs.txt": ""})
	if out, err := runScript(t, publishEnv(env, ""), publishScript, "verify", filepath.Join(dir, "refs.txt")); err == nil {
		t.Fatalf("verify passed without refs:\n%s", out)
	}
}

func TestPublishNamesThePackagesForTheCleanup(t *testing.T) {
	_, env := fakes(t, map[string]string{"docker": fakeDocker}, map[string]string{"bake.json": bakePrint})
	out, err := runScript(t, publishEnv(env, ""), publishScript, "packages")
	if err != nil {
		t.Fatalf("packages failed: %v\n%s", err, out)
	}
	if want := "ogcloud/api,ogcloud/paper,ogcloud/charts/ogcloud\n"; !strings.HasSuffix(out, want) {
		t.Errorf("packages = %q, want %q", out, want)
	}
}
