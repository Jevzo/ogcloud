// Package command runs the one-shot commands (bootstrap, template-loader, backup), as package service runs the
// long-running ones.
package command

import (
	"fmt"
	"os"
	"slices"
	"strings"
)

// Main runs run with the command's arguments; an error ends the process with "<component>: <error>" and status 1.
func Main(component string, run func(args []string) error) {
	if err := run(os.Args[1:]); err != nil {
		fmt.Fprintln(os.Stderr, component+":", err)
		os.Exit(1)
	}
}

// Subcommand is args' only argument when it names one of subcommands.
func Subcommand(component string, args []string, subcommands ...string) (string, error) {
	if len(args) != 1 || !slices.Contains(subcommands, args[0]) {
		return "", fmt.Errorf("usage: %s <%s>", component, strings.Join(subcommands, "|"))
	}
	return args[0], nil
}
