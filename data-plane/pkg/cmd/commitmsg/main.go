// Command commitmsg checks a commit message file (git commit-msg hook) or stdin ("-").
package main

import (
	"fmt"
	"io"
	"os"

	"github.com/jevzo/ogcloud/data-plane/pkg/commitmsg"
)

func main() {
	if err := run(os.Args[1:]); err != nil {
		fmt.Fprintln(os.Stderr, "commit message rejected:\n", err)
		os.Exit(1)
	}
}

func run(args []string) error {
	if len(args) != 1 {
		return fmt.Errorf("usage: commitmsg <message-file|->")
	}
	message, err := read(args[0])
	if err != nil {
		return err
	}
	return commitmsg.Validate(string(message))
}

func read(path string) ([]byte, error) {
	if path == "-" {
		data, err := io.ReadAll(os.Stdin)
		if err != nil {
			return nil, fmt.Errorf("read stdin: %w", err)
		}
		return data, nil
	}
	data, err := os.ReadFile(path) //nolint:gosec // G304: git passes the path of its own message file.
	if err != nil {
		return nil, fmt.Errorf("read %s: %w", path, err)
	}
	return data, nil
}
