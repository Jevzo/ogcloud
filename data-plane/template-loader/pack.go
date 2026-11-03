package templateloader

import (
	"archive/tar"
	"compress/gzip"
	"errors"
	"fmt"
	"io"
	"io/fs"
	"os"
	"path"
	"slices"
	"strings"
)

// Never pushed: the runtime bundle comes from the controller, the rest is the server's own churn (D-0119).
var (
	excludedDirs  = []string{stateDir, "logs", "crash-reports", "cache", "libraries", "versions", "plugins/.paper-remapped"}
	excludedNames = []string{"session.lock"}
)

// excluder decides what a push leaves out; bundle holds the runtime-bundle paths the loader installed.
type excluder struct{ bundle []string }

func newExcluder(root *os.Root) (excluder, error) {
	list, err := root.ReadFile(bundleList)
	if errors.Is(err, fs.ErrNotExist) {
		return excluder{}, nil
	}
	if err != nil {
		return excluder{}, fmt.Errorf("read bundle list: %w", err)
	}
	return excluder{bundle: strings.Fields(string(list))}, nil
}

func (e excluder) excludes(name string, dir bool) bool {
	if dir {
		return slices.Contains(excludedDirs, name)
	}
	return slices.Contains(excludedNames, path.Base(name)) || slices.Contains(e.bundle, name)
}

// pack writes everything in root that is not excluded as a tar.gz archive to out. Links are skipped: a push
// carries files, not pointers to elsewhere.
// It returns the paths it left out because they were links or special files.
func pack(root *os.Root, out io.Writer, skip excluder) ([]string, error) {
	zipped := gzip.NewWriter(out)
	archive := tar.NewWriter(zipped)
	var skipped []string
	err := fs.WalkDir(root.FS(), ".", func(name string, entry fs.DirEntry, err error) error {
		if err != nil {
			return err
		}
		if name == "." {
			return nil
		}
		if skip.excludes(name, entry.IsDir()) {
			if entry.IsDir() {
				return fs.SkipDir
			}
			return nil
		}
		if !entry.IsDir() && !entry.Type().IsRegular() {
			skipped = append(skipped, name)
			return nil
		}
		return addEntry(root, archive, name, entry)
	})
	if err != nil {
		return nil, fmt.Errorf("pack: %w", err)
	}
	if err := archive.Close(); err != nil {
		return nil, fmt.Errorf("pack: %w", err)
	}
	if err := zipped.Close(); err != nil {
		return nil, fmt.Errorf("pack: %w", err)
	}
	return skipped, nil
}

func addEntry(root *os.Root, archive *tar.Writer, name string, entry fs.DirEntry) error {
	info, err := entry.Info()
	if err != nil {
		return err //nolint:wrapcheck // WalkDir's caller wraps it.
	}
	header, err := tar.FileInfoHeader(info, "")
	if err != nil {
		return fmt.Errorf("%s: %w", name, err)
	}
	header.Name = name
	if entry.IsDir() {
		header.Name += "/"
	}
	if err := archive.WriteHeader(header); err != nil {
		return fmt.Errorf("%s: %w", name, err)
	}
	if entry.IsDir() {
		return nil
	}
	file, err := root.Open(name)
	if err != nil {
		return fmt.Errorf("%s: %w", name, err)
	}
	defer func() { _ = file.Close() }()
	// A file the server grows meanwhile is cut at the size in the header.
	if _, err := io.CopyN(archive, file, header.Size); err != nil {
		return fmt.Errorf("%s: %w", name, err)
	}
	return nil
}
