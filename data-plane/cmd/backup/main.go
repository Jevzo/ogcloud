// Command backup keeps the template archives in the backup target (D-0166): `templates` copies new ones and prunes
// old copies of deleted ones (a daily CronJob), `restore-templates` copies them back after a restore.
package main

import (
	"context"
	"log/slog"
	"os"
	"os/signal"
	"syscall"
	"time"

	"github.com/jevzo/ogcloud/data-plane/backup"
	"github.com/jevzo/ogcloud/data-plane/pkg/command"
	"github.com/jevzo/ogcloud/data-plane/pkg/config"
	"github.com/jevzo/ogcloud/data-plane/pkg/logging"
	"github.com/jevzo/ogcloud/data-plane/pkg/objects"
)

const (
	component = "backup"
	// D-0043's backup retention.
	defaultRetentionDays = 14
	runTimeout           = 2 * time.Hour
)

func main() {
	command.Main(component, run)
}

func run(args []string) error {
	subcommand, err := command.Subcommand(component, args, "templates", "restore-templates")
	if err != nil {
		return err
	}
	env := config.FromEnvironment()
	data := backup.NewBucket(objects.FromConfig(env))
	target := backup.NewBucket(objects.FromConfigPrefix(env, "OGCLOUD_BACKUP_S3"))
	retention := time.Duration(env.Int("OGCLOUD_BACKUP_RETENTION_DAYS", defaultRetentionDays)) * 24 * time.Hour
	timeout := env.Duration("OGCLOUD_BACKUP_TIMEOUT", runTimeout)
	logLevel := env.String("OGCLOUD_LOG_LEVEL", "info")
	if err := env.Err(); err != nil {
		return err
	}
	logger, err := logging.New(os.Stdout, component, logLevel)
	if err != nil {
		return err
	}
	ctx, stop := signal.NotifyContext(context.Background(), syscall.SIGTERM, os.Interrupt)
	defer stop()
	ctx, cancel := context.WithTimeout(ctx, timeout)
	defer cancel()

	var result backup.Result
	if subcommand == "templates" {
		result, err = backup.BackUp(ctx, data, target, time.Now(), retention)
	} else {
		result, err = backup.Restore(ctx, target, data)
	}
	if err != nil {
		return err
	}
	logger.Info("templates "+subcommand+" done", slog.Int("copied", result.Copied), slog.Int("pruned", result.Pruned))
	return nil
}
