package valkey

import (
	"bytes"
	"context"
	"crypto/tls"
	"log/slog"
	"net"
	"strings"
	"testing"

	"github.com/testcontainers/testcontainers-go"
	"github.com/testcontainers/testcontainers-go/network"
	"github.com/testcontainers/testcontainers-go/wait"
	"google.golang.org/protobuf/proto"

	"github.com/jevzo/ogcloud/data-plane/internal/testvalkey"
	eventsv1 "github.com/jevzo/ogcloud/data-plane/pkg/gen/proto/ogcloud/events/v1"
	internalv1 "github.com/jevzo/ogcloud/data-plane/pkg/gen/proto/ogcloud/internalapi/v1"
	"github.com/jevzo/ogcloud/data-plane/pkg/valkeykeys"
)

const (
	master       = "ogcloud"
	primaryAlias = "primary"
	sentinelPort = "26379/tcp"
)

func TestRecordsSessionsAndReadModelsAreReadAsTheControllerWroteThem(t *testing.T) {
	ctx := context.Background()
	address := testvalkey.Start(t)
	writer := testvalkey.NewWriter(t, address)
	lobby := &internalv1.ServerRecord{ServerId: "s-1", GroupId: "lobby", State: eventsv1.ServerState_SERVER_STATE_RUNNING}
	writer.Record(t, valkeykeys.Server("s-1"), lobby)
	writer.Add(t, valkeykeys.Servers, "s-1", "expired")
	writer.Add(t, valkeykeys.ServersOfGroup("lobby"), "s-1")
	writer.Record(t, valkeykeys.Session("p-1"), &internalv1.PlayerSession{PlayerUuid: "p-1", PlayerName: "Jevzo"})
	writer.Set(t, valkeykeys.SessionName("Jevzo"), "p-1")
	writer.Set(t, valkeykeys.ReadModel(valkeykeys.ReadModelGroup, "lobby"), "bytes")
	store := connect(t, Settings{Addresses: []string{address}})

	servers, err := store.Servers(ctx)
	must(t, err)
	if len(servers) != 1 || !proto.Equal(servers[0], lobby) {
		t.Errorf("Servers = %v; the expired id should be skipped", servers)
	}
	inGroup, err := store.ServersOfGroup(ctx, "lobby")
	must(t, err)
	if len(inGroup) != 1 {
		t.Errorf("ServersOfGroup = %v", inGroup)
	}
	missing, err := store.Server(ctx, "nope")
	must(t, err)
	if missing != nil {
		t.Errorf("Server(nope) = %v", missing)
	}
	session, err := store.SessionByName(ctx, "JEVZO")
	must(t, err)
	if session.GetPlayerUuid() != "p-1" {
		t.Errorf("SessionByName = %v", session)
	}
	model, err := store.ReadModel(ctx, valkeykeys.ReadModelGroup, "lobby")
	must(t, err)
	if string(model) != "bytes" {
		t.Errorf("ReadModel = %q", model)
	}
	absent, err := store.ReadModel(ctx, valkeykeys.ReadModelGroup, "bedwars")
	must(t, err)
	if absent != nil {
		t.Errorf("absent read model = %q", absent)
	}
}

func TestACorruptRecordIsAnErrorButListsSkipIt(t *testing.T) {
	ctx := context.Background()
	address := testvalkey.Start(t)
	writer := testvalkey.NewWriter(t, address)
	raw := writer.Client
	must(t, raw.Do(ctx, raw.B().Hset().Key(valkeykeys.Server("s-1")).FieldValue().FieldValue(valkeykeys.FieldData, "\xff\xff").Build()).Error())
	writer.Record(t, valkeykeys.Server("s-2"), &internalv1.ServerRecord{ServerId: "s-2"})
	writer.Add(t, valkeykeys.Servers, "s-1", "s-2")
	var logged bytes.Buffer
	store := connect(t, Settings{Addresses: []string{address}, Logger: slog.New(slog.NewTextHandler(&logged, nil))})

	if _, err := store.Server(ctx, "s-1"); err == nil {
		t.Error("a corrupt record was accepted")
	}
	servers, err := store.Servers(ctx)
	must(t, err)
	if len(servers) != 1 || servers[0].GetServerId() != "s-2" || !strings.Contains(logged.String(), "corrupt record skipped") {
		t.Errorf("Servers = %v, logged %q", servers, logged.String())
	}
}

func TestACLUsersAuthenticate(t *testing.T) {
	address := testvalkey.Start(t, "--user", "default", "off", "--user", "ogcloud", "on", ">s3cret", "~*", "&*", "+@all")

	store := connect(t, Settings{Addresses: []string{address}, Username: "ogcloud", Password: "s3cret"})
	must(t, store.Ping(context.Background()))
	if _, err := Connect(Settings{Addresses: []string{address}, Username: "ogcloud", Password: "wrong"}); err == nil {
		t.Error("a wrong password was accepted")
	}
}

func TestTheSentinelNamesThePrimary(t *testing.T) {
	ctx := context.Background()
	dockerNetwork := testvalkey.Network(t)
	primary := testvalkey.Run(t, network.WithNetwork([]string{primaryAlias}, &testcontainers.DockerNetwork{Name: dockerNetwork}))
	sentinel := testvalkey.Run(t,
		network.WithNetwork(nil, &testcontainers.DockerNetwork{Name: dockerNetwork}),
		testcontainers.WithExposedPorts(sentinelPort),
		testcontainers.WithWaitStrategy(wait.ForListeningPort(sentinelPort)),
		testcontainers.WithCmd("sh", "-c",
			"printf 'port 26379\\nsentinel resolve-hostnames yes\\nsentinel announce-hostnames yes\\n"+
				"sentinel monitor "+master+" "+primaryAlias+" 6379 1\\n' > /tmp/sentinel.conf && valkey-server /tmp/sentinel.conf --sentinel"),
	)
	primaryAddress := testvalkey.Address(t, primary)
	// Sentinel announces the primary by its network alias; the test reaches it through the mapped port.
	dial := func(ctx context.Context, address string, dialer *net.Dialer, _ *tls.Config) (net.Conn, error) {
		if host, _, err := net.SplitHostPort(address); err == nil && host == primaryAlias {
			address = primaryAddress
		}
		return dialer.DialContext(ctx, "tcp", address)
	}

	store := connect(t, Settings{Addresses: []string{testvalkey.Address(t, sentinel, sentinelPort)}, SentinelMaster: master, Dial: dial})

	must(t, store.Ping(ctx))
}

func connect(t *testing.T, settings Settings) *Store {
	t.Helper()
	store, err := Connect(settings)
	must(t, err)
	t.Cleanup(store.Close)
	return store
}

func must(t *testing.T, err error) {
	t.Helper()
	if err != nil {
		t.Fatal(err)
	}
}
