package deploytest

import (
	"slices"
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
