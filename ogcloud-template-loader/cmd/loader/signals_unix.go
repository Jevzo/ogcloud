//go:build unix

package main

import (
	"context"
	"os"
	"os/signal"
	"syscall"
)

func signalContext(parent context.Context) (context.Context, context.CancelFunc) {
	return signal.NotifyContext(parent, os.Interrupt, syscall.SIGTERM)
}

func notifyTemplateSignals(ch chan<- os.Signal) {
	signal.Notify(ch, os.Interrupt, syscall.SIGTERM, syscall.SIGUSR1)
}

func isForcePushSignal(sig os.Signal) bool {
	return sig == syscall.SIGUSR1
}
