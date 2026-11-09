package deploytest

import (
	"os"
	"path/filepath"
	"slices"
	"strconv"
	"strings"
	"testing"
)

// PostgreSQL archives its WAL and takes daily base backups into the backup target through the Barman Cloud plugin,
// with the seeded credentials and the chart's retention (D-0166, D-0043).
func TestPostgresBacksUpIntoTheBackupTarget(t *testing.T) {
	objects := render(t, hetznerValues)
	store := objects.find(t, "ObjectStore", "ogcloud-backups")
	if got := str(store, "spec", "configuration", "destinationPath"); got != "s3://ogcloud-backups/postgres" {
		t.Errorf("PostgreSQL backs up to %q", got)
	}
	if got := str(store, "spec", "retentionPolicy"); got != "14d" {
		t.Errorf("backups are kept for %q, want D-0043's 14d", got)
	}

	credentials := str(store, "spec", "configuration", "s3Credentials", "accessKeyId", "name")
	targets, _ := get(objects.find(t, "InfisicalStaticSecret", credentials), "spec", "targets").([]any)
	if len(targets) != 1 {
		t.Fatalf("the backup credentials go to %d Secrets, want 1", len(targets))
	}
	for key, source := range map[string]string{"ACCESS_KEY_ID": "BACKUP_S3_ACCESS_KEY", "ACCESS_SECRET_KEY": "BACKUP_S3_SECRET_KEY"} {
		if got := str(targets[0], "template", "data", key); !strings.Contains(got, "."+source+".") {
			t.Errorf("%s holds %q, want the seeded %s", key, got, source)
		}
	}

	plugins, _ := get(objects.find(t, "Cluster", "ogcloud-postgres"), "spec", "plugins").([]any)
	archives := slices.ContainsFunc(plugins, func(plugin any) bool {
		return get(plugin, "isWALArchiver") == true && str(plugin, "parameters", "barmanObjectName") == "ogcloud-backups"
	})
	if !archives {
		t.Error("PostgreSQL does not archive its WAL into the backup target")
	}

	schedule := objects.find(t, "ScheduledBackup", "ogcloud-postgres-daily")
	if str(schedule, "spec", "method") != "plugin" || str(schedule, "spec", "cluster", "name") != "ogcloud-postgres" {
		t.Error("the daily backup does not run the plugin on ogcloud-postgres")
	}
	if fields := strings.Fields(str(schedule, "spec", "schedule")); len(fields) != 6 {
		t.Errorf("the schedule has %d fields; CloudNativePG's cron has six, seconds first", len(fields))
	}
}

func TestBackupsStayOffUnlessEnabled(t *testing.T) {
	objects := render(t, minikubeValues)
	if len(objects.all("ObjectStore"))+len(objects.all("ScheduledBackup")) != 0 {
		t.Error("minikube backs up without backup.enabled")
	}
	if get(objects.find(t, "Cluster", "ogcloud-postgres"), "spec", "plugins") != nil {
		t.Error("PostgreSQL loads the backup plugin without backup.enabled")
	}
}

// The template copy runs daily with both buckets' credentials and the backups' retention in days (D-0166).
func TestTemplatesAreCopiedDaily(t *testing.T) {
	job := render(t, hetznerValues).find(t, "CronJob", "ogcloud-backup-templates")
	container := firstContainer(t, job)
	if args := stringList(get(container, "args")); !slices.Equal(args, []string{"templates"}) {
		t.Errorf("the job runs %v", args)
	}
	if image := str(container, "image"); !strings.Contains(image, "/backup:") {
		t.Errorf("the job runs image %s", image)
	}
	env := podEnv(t, container, nil)
	for name, want := range map[string]string{
		"OGCLOUD_BACKUP_RETENTION_DAYS":     "14",
		"OGCLOUD_BACKUP_S3_BUCKET":          "ogcloud-backups",
		"OGCLOUD_S3_ACCESS_KEY_FILE":        "/var/run/secrets/ogcloud/secrets/s3-access-key",
		"OGCLOUD_BACKUP_S3_SECRET_KEY_FILE": "/var/run/secrets/ogcloud/secrets/backup-secret-key",
	} {
		if env[name] != want {
			t.Errorf("%s = %q, want %q", name, env[name], want)
		}
	}
}

// A restore recovers PostgreSQL from the backup it names, archives anew under its own server name, and copies the
// templates back after bootstrap and the bucket (T-0607).
func TestARestoreRecoversPostgresAndThenTheTemplates(t *testing.T) {
	restore := filepath.Join(t.TempDir(), "restore.yaml")
	if err := os.WriteFile(restore, []byte("restore: {fromServer: ogcloud-postgres}\nbackup: {serverName: ogcloud-postgres-2}\n"), 0o600); err != nil {
		t.Fatal(err)
	}
	objects := render(t, hetznerValues, restore)
	cluster := objects.find(t, "Cluster", "ogcloud-postgres")
	if got := str(cluster, "spec", "bootstrap", "recovery", "source"); got != "backup" {
		t.Fatalf("PostgreSQL bootstraps from %q, want the backup", got)
	}
	if get(cluster, "spec", "bootstrap", "initdb") != nil {
		t.Error("a restored PostgreSQL still runs initdb")
	}
	sources, _ := get(cluster, "spec", "externalClusters").([]any)
	if len(sources) != 1 || str(sources[0], "plugin", "parameters", "serverName") != "ogcloud-postgres" {
		t.Errorf("the restore reads %v, want the backup archived under ogcloud-postgres", sources)
	}
	plugins, _ := get(cluster, "spec", "plugins").([]any)
	if len(plugins) != 1 || str(plugins[0], "parameters", "serverName") != "ogcloud-postgres-2" {
		t.Errorf("the restored cluster archives as %v, want ogcloud-postgres-2", plugins)
	}

	job := objects.find(t, "Job", "ogcloud-restore-templates")
	if args := stringList(get(firstContainer(t, job), "args")); !slices.Equal(args, []string{"restore-templates"}) {
		t.Errorf("the restore job runs %v", args)
	}
	weight := func(o object) int {
		w, _ := strconv.Atoi(str(o, "metadata", "annotations", "helm.sh/hook-weight"))
		return w
	}
	if weight(job) <= weight(objects.find(t, "Job", "ogcloud-bootstrap-infisical")) {
		t.Error("templates are restored before bootstrap has synced the S3 credentials")
	}
}

func TestARestoreNeedsTheBackupTargetAndANewServerName(t *testing.T) {
	refused := func(values string, want string, args ...string) {
		t.Helper()
		if _, err := helmTemplate(t, []string{values}, args...); err == nil || !strings.Contains(err.Error(), want) {
			t.Errorf("%v rendered with %v, want %q", args, err, want)
		}
	}
	refused(hetznerValues, "archives under a new backup.serverName",
		"--set", "restore.fromServer=ogcloud-postgres", "--set", "backup.serverName=ogcloud-postgres")
	refused(minikubeValues, "set backup.enabled", "--set", "restore.fromServer=ogcloud-postgres")
}
