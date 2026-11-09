package deploytest

import (
	"os"
	"path/filepath"
	"strings"
	"testing"

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

// literalEnv is a container's environment without the values Kubernetes fills in.
func literalEnv(container any) map[string]string {
	env := map[string]string{}
	variables, _ := get(container, "env").([]any)
	for _, variable := range variables {
		if value, ok := get(variable, "value").(string); ok {
			env[str(variable, "name")] = value
		}
	}
	return env
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
