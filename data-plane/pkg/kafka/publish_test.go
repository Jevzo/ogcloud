package kafka

import (
	"bytes"
	"context"
	"log/slog"
	"strings"
	"testing"
	"time"

	"github.com/twmb/franz-go/pkg/kgo"
)

func TestPublishesGiveUpWhenNoBrokerAnswers(t *testing.T) {
	// Port 1 is closed; without a deadline franz-go would retry for as long as ctx lives (D-0121).
	producer, err := NewProducer(Settings{Brokers: []string{"127.0.0.1:1"}, ClientID: "gateway"})
	if err != nil {
		t.Fatal(err)
	}
	defer producer.Close()
	started := time.Now()

	err = producer.Publish(context.Background(), "ogcloud.test", "k", command("lost"))

	if err == nil || time.Since(started) > 2*publishTimeout {
		t.Errorf("Publish = %v after %v", err, time.Since(started))
	}
}

func TestClientLogsReachSlogAtTheirSeverity(t *testing.T) {
	var out bytes.Buffer
	logger := clientLogger{slog.New(slog.NewTextHandler(&out, &slog.HandlerOptions{Level: slog.LevelDebug}))}

	logger.Log(kgo.LogLevelError, "SASL refused", "broker", "b-1")
	logger.Log(kgo.LogLevelWarn, "metadata stale")

	if !strings.Contains(out.String(), `level=ERROR msg="SASL refused" broker=b-1`) || !strings.Contains(out.String(), "level=WARN") {
		t.Errorf("logged %q", out.String())
	}
	if logger.Level() != kgo.LogLevelWarn {
		t.Error("franz-go's chatty info logs should stay off")
	}
}
