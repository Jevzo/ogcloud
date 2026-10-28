package kafka

import (
	"context"
	"fmt"
	"io"
	"log/slog"
	"strings"
	"testing"
	"time"

	"github.com/twmb/franz-go/pkg/kgo"
	"google.golang.org/protobuf/proto"

	"github.com/jevzo/ogcloud/data-plane/internal/testkafka"
	eventsv1 "github.com/jevzo/ogcloud/data-plane/pkg/gen/proto/ogcloud/events/v1"
)

const (
	user     = "ogcloud-test"
	password = "s3cret-password"
	wait     = 30 * time.Second
	refusal  = 5 * time.Second
	retry    = 500 * time.Millisecond
)

func TestBroadcastsSeeNewRecordsOnEveryReplicaButNotOldOnes(t *testing.T) {
	ctx := context.Background()
	broker := testkafka.Start(t, nil)
	settings := Settings{Brokers: broker.Addresses, ClientID: "gateway"}
	producer, err := NewProducer(settings)
	if err != nil {
		t.Fatalf("NewProducer: %v", err)
	}
	defer producer.Close()
	topic := "ogcloud.test.broadcast"
	broker.CreateTopic(t, topic)
	if err := producer.Publish(ctx, topic, "old", command("old")); err != nil {
		t.Fatalf("Publish: %v", err)
	}

	first := listen(t, settings, "ogcloud-gateway-replica-1", topic)
	second := listen(t, settings, "ogcloud-gateway-replica-2", topic)

	// Each replica joins at the latest offset once assigned, so the new record is sent until both see it.
	for _, replica := range []chan string{first, second} {
		if got := await(t, replica, func() { publish(t, producer, topic, "new") }); got != "new" {
			t.Errorf("replica saw %q first, want new", got)
		}
	}
}

func TestGroupsOutsideTheServicePrefixAreRefused(t *testing.T) {
	_, err := NewBroadcast(Settings{Brokers: []string{"localhost:1"}, ClientID: "gateway"}, quiet(), "ogcloud-api-x", "t")
	if err == nil || !strings.Contains(err.Error(), "must start with ogcloud-gateway") {
		t.Errorf("got %v", err)
	}
}

func TestScramCredentialsAreRequired(t *testing.T) {
	// The host-facing listener speaks SASL with SCRAM; the broker and controller listeners stay internal.
	broker := testkafka.Start(t, map[string]string{
		"KAFKA_LISTENER_SECURITY_PROTOCOL_MAP":                             "BROKER:PLAINTEXT,PLAINTEXT:SASL_PLAINTEXT,CONTROLLER:PLAINTEXT",
		"KAFKA_SASL_ENABLED_MECHANISMS":                                    "SCRAM-SHA-512",
		"KAFKA_LISTENER_NAME_PLAINTEXT_SCRAM___SHA___512_SASL_JAAS_CONFIG": "org.apache.kafka.common.security.scram.ScramLoginModule required;",
	})
	// Created over the internal listener, as Strimzi's user operator would.
	broker.Exec(t, "kafka-configs.sh", "--alter", "--add-config", fmt.Sprintf("SCRAM-SHA-512=[password=%s]", password),
		"--entity-type", "users", "--entity-name", user)
	broker.CreateTopic(t, "ogcloud.test.scram")

	for _, tt := range []struct {
		password string
		ok       bool
	}{{password, true}, {"wrong", false}} {
		producer, err := NewProducer(Settings{Brokers: broker.Addresses, ClientID: "gateway", Username: user, Password: tt.password})
		if err != nil {
			t.Fatalf("NewProducer: %v", err)
		}
		// A refused login is retried until the deadline, so the wrong password gets a short one.
		timeout := wait
		if !tt.ok {
			timeout = refusal
		}
		ctx, cancel := context.WithTimeout(context.Background(), timeout)
		err = producer.Publish(ctx, "ogcloud.test.scram", "k", command("k"))
		cancel()
		producer.Close()
		if (err == nil) != tt.ok {
			t.Errorf("password %q: publish error %v", tt.password, err)
		}
	}
}

// listen starts a broadcast in group and returns the keys it receives.
func listen(t *testing.T, settings Settings, group, topic string) chan string {
	t.Helper()
	broadcast, err := NewBroadcast(settings, quiet(), group, topic)
	if err != nil {
		t.Fatalf("NewBroadcast: %v", err)
	}
	ctx, cancel := context.WithCancel(context.Background())
	keys := make(chan string, 100)
	done := make(chan error, 1)
	go func() {
		done <- broadcast.Run(ctx, func(_ context.Context, record *kgo.Record) { keys <- string(record.Key) })
	}()
	t.Cleanup(func() {
		cancel()
		if err := <-done; err != nil {
			t.Errorf("Run: %v", err)
		}
	})
	return keys
}

func await(t *testing.T, keys chan string, send func()) string {
	t.Helper()
	deadline := time.After(wait)
	for {
		send()
		select {
		case key := <-keys:
			return key
		case <-time.After(retry):
		case <-deadline:
			t.Fatal("no record arrived")
		}
	}
}

func publish(t *testing.T, producer *Producer, topic, key string) {
	t.Helper()
	if err := producer.Publish(context.Background(), topic, key, command(key)); err != nil {
		t.Fatalf("Publish: %v", err)
	}
}

func command(serverID string) proto.Message {
	return &eventsv1.ServerCommand{ServerId: serverID}
}

func quiet() *slog.Logger { return slog.New(slog.NewTextHandler(io.Discard, nil)) }
