package main

import (
	"context"
	"encoding/json"
	"fmt"
	"io"
	"os"
	"path/filepath"
	"strings"
	"sync"
	"time"

	"go.uber.org/zap"

	"github.com/ogwars/ogcloud-template-loader/internal/extractor"
	minioclient "github.com/ogwars/ogcloud-template-loader/internal/minio"
	"github.com/ogwars/ogcloud-template-loader/internal/runtimeconfig"
)

const (
	defaultDataDir         = "/data"
	downloadTimeoutSeconds = 300
	pushTimeoutSeconds     = 300
	modePull               = "pull"
	modePush               = "push"
)

func main() {
	logger, _ := zap.NewProduction()
	defer logger.Sync()

	mode := getEnvOrDefault("MODE", modePull)

	switch mode {
	case modePull:
		runPullMode(logger)
	case modePush:
		runPushMode(logger)
	default:
		logger.Fatal("unknown MODE", zap.String("mode", mode))
	}
}

func runPullMode(logger *zap.Logger) {
	endpoint := requireEnv("MINIO_ENDPOINT", logger)
	accessKey := requireEnv("MINIO_ACCESS_KEY", logger)
	secretKey := requireEnv("MINIO_SECRET_KEY", logger)
	bucket := requireEnv("MINIO_BUCKET", logger)
	templatePath := requireEnv("TEMPLATE_PATH", logger)
	runtimeBucket := getEnvOrDefault("RUNTIME_BUCKET", "")
	runtimeManifestPath := getEnvOrDefault("RUNTIME_MANIFEST_PATH", "")
	dataDir := getEnvOrDefault("DATA_DIR", defaultDataDir)

	ctx, cancel := signalContext(context.Background())
	defer cancel()

	ctx, timeoutCancel := context.WithTimeout(ctx, downloadTimeoutSeconds*time.Second)
	defer timeoutCancel()

	logger.Info("starting template download",
		zap.String("bucket", bucket),
		zap.String("path", templatePath),
		zap.String("dataDir", dataDir),
	)

	client, err := minioclient.NewClient(endpoint, accessKey, secretKey)
	if err != nil {
		logger.Fatal("failed to create minio client", zap.Error(err))
	}

	archivePath, err := client.DownloadTemplate(ctx, bucket, templatePath)
	if err != nil {
		logger.Fatal("failed to download template", zap.Error(err))
	}

	if err := extractor.ExtractTarGz(archivePath, dataDir); err != nil {
		logger.Fatal("failed to extract template", zap.Error(err))
	}

	if err := os.Remove(archivePath); err != nil {
		logger.Warn("failed to remove downloaded archive", zap.String("path", archivePath), zap.Error(err))
	}

	if runtimeBucket != "" && runtimeManifestPath != "" {
		scopePrefix, err := loadRuntimeAssets(ctx, logger, client, runtimeBucket, runtimeManifestPath, dataDir)
		if err != nil {
			logger.Fatal("failed to load runtime assets", zap.Error(err))
		}
		if err := runtimeconfig.Apply(scopePrefix, dataDir, requireEnv("FORWARDING_SECRET", logger)); err != nil {
			logger.Fatal("failed to apply managed runtime config", zap.Error(err))
		}
	}

	logger.Info("template loaded successfully", zap.String("dataDir", dataDir))
}

func runPushMode(logger *zap.Logger) {
	endpoint := requireEnv("MINIO_ENDPOINT", logger)
	accessKey := requireEnv("MINIO_ACCESS_KEY", logger)
	secretKey := requireEnv("MINIO_SECRET_KEY", logger)
	bucket := requireEnv("MINIO_BUCKET", logger)
	templatePath := requireEnv("TEMPLATE_PATH", logger)
	dataDir := getEnvOrDefault("DATA_DIR", defaultDataDir)
	pushOnShutdown := getEnvOrDefault("PUSH_ON_SHUTDOWN", "false") == "true"

	client, err := minioclient.NewClient(endpoint, accessKey, secretKey)
	if err != nil {
		logger.Fatal("failed to create minio client", zap.Error(err))
	}

	var pushing sync.Mutex

	pushTemplate := func(reason string) {
		if !pushing.TryLock() {
			logger.Warn("push already in progress, skipping", zap.String("reason", reason))
			return
		}
		defer pushing.Unlock()

		logger.Info("starting template push", zap.String("reason", reason))

		tmpFile, err := os.CreateTemp("", "template-push-*.tar.gz")
		if err != nil {
			logger.Error("failed to create temp file for push", zap.Error(err))
			return
		}
		tmpPath := tmpFile.Name()
		tmpFile.Close()
		defer os.Remove(tmpPath)

		if err := extractor.CreateTarGz(dataDir, tmpPath); err != nil {
			logger.Error("failed to create tar.gz for push", zap.Error(err))
			return
		}

		ctx, cancel := context.WithTimeout(context.Background(), pushTimeoutSeconds*time.Second)
		defer cancel()

		if err := client.UploadTemplate(ctx, bucket, templatePath, tmpPath); err != nil {
			logger.Error("failed to upload template", zap.Error(err))
			return
		}

		logger.Info("template pushed successfully", zap.String("reason", reason))
	}

	sigCh := make(chan os.Signal, 1)
	notifyTemplateSignals(sigCh)

	logger.Info("template-pusher sidecar started, waiting for signals",
		zap.String("dataDir", dataDir),
		zap.String("bucket", bucket),
		zap.String("templatePath", templatePath),
		zap.Bool("pushOnShutdown", pushOnShutdown),
	)

	for sig := range sigCh {
		if isForcePushSignal(sig) {
			pushTemplate("force-push")
			continue
		}

		if pushOnShutdown {
			pushTemplate("shutdown")
		}
		logger.Info("template-pusher exiting")
		return
	}
}

func requireEnv(key string, logger *zap.Logger) string {
	val := os.Getenv(key)
	if val == "" {
		logger.Fatal("required environment variable not set", zap.String("key", key))
	}
	return val
}

func getEnvOrDefault(key, defaultVal string) string {
	if val := os.Getenv(key); val != "" {
		return val
	}
	return defaultVal
}

type runtimeManifest struct {
	Artifacts []runtimeManifestArtifact `json:"artifacts"`
}

type runtimeManifestArtifact struct {
	ObjectKey string `json:"objectKey"`
}

func loadRuntimeAssets(
	ctx context.Context,
	logger *zap.Logger,
	client *minioclient.Client,
	bucket string,
	manifestPath string,
	dataDir string,
) (string, error) {
	manifestArchive, err := client.DownloadObject(ctx, bucket, manifestPath)
	if err != nil {
		return "", err
	}
	defer os.Remove(manifestArchive)

	manifestBytes, err := os.ReadFile(manifestArchive)
	if err != nil {
		return "", err
	}

	var manifest runtimeManifest
	if err := json.Unmarshal(manifestBytes, &manifest); err != nil {
		return "", err
	}

	scopePrefix := strings.TrimSuffix(manifestPath, "/manifest.json")

	for _, artifact := range manifest.Artifacts {
		relativePath := strings.TrimPrefix(artifact.ObjectKey, scopePrefix+"/")
		if relativePath == artifact.ObjectKey || relativePath == "" {
			return "", fmt.Errorf("runtime object is outside manifest scope: objectKey=%s scopePrefix=%s", artifact.ObjectKey, scopePrefix)
		}

		tmpFile, err := client.DownloadObject(ctx, bucket, artifact.ObjectKey)
		if err != nil {
			return "", err
		}

		targetPath := filepath.Join(dataDir, filepath.FromSlash(relativePath))
		if err := writeRuntimeAsset(tmpFile, targetPath); err != nil {
			os.Remove(tmpFile)
			return "", err
		}

		if err := os.Remove(tmpFile); err != nil {
			logger.Warn("failed to remove runtime temp file", zap.String("path", tmpFile), zap.Error(err))
		}
	}

	logger.Info(
		"runtime assets loaded successfully",
		zap.String("bucket", bucket),
		zap.String("manifestPath", manifestPath),
		zap.Int("artifactCount", len(manifest.Artifacts)),
	)

	return scopePrefix, nil
}

func writeRuntimeAsset(
	sourcePath string,
	targetPath string,
) error {
	sourceFile, err := os.Open(sourcePath)
	if err != nil {
		return err
	}
	defer sourceFile.Close()

	if err := os.MkdirAll(filepath.Dir(targetPath), 0755); err != nil {
		return err
	}

	targetFile, err := os.OpenFile(targetPath, os.O_CREATE|os.O_WRONLY|os.O_TRUNC, 0644)
	if err != nil {
		return err
	}

	if _, err := io.Copy(targetFile, sourceFile); err != nil {
		targetFile.Close()
		return err
	}

	return targetFile.Close()
}
