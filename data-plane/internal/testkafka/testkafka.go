// Package testkafka runs a single-node KRaft broker from the pinned apache/kafka image (D-0113), which the
// Testcontainers Kafka module for Go does not support. The host reaches the PLAINTEXT listener; BROKER
// (9092) and CONTROLLER stay inside the container.
package testkafka

import (
	"context"
	"fmt"
	"maps"
	"strings"
	"testing"
	"time"

	"github.com/testcontainers/testcontainers-go"
	"github.com/testcontainers/testcontainers-go/wait"

	"github.com/jevzo/ogcloud/data-plane/internal/testimages"
)

const (
	hostPort     = "9093/tcp"
	startScript  = "/tmp/start.sh"
	startTimeout = 2 * time.Minute
)

// Broker is a running test broker.
type Broker struct {
	testcontainers.Container
	// Addresses reach the PLAINTEXT listener from the host.
	Addresses []string
}

// Start runs a broker; env overrides the defaults, e.g. to put SASL on the PLAINTEXT listener.
func Start(t *testing.T, env map[string]string) *Broker {
	t.Helper()
	ctx := context.Background()
	image, err := testimages.Image("kafka")
	if err != nil {
		t.Fatalf("image: %v", err)
	}
	settings := map[string]string{
		"CLUSTER_ID":                                     "ogcloud-test-cluster-01",
		"KAFKA_NODE_ID":                                  "1",
		"KAFKA_PROCESS_ROLES":                            "broker,controller",
		"KAFKA_LISTENERS":                                "PLAINTEXT://0.0.0.0:9093,BROKER://0.0.0.0:9092,CONTROLLER://0.0.0.0:9094",
		"KAFKA_LISTENER_SECURITY_PROTOCOL_MAP":           "BROKER:PLAINTEXT,PLAINTEXT:PLAINTEXT,CONTROLLER:PLAINTEXT",
		"KAFKA_INTER_BROKER_LISTENER_NAME":               "BROKER",
		"KAFKA_CONTROLLER_LISTENER_NAMES":                "CONTROLLER",
		"KAFKA_CONTROLLER_QUORUM_VOTERS":                 "1@localhost:9094",
		"KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR":         "1",
		"KAFKA_TRANSACTION_STATE_LOG_REPLICATION_FACTOR": "1",
		"KAFKA_TRANSACTION_STATE_LOG_MIN_ISR":            "1",
		"KAFKA_GROUP_INITIAL_REBALANCE_DELAY_MS":         "0",
	}
	maps.Copy(settings, env)
	container, err := testcontainers.GenericContainer(ctx, testcontainers.GenericContainerRequest{
		ContainerRequest: testcontainers.ContainerRequest{
			Image:        image,
			ExposedPorts: []string{hostPort},
			Env:          settings,
			// The advertised address needs the mapped port, known only once the container runs.
			Entrypoint: []string{"sh", "-c", "while [ ! -f " + startScript + " ]; do sleep 0.1; done; sh " + startScript},
		},
		Started: true,
	})
	testcontainers.CleanupContainer(t, container)
	if err != nil {
		t.Fatalf("start Kafka: %v", err)
	}
	host, err := container.Host(ctx)
	if err != nil {
		t.Fatalf("host: %v", err)
	}
	port, err := container.MappedPort(ctx, hostPort)
	if err != nil {
		t.Fatalf("mapped port: %v", err)
	}
	address := fmt.Sprintf("%s:%s", host, port.Port())
	script := fmt.Sprintf("export KAFKA_ADVERTISED_LISTENERS=PLAINTEXT://%s,BROKER://localhost:9092\nexec /etc/kafka/docker/run\n", address)
	if err := container.CopyToContainer(ctx, []byte(script), startScript, 0o755); err != nil {
		t.Fatalf("copy start script: %v", err)
	}
	ready := wait.ForLog("Kafka Server started").WithStartupTimeout(startTimeout)
	if err := ready.WaitUntilReady(ctx, container); err != nil {
		t.Fatalf("Kafka did not start: %v", err)
	}
	return &Broker{Container: container, Addresses: []string{address}}
}

// Exec runs a Kafka CLI tool inside the container against the internal BROKER listener and fails the test
// unless it succeeds.
func (b *Broker) Exec(t *testing.T, tool string, args ...string) {
	t.Helper()
	command := append([]string{"/opt/kafka/bin/" + tool, "--bootstrap-server", "localhost:9092"}, args...)
	code, _, err := b.Container.Exec(context.Background(), command)
	if err != nil || code != 0 {
		t.Fatalf("%s: exit %d, %v", strings.Join(command, " "), code, err)
	}
}

// CreateTopic creates topic, as Strimzi does in a cluster; clients never create topics themselves.
func (b *Broker) CreateTopic(t *testing.T, topic string) {
	t.Helper()
	b.Exec(t, "kafka-topics.sh", "--create", "--topic", topic, "--partitions", "3", "--replication-factor", "1")
}
