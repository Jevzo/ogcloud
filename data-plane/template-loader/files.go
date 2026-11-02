// Package templateloader fills a game pod's data directory before the server starts (load) and pushes the
// server's files back as a new template version (agent). Every file operation goes through an os.Root, so
// nothing an archive or the server wrote can reach outside the data directory.
package templateloader

import (
	"archive/tar"
	"compress/gzip"
	"crypto/sha256"
	"encoding/hex"
	"errors"
	"fmt"
	"hash"
	"io"
	"io/fs"
	"os"
	"path"
)

const (
	dirMode  = 0o755
	fileMode = 0o644
	// Everything OgCloud keeps in the data directory for itself lives here.
	stateDir = ".ogcloud"
)

// ErrUnsafe marks archives and paths that would leave the data directory or hold links; retrying cannot help.
var ErrUnsafe = errors.New("unsafe archive entry")

// extract unpacks a tar.gz archive into root. Links and device files are refused, and at most maxBytes
// of file content are written.
func extract(root *os.Root, archive io.Reader, maxBytes int64) error {
	unzipped, err := gzip.NewReader(archive)
	if err != nil {
		return fmt.Errorf("open gzip: %w", err)
	}
	defer func() { _ = unzipped.Close() }()
	entries := tar.NewReader(unzipped)
	written := int64(0)
	for {
		header, err := entries.Next()
		if errors.Is(err, io.EOF) {
			return nil
		}
		if err != nil {
			return fmt.Errorf("read archive: %w", err)
		}
		name := path.Clean(header.Name)
		if !fs.ValidPath(name) || name == "." && header.Typeflag != tar.TypeDir {
			return fmt.Errorf("%w: %q", ErrUnsafe, header.Name)
		}
		switch header.Typeflag {
		case tar.TypeDir:
			if err := root.MkdirAll(name, dirMode); err != nil {
				return fmt.Errorf("create %s: %w", name, err)
			}
		case tar.TypeReg:
			written += header.Size
			if written > maxBytes {
				return fmt.Errorf("%w: more than %d bytes", ErrUnsafe, maxBytes)
			}
			if err := writeFile(root, name, io.LimitReader(entries, header.Size)); err != nil {
				return err
			}
		default:
			return fmt.Errorf("%w: %q is not a file or directory", ErrUnsafe, header.Name)
		}
	}
}

// writeFile replaces name with content, creating its directory.
func writeFile(root *os.Root, name string, content io.Reader) error {
	if dir := path.Dir(name); dir != "." {
		if err := root.MkdirAll(dir, dirMode); err != nil {
			return fmt.Errorf("create %s: %w", dir, err)
		}
	}
	file, err := root.OpenFile(name, os.O_CREATE|os.O_TRUNC|os.O_WRONLY, fileMode)
	if err != nil {
		return fmt.Errorf("create %s: %w", name, err)
	}
	if _, err := io.Copy(file, content); err != nil {
		_ = file.Close()
		return fmt.Errorf("write %s: %w", name, err)
	}
	if err := file.Close(); err != nil {
		return fmt.Errorf("write %s: %w", name, err)
	}
	return nil
}

// wipe removes everything in root.
func wipe(root *os.Root) error {
	entries, err := fs.ReadDir(root.FS(), ".")
	if err != nil {
		return fmt.Errorf("list data: %w", err)
	}
	for _, entry := range entries {
		if err := root.RemoveAll(entry.Name()); err != nil {
			return fmt.Errorf("remove %s: %w", entry.Name(), err)
		}
	}
	return nil
}

// digest counts and hashes what passes through it.
type digest struct {
	hash hash.Hash
	size int64
}

func newDigest() *digest { return &digest{hash: sha256.New()} }

func (d *digest) Write(p []byte) (int, error) {
	d.size += int64(len(p))
	return d.hash.Write(p) //nolint:wrapcheck // hashes never fail.
}

func (d *digest) sum() string { return hex.EncodeToString(d.hash.Sum(nil)) }

// check compares what passed through with what the gateway announced.
func (d *digest) check(sha256 string, size uint64) error {
	if d.sum() != sha256 || uint64(d.size) != size { //nolint:gosec // G115: sizes are never negative.
		return fmt.Errorf("checksum mismatch: got %s (%d bytes), want %s (%d bytes)", d.sum(), d.size, sha256, size)
	}
	return nil
}
