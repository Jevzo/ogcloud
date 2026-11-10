package deploytest

import (
	"os"
	"os/exec"
	"path/filepath"
	"strings"
	"testing"
)

const rootKeyScript = "../../deploy/backup/root-key.sh"

// exported renders objects as root-key.sh's go-template does.
func exported(objects ...string) string {
	var out strings.Builder
	for _, object := range objects {
		kind, name, _ := strings.Cut(object, "/")
		out.WriteString("---\napiVersion: v1\nkind: " + kind + "\nmetadata:\n  name: " + name + "\n  labels:\n" +
			"    app.kubernetes.io/managed-by: \"ogcloud-bootstrap\"\ndata:\n  \"key\": \"dmFsdWU=\"\n")
	}
	return out.String()
}

// exportRootKey runs root-key.sh export against a kubectl that prints labelled for the bootstrap selector and backup
// for the backup credentials.
func exportRootKey(t *testing.T, labelled, backup string) (string, error) {
	t.Helper()
	dir := t.TempDir()
	for name, content := range map[string]string{"labelled": labelled, "backup": backup} {
		if err := os.WriteFile(filepath.Join(dir, name), []byte(content), 0o600); err != nil {
			t.Fatal(err)
		}
	}
	fake := "#!/bin/sh\ncase \"$*\" in\n  *--field-selector*) cat \"$FAKE/backup\" ;;\n  *--selector*) cat \"$FAKE/labelled\" ;;\nesac\n"
	if err := os.WriteFile(filepath.Join(dir, "kubectl"), []byte(fake), 0o700); err != nil {
		t.Fatal(err)
	}
	file := filepath.Join(dir, "export", "root-key.yaml")
	command := exec.CommandContext(t.Context(), "bash", rootKeyScript, "export", file)
	command.Env = append(os.Environ(), "PATH="+dir+string(os.PathListSeparator)+os.Getenv("PATH"), "FAKE="+dir)
	out, err := command.CombinedOutput()
	if err != nil {
		return string(out), err
	}
	content, readErr := os.ReadFile(file)
	if readErr != nil {
		t.Fatal(readErr)
	}
	return string(content), nil
}

var essentials = []string{"Secret/ogcloud-infisical-root", "ConfigMap/ogcloud-infisical-state", "Secret/ogcloud-infisical-break-glass"}

func TestTheRootKeyExportHoldsWhatARestoreNeeds(t *testing.T) {
	content, err := exportRootKey(t, exported(essentials...), exported("Secret/ogcloud-backup-s3"))
	if err != nil {
		t.Fatalf("the export failed: %v\n%s", err, content)
	}
	for _, name := range []string{"ogcloud-infisical-root", "ogcloud-infisical-break-glass", "ogcloud-backup-s3"} {
		if !strings.Contains(content, "  name: "+name+"\n") {
			t.Errorf("the export lacks %s", name)
		}
	}
}

// The backup credentials alone are no export: without Infisical's root key a restore cannot decrypt anything.
func TestTheRootKeyExportFailsWithoutItsEssentials(t *testing.T) {
	tests := map[string]string{
		"nothing labelled":       "",
		"no Infisical root key":  exported(essentials[1:]...),
		"no break-glass login":   exported(essentials[:2]...),
		"no bootstrap state map": exported(essentials[0], essentials[2]),
	}
	for name, labelled := range tests {
		t.Run(name, func(t *testing.T) {
			if out, err := exportRootKey(t, labelled, exported("Secret/ogcloud-backup-s3")); err == nil {
				t.Errorf("the export passed:\n%s", out)
			}
		})
	}
}
