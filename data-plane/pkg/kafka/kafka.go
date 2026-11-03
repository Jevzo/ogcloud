// Package kafka connects the data plane to Kafka with franz-go (D-0109): SCRAM-SHA-512 over TLS like the
// control plane (D-0041), an idempotent producer for events, and broadcast consumers that let every
// replica see every record from now on.
package kafka

import (
	"context"
	"crypto/tls"
	"errors"
	"fmt"
	"log/slog"
	"strings"
	"time"

	"github.com/twmb/franz-go/pkg/kgo"
	"github.com/twmb/franz-go/pkg/sasl/scram"
	"google.golang.org/protobuf/proto"
)

const (
	lingerTime = 5 * time.Millisecond
	// A publish fails after this instead of retrying forever, so a Kafka outage never blocks a caller (D-0121).
	publishTimeout = 5 * time.Second
)

// Settings reach the cluster as one service, whose name prefixes the consumer groups its ACLs allow.
type Settings struct {
	Brokers  []string
	ClientID string
	// Empty for clusters without SASL, e.g. in tests.
	Username string
	Password string
	// Nil for plaintext.
	TLS *tls.Config
	// Receives franz-go's warnings and errors, e.g. refused logins or missing ACLs; nil drops them.
	Logger *slog.Logger
}

func (s Settings) options() []kgo.Opt {
	opts := []kgo.Opt{kgo.SeedBrokers(s.Brokers...), kgo.ClientID(s.ClientID)}
	if s.Logger != nil {
		opts = append(opts, kgo.WithLogger(clientLogger{s.Logger.With("component", "franz-go")}))
	}
	if s.Username != "" {
		opts = append(opts, kgo.SASL(scram.Auth{User: s.Username, Pass: s.Password}.AsSha512Mechanism()))
	}
	if s.TLS != nil {
		opts = append(opts, kgo.DialTLSConfig(s.TLS))
	}
	return opts
}

// Producer publishes Protobuf events; franz-go produces idempotently with acknowledgements from every
// in-sync replica.
type Producer struct {
	client *kgo.Client
}

// NewProducer connects lazily; the first publish reaches the brokers.
func NewProducer(s Settings) (*Producer, error) {
	client, err := kgo.NewClient(append(s.options(), kgo.ProducerLinger(lingerTime), kgo.RecordDeliveryTimeout(publishTimeout))...)
	if err != nil {
		return nil, fmt.Errorf("kafka producer: %w", err)
	}
	return &Producer{client: client}, nil
}

// Publish sends event to topic under key and waits until the brokers stored it, at most publishTimeout.
func (p *Producer) Publish(ctx context.Context, topic, key string, event proto.Message) error {
	ctx, cancel := context.WithTimeout(ctx, publishTimeout)
	defer cancel()
	value, err := proto.Marshal(event)
	if err != nil {
		return fmt.Errorf("encode %s for %s: %w", event.ProtoReflect().Descriptor().FullName(), topic, err)
	}
	record := &kgo.Record{Topic: topic, Key: []byte(key), Value: value}
	if err := p.client.ProduceSync(ctx, record).FirstErr(); err != nil {
		return fmt.Errorf("publish %s with key %s to %s: %w", event.ProtoReflect().Descriptor().FullName(), key, topic, err)
	}
	return nil
}

// Close disconnects; Publish is synchronous, so nothing is left buffered.
func (p *Producer) Close() { p.client.Close() }

// Broadcast reads topics with a consumer group of its own that never commits and starts at the end, so a
// replica sees every record from now on. Delivery is at-most-once; callers resync from Valkey.
type Broadcast struct {
	client *kgo.Client
	logger *slog.Logger
}

// NewBroadcast consumes topics in group, which must carry the service's ACL prefix ogcloud-<client id>.
func NewBroadcast(s Settings, logger *slog.Logger, group string, topics ...string) (*Broadcast, error) {
	if prefix := "ogcloud-" + s.ClientID; !strings.HasPrefix(group, prefix) {
		return nil, fmt.Errorf("group %s must start with %s (ACLs)", group, prefix)
	}
	client, err := kgo.NewClient(append(s.options(),
		kgo.ConsumerGroup(group),
		kgo.ConsumeTopics(topics...),
		kgo.ConsumeResetOffset(kgo.NewOffset().AtEnd()),
		kgo.DisableAutoCommit(),
	)...)
	if err != nil {
		return nil, fmt.Errorf("kafka broadcast %s: %w", group, err)
	}
	return &Broadcast{client: client, logger: logger.With("group", group)}, nil
}

// Run hands every record to handle until ctx ends, then leaves the group. Fetch errors are logged and
// retried by franz-go.
func (b *Broadcast) Run(ctx context.Context, handle func(context.Context, *kgo.Record)) error {
	defer b.client.Close()
	for {
		fetches := b.client.PollFetches(ctx)
		if ctx.Err() != nil {
			return nil
		}
		if fetches.IsClientClosed() {
			return errors.New("kafka broadcast: client closed")
		}
		fetches.EachError(func(topic string, partition int32, err error) {
			b.logger.Warn("fetch failed", "topic", topic, "partition", partition, "error", err)
		})
		fetches.EachRecord(func(record *kgo.Record) { handle(ctx, record) })
	}
}

// clientLogger hands franz-go's warnings and errors to slog.
type clientLogger struct{ logger *slog.Logger }

func (l clientLogger) Level() kgo.LogLevel { return kgo.LogLevelWarn }

func (l clientLogger) Log(level kgo.LogLevel, message string, keyvals ...any) {
	severity := slog.LevelWarn
	switch level {
	case kgo.LogLevelError:
		severity = slog.LevelError
	case kgo.LogLevelInfo:
		severity = slog.LevelInfo
	case kgo.LogLevelDebug:
		severity = slog.LevelDebug
	}
	l.logger.Log(context.Background(), severity, message, keyvals...)
}
