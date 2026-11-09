package deploytest

import (
	"os"
	"path/filepath"
	"strings"
	"testing"
	"text/template"

	"github.com/moby/moby/api/types/container"
	"github.com/testcontainers/testcontainers-go"
)

// Helpers that run the chart's containers in Docker as Kubernetes would.

func firstContainer(t *testing.T, workload object) any {
	t.Helper()
	containers, _ := get(workload.podSpec(), "containers").([]any)
	if len(containers) == 0 {
		t.Fatalf("%s %s has no containers", workload.kind(), workload.name())
	}
	return containers[0]
}

// podEnv is a container's environment as Kubernetes builds it: secretKeyRef values come from secrets (by Secret,
// then key), $(VAR) refers to an earlier variable, and values Kubernetes fills in itself (fieldRef) are left out.
func podEnv(t *testing.T, container any, secrets map[string]map[string]string) map[string]string {
	t.Helper()
	env := map[string]string{}
	variables, _ := get(container, "env").([]any)
	for _, variable := range variables {
		name := str(variable, "name")
		switch {
		case get(variable, "value") != nil:
			env[name] = os.Expand(strings.NewReplacer("$(", "${", ")", "}").Replace(str(variable, "value")), func(ref string) string {
				value, ok := env[ref]
				if !ok {
					t.Fatalf("%s refers to %s, which is not defined before it", name, ref)
				}
				return value
			})
		case get(variable, "valueFrom", "secretKeyRef") != nil:
			secret, key := str(variable, "valueFrom", "secretKeyRef", "name"), str(variable, "valueFrom", "secretKeyRef", "key")
			value, ok := secrets[secret][key]
			if !ok {
				t.Fatalf("%s reads %s/%s, which the test does not provide", name, secret, key)
			}
			env[name] = value
		}
	}
	return env
}

// certificateNames are the DNS names of the rendered cert-manager Certificate name.
func certificateNames(t *testing.T, objects rendered, name string) []string {
	t.Helper()
	return stringList(get(objects.find(t, "Certificate", name), "spec", "dnsNames"))
}

func stringList(value any) []string {
	items, _ := value.([]any)
	var list []string
	for _, item := range items {
		s, _ := item.(string)
		list = append(list, s)
	}
	return list
}

// syncedTemplate is what the Secrets Operator writes into key of the synced Secret secret, given Infisical's
// secrets: its template engine is Go's text/template over them.
func syncedTemplate(t *testing.T, objects rendered, secret, key string, secrets map[string]string) string {
	t.Helper()
	targets, _ := get(objects.find(t, "InfisicalStaticSecret", secret), "spec", "targets").([]any)
	if len(targets) != 1 {
		t.Fatalf("%s syncs %d targets, want 1", secret, len(targets))
	}
	values := map[string]any{}
	for name, value := range secrets {
		values[name] = map[string]string{"Value": value}
	}
	var out strings.Builder
	source := str(targets[0], "template", "data", key)
	if err := template.Must(template.New(key).Parse(source)).Execute(&out, values); err != nil {
		t.Fatalf("%s %s template: %v", secret, key, err)
	}
	return out.String()
}

// mounted are files a container reads, by path, as Kubernetes mounts Secrets and ConfigMaps.
type mounted map[string]mountedFile

type mountedFile struct {
	content    string
	executable bool
}

// mounts holds mounted files on the host, by container path.
type mounts struct {
	t    *testing.T
	host map[string]string
}

func mount(t *testing.T, files mounted) mounts {
	t.Helper()
	dir := t.TempDir()
	m := mounts{t: t, host: map[string]string{}}
	for path, file := range files {
		m.host[path] = filepath.Join(dir, strings.ReplaceAll(strings.TrimPrefix(path, "/"), "/", "_"))
		mode := os.FileMode(0o644)
		if file.executable {
			mode = 0o755
		}
		m.write(path, file.content, mode)
	}
	return m
}

// update rewrites a mounted file in place, as kubelet updates a mounted Secret.
func (m mounts) update(path, content string) {
	m.t.Helper()
	m.write(path, content, 0o644)
}

func (m mounts) write(path, content string, mode os.FileMode) {
	m.t.Helper()
	host, ok := m.host[path]
	if !ok {
		m.t.Fatalf("%s is not mounted", path)
	}
	if err := os.WriteFile(host, []byte(content), mode); err != nil {
		m.t.Fatal(err)
	}
	// WriteFile honours the umask; the container's user is not the file's owner.
	if err := os.Chmod(host, mode); err != nil {
		m.t.Fatal(err)
	}
}

// restricted runs a container as the chart's pods run: uid 1000 on a read-only root, the files bind-mounted
// read-only (Docker copies nothing onto a read-only root) and tmpfs at writable. It keeps modifiers set before it.
func (m mounts) restricted(writable ...string) testcontainers.CustomizeRequestOption {
	var binds []string
	for path, host := range m.host {
		binds = append(binds, host+":"+path+":ro")
	}
	return func(request *testcontainers.GenericContainerRequest) error {
		config, host := request.ConfigModifier, request.HostConfigModifier
		request.ConfigModifier = func(c *container.Config) {
			if config != nil {
				config(c)
			}
			c.User = "1000:1000"
		}
		request.HostConfigModifier = func(h *container.HostConfig) {
			if host != nil {
				host(h)
			}
			h.ReadonlyRootfs = true
			h.Binds = append(h.Binds, binds...)
			h.Tmpfs = map[string]string{}
			for _, path := range writable {
				h.Tmpfs[path] = "uid=1000"
			}
		}
		return nil
	}
}
