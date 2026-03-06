//go:build !unix

package main

import (
	"context"
	"os"
	"os/signal"
)

func signalContext(parent context.Context) (context.Context, context.CancelFunc) {
	return signal.NotifyContext(parent, os.Interrupt)
}

func notifyTemplateSignals(ch chan<- os.Signal) {
	signal.Notify(ch, os.Interrupt)
}

func isForcePushSignal(_ os.Signal) bool {
	return false
}
