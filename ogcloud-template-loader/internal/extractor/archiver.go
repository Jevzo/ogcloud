package extractor

import (
	"archive/tar"
	"compress/gzip"
	"fmt"
	"io"
	"os"
	"path/filepath"
	"strings"
)

var templateIncludes = []string{
	"config",
	"plugins",
	"world",
	"world_nether",
	"world_the_end",
	"bukkit.yml",
	"eula.txt",
	"server.properties",
	"spigot.yml",
}

var runtimeExcludes = map[string]bool{
	"session.lock": true,
	"uid.dat":      true,
}

var runtimeManagedPluginFiles = map[string]bool{
	"BungeeGuard.jar": true,
	"ProtocolLib.jar": true,
}

func CreateTarGz(sourceDir, destPath string) error {
	outFile, err := os.Create(destPath)
	if err != nil {
		return fmt.Errorf("create archive file: %w", err)
	}
	defer outFile.Close()

	gzWriter := gzip.NewWriter(outFile)
	defer gzWriter.Close()

	tarWriter := tar.NewWriter(gzWriter)
	defer tarWriter.Close()

	for _, include := range templateIncludes {
		fullPath := filepath.Join(sourceDir, include)

		info, err := os.Stat(fullPath)
		if os.IsNotExist(err) {
			continue
		}
		if err != nil {
			return fmt.Errorf("stat %s: %w", include, err)
		}

		if info.IsDir() {
			if err := addDir(tarWriter, sourceDir, fullPath); err != nil {
				return err
			}
		} else {
			if err := addFile(tarWriter, sourceDir, fullPath, info); err != nil {
				return err
			}
		}
	}

	return nil
}

func addDir(tw *tar.Writer, baseDir, dir string) error {
	return filepath.Walk(dir, func(path string, info os.FileInfo, err error) error {
		if err != nil {
			return err
		}

		skip, err := shouldSkipRuntimePath(baseDir, path, info)
		if err != nil {
			return err
		}
		if skip {
			if info.IsDir() {
				return filepath.SkipDir
			}
			return nil
		}

		relPath, err := filepath.Rel(baseDir, path)
		if err != nil {
			return fmt.Errorf("compute relative path: %w", err)
		}

		return writeEntry(tw, path, relPath, info)
	})
}

func addFile(tw *tar.Writer, baseDir, path string, info os.FileInfo) error {
	relPath, err := filepath.Rel(baseDir, path)
	if err != nil {
		return fmt.Errorf("compute relative path: %w", err)
	}
	return writeEntry(tw, path, relPath, info)
}

func writeEntry(tw *tar.Writer, fullPath, relPath string, info os.FileInfo) error {
	header, err := tar.FileInfoHeader(info, "")
	if err != nil {
		return fmt.Errorf("create tar header for %s: %w", relPath, err)
	}
	header.Name = filepath.ToSlash(relPath)

	if err := tw.WriteHeader(header); err != nil {
		return fmt.Errorf("write tar header for %s: %w", relPath, err)
	}

	if info.IsDir() {
		return nil
	}

	file, err := os.Open(fullPath)
	if err != nil {
		return fmt.Errorf("open file %s: %w", fullPath, err)
	}
	defer file.Close()

	if _, err := io.Copy(tw, file); err != nil {
		return fmt.Errorf("write file %s to archive: %w", relPath, err)
	}

	return nil
}

func shouldSkipRuntimePath(
	baseDir string,
	path string,
	info os.FileInfo,
) (bool, error) {
	if runtimeExcludes[info.Name()] {
		return true, nil
	}

	relPath, err := filepath.Rel(baseDir, path)
	if err != nil {
		return false, fmt.Errorf("compute relative path: %w", err)
	}

	normalizedPath := filepath.ToSlash(relPath)
	switch {
	case normalizedPath == "plugins/.paper-remapped":
		return true, nil
	case strings.HasPrefix(normalizedPath, "plugins/.paper-remapped/"):
		return true, nil
	case normalizedPath == "plugins/BungeeGuard":
		return true, nil
	case strings.HasPrefix(normalizedPath, "plugins/BungeeGuard/"):
		return true, nil
	}

	if !strings.HasPrefix(normalizedPath, "plugins/") {
		return false, nil
	}

	if runtimeManagedPluginFiles[info.Name()] {
		return true, nil
	}

	if strings.HasPrefix(info.Name(), "ogcloud-paper-plugin") && strings.HasSuffix(info.Name(), ".jar") {
		return true, nil
	}

	return false, nil
}
