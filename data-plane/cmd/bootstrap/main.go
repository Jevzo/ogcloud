// Command bootstrap prepares secrets at install and upgrade time, driven by the chart's config (D-0044,
// D-0114):
//
//	bootstrap root -config /etc/ogcloud/bootstrap.yaml       (pre-install and pre-upgrade hook: the secrets root)
//	bootstrap infisical -config /etc/ogcloud/bootstrap.yaml  (post-install and post-upgrade Job: Infisical setup)
package main

import (
	"context"
	"crypto/rand"
	"errors"
	"flag"
	"fmt"
	"os"
	"os/signal"
	"syscall"
	"time"

	"github.com/jevzo/ogcloud/data-plane/bootstrap"
	"github.com/jevzo/ogcloud/data-plane/bootstrap/infisical"
	"github.com/jevzo/ogcloud/data-plane/pkg/command"
	"github.com/jevzo/ogcloud/data-plane/pkg/config"
	"github.com/jevzo/ogcloud/data-plane/pkg/kube"
	"github.com/jevzo/ogcloud/data-plane/pkg/logging"
	"github.com/jevzo/ogcloud/data-plane/pkg/tlsfiles"
)

// Each step after Infisical's one-time bootstrap is retried for about a minute.
const (
	stepAttempts = 7
	stepPause    = 10 * time.Second
)

func main() {
	command.Main("bootstrap", run)
}

func run(args []string) error {
	if len(args) == 0 {
		return errors.New("usage: bootstrap <root|infisical> -config <file>")
	}
	flags := flag.NewFlagSet(args[0], flag.ContinueOnError)
	configPath := flags.String("config", "/etc/ogcloud/bootstrap.yaml", "bootstrap config from the chart")
	if err := flags.Parse(args[1:]); err != nil {
		return fmt.Errorf("parse flags: %w", err)
	}
	env := config.FromEnvironment()
	logger, err := logging.New(os.Stdout, "bootstrap", env.String("OGCLOUD_LOG_LEVEL", "info"))
	if err != nil {
		return err
	}
	cfg, err := bootstrap.LoadConfig(*configPath)
	if err != nil {
		return err
	}
	client, err := kube.NewClient(env.String("KUBECONFIG", ""))
	if err != nil {
		return err
	}
	ctx, stop := signal.NotifyContext(context.Background(), syscall.SIGTERM, os.Interrupt)
	defer stop()

	switch args[0] {
	case "root":
		return bootstrap.EnsureRootSecrets(ctx, client, cfg, rand.Reader)
	case "infisical":
		if err := cfg.ValidateInfisical(); err != nil {
			return err
		}
		api := infisical.New(cfg.Infisical.URL, nil)
		if cfg.Infisical.CAFile != "" {
			tlsConfig, err := tlsfiles.Client(cfg.Infisical.CAFile, "")
			if err != nil {
				return err
			}
			api = infisical.New(cfg.Infisical.URL, tlsConfig)
		}
		setup := &bootstrap.Setup{
			Kube: client, API: api, Config: cfg, ReadFile: os.ReadFile, Random: rand.Reader, Logger: logger,
			Attempts: stepAttempts, Pause: stepPause,
		}
		return setup.Run(ctx)
	default:
		return fmt.Errorf("unknown stage %q", args[0])
	}
}
