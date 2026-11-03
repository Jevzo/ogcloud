// Package valkey reads the live state and read models the controller keeps in Valkey (D-0007, D-0077):
// server records, player sessions and read-model bytes. The data plane only reads; keys come from the
// generated layout (D-0111).
package valkey

import (
	"context"
	"crypto/tls"
	"errors"
	"fmt"
	"log/slog"
	"net"
	"strings"
	"time"

	"github.com/valkey-io/valkey-go"
	"google.golang.org/protobuf/proto"

	internalv1 "github.com/jevzo/ogcloud/data-plane/pkg/gen/proto/ogcloud/internalapi/v1"
	"github.com/jevzo/ogcloud/data-plane/pkg/valkeykeys"
)

const sentinelRefresh = 5 * time.Second

// Settings reach a standalone Valkey, or the primary that Sentinel names when SentinelMaster is set (D-0048).
type Settings struct {
	// Valkey nodes, or the sentinels with SentinelMaster.
	Addresses      []string
	SentinelMaster string
	// ACL user; empty uses the default user.
	Username string
	Password string
	// Nil for plaintext.
	TLS *tls.Config
	// Optional custom dialing, e.g. to map addresses Sentinel announces inside a test network.
	Dial func(ctx context.Context, address string, dialer *net.Dialer, tlsConfig *tls.Config) (net.Conn, error)
	// Where corrupt records skipped in lists are reported; nil uses slog's default logger.
	Logger *slog.Logger
}

// Store reads live state.
type Store struct {
	client valkey.Client
	logger *slog.Logger
}

// Connect opens a client; it fails when no node or sentinel answers.
func Connect(s Settings) (*Store, error) {
	option := valkey.ClientOption{
		InitAddress:  s.Addresses,
		Username:     s.Username,
		Password:     s.Password,
		TLSConfig:    s.TLS,
		DialCtxFn:    s.Dial,
		DisableCache: true,
	}
	if s.SentinelMaster != "" {
		option.Sentinel = valkey.SentinelOption{
			MasterSet:               s.SentinelMaster,
			Username:                s.Username,
			Password:                s.Password,
			TLSConfig:               s.TLS,
			TopologyRefreshInterval: sentinelRefresh,
		}
	}
	client, err := valkey.NewClient(option)
	if err != nil {
		return nil, fmt.Errorf("connect to Valkey %s: %w", strings.Join(s.Addresses, ","), err)
	}
	logger := s.Logger
	if logger == nil {
		logger = slog.Default()
	}
	return &Store{client: client, logger: logger}, nil
}

// Close disconnects.
func (s *Store) Close() { s.client.Close() }

// Ping checks the connection, e.g. for readiness.
func (s *Store) Ping(ctx context.Context) error {
	if err := s.client.Do(ctx, s.client.B().Ping().Build()).Error(); err != nil {
		return fmt.Errorf("ping Valkey: %w", err)
	}
	return nil
}

// Server returns the live record of a server, or nil when there is none.
func (s *Store) Server(ctx context.Context, id string) (*internalv1.ServerRecord, error) {
	records, err := readRecords[*internalv1.ServerRecord](ctx, s, []string{valkeykeys.Server(id)}, false)
	if err != nil {
		return nil, err
	}
	if len(records) == 0 {
		return nil, nil
	}
	return records[0], nil
}

// Servers returns every live server record.
func (s *Store) Servers(ctx context.Context) ([]*internalv1.ServerRecord, error) {
	return s.serversIn(ctx, valkeykeys.Servers)
}

// ServersOfGroup returns the live records of a group's servers.
func (s *Store) ServersOfGroup(ctx context.Context, groupID string) ([]*internalv1.ServerRecord, error) {
	return s.serversIn(ctx, valkeykeys.ServersOfGroup(groupID))
}

// Session returns an online player's session, or nil when the player is offline.
func (s *Store) Session(ctx context.Context, playerUUID string) (*internalv1.PlayerSession, error) {
	records, err := readRecords[*internalv1.PlayerSession](ctx, s, []string{valkeykeys.Session(playerUUID)}, false)
	if err != nil {
		return nil, err
	}
	if len(records) == 0 {
		return nil, nil
	}
	return records[0], nil
}

// SessionByName returns the session of the online player with name, ignoring case, or nil.
func (s *Store) SessionByName(ctx context.Context, name string) (*internalv1.PlayerSession, error) {
	uuid, err := s.client.Do(ctx, s.client.B().Get().Key(valkeykeys.SessionName(name)).Build()).ToString()
	if valkey.IsValkeyNil(err) {
		return nil, nil
	}
	if err != nil {
		return nil, fmt.Errorf("look up session of %s: %w", name, err)
	}
	return s.Session(ctx, uuid)
}

// ReadModel returns the stored bytes of a read model, or nil when there is none.
func (s *Store) ReadModel(ctx context.Context, kind valkeykeys.ReadModelKind, id string) ([]byte, error) {
	encoded, err := s.client.Do(ctx, s.client.B().Get().Key(valkeykeys.ReadModel(kind, id)).Build()).AsBytes()
	if valkey.IsValkeyNil(err) {
		return nil, nil
	}
	if err != nil {
		return nil, fmt.Errorf("read model %s %s: %w", kind, id, err)
	}
	return encoded, nil
}

func (s *Store) serversIn(ctx context.Context, set string) ([]*internalv1.ServerRecord, error) {
	ids, err := s.client.Do(ctx, s.client.B().Smembers().Key(set).Build()).AsStrSlice()
	if err != nil {
		return nil, fmt.Errorf("list %s: %w", set, err)
	}
	keys := make([]string, len(ids))
	for i, id := range ids {
		keys[i] = valkeykeys.Server(id)
	}
	return readRecords[*internalv1.ServerRecord](ctx, s, keys, true)
}

// readRecords loads the data field of each revisioned record in one round trip. Records that expired
// since their id was listed are skipped. A record that does not decode is an error, except in a listing,
// where it is logged and skipped so one bad record cannot hide every server.
func readRecords[T proto.Message](ctx context.Context, s *Store, keys []string, listing bool) ([]T, error) {
	if len(keys) == 0 {
		return nil, nil
	}
	commands := make(valkey.Commands, len(keys))
	for i, key := range keys {
		commands[i] = s.client.B().Hget().Key(key).Field(valkeykeys.FieldData).Build()
	}
	var records []T
	for i, result := range s.client.DoMulti(ctx, commands...) {
		encoded, err := result.AsBytes()
		if valkey.IsValkeyNil(err) {
			continue
		}
		if err != nil {
			return nil, fmt.Errorf("read %s: %w", keys[i], err)
		}
		record := newMessage[T]()
		if err := proto.Unmarshal(encoded, record); err != nil {
			if listing {
				s.logger.Error("corrupt record skipped", "key", keys[i], "error", err)
				continue
			}
			return nil, errors.Join(fmt.Errorf("corrupt record %s", keys[i]), err)
		}
		records = append(records, record)
	}
	return records, nil
}

// newMessage returns an empty T, e.g. a new *ServerRecord.
func newMessage[T proto.Message]() T {
	var zero T
	return zero.ProtoReflect().New().Interface().(T) //nolint:forcetypeassert // New returns the same message type.
}
