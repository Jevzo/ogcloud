package testvalkey

import (
	"context"
	"testing"

	vk "github.com/valkey-io/valkey-go"
	"google.golang.org/protobuf/proto"

	"github.com/jevzo/ogcloud/data-plane/pkg/valkeykeys"
)

// Writer writes live state the way the controller does, for tests of the data plane's readers.
type Writer struct {
	Client vk.Client
}

// NewWriter connects to address.
func NewWriter(t *testing.T, address string) *Writer {
	t.Helper()
	client, err := vk.NewClient(vk.ClientOption{InitAddress: []string{address}, DisableCache: true})
	if err != nil {
		t.Fatalf("valkey client: %v", err)
	}
	t.Cleanup(client.Close)
	return &Writer{Client: client}
}

// Record writes a revisioned record (server or session) under key.
func (w *Writer) Record(t *testing.T, key string, record proto.Message) {
	t.Helper()
	encoded, err := proto.Marshal(record)
	if err != nil {
		t.Fatalf("encode %s: %v", key, err)
	}
	w.do(t, w.Client.B().Hset().Key(key).FieldValue().
		FieldValue(valkeykeys.FieldRevision, "1").FieldValue(valkeykeys.FieldData, string(encoded)).Build())
}

// ReadModel stores a read model's message bytes.
func (w *Writer) ReadModel(t *testing.T, kind valkeykeys.ReadModelKind, id string, message proto.Message) {
	t.Helper()
	encoded, err := proto.Marshal(message)
	if err != nil {
		t.Fatalf("encode %s %s: %v", kind, id, err)
	}
	w.Set(t, valkeykeys.ReadModel(kind, id), string(encoded))
}

// Set writes a plain string value.
func (w *Writer) Set(t *testing.T, key, value string) {
	t.Helper()
	w.do(t, w.Client.B().Set().Key(key).Value(value).Build())
}

// Add puts members into a set.
func (w *Writer) Add(t *testing.T, key string, members ...string) {
	t.Helper()
	w.do(t, w.Client.B().Sadd().Key(key).Member(members...).Build())
}

func (w *Writer) do(t *testing.T, command vk.Completed) {
	t.Helper()
	if err := w.Client.Do(context.Background(), command).Error(); err != nil {
		t.Fatalf("valkey: %v", err)
	}
}
