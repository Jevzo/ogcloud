package gateway

import (
	"context"
	"io"
	"log/slog"
	"testing"
	"time"

	"github.com/twmb/franz-go/pkg/kgo"

	"github.com/jevzo/ogcloud/data-plane/internal/testkafka"
	"github.com/jevzo/ogcloud/data-plane/internal/testvalkey"
	eventsv1 "github.com/jevzo/ogcloud/data-plane/pkg/gen/proto/ogcloud/events/v1"
	internalv1 "github.com/jevzo/ogcloud/data-plane/pkg/gen/proto/ogcloud/internalapi/v1"
	nodev1 "github.com/jevzo/ogcloud/data-plane/pkg/gen/proto/ogcloud/node/v1"
	"github.com/jevzo/ogcloud/data-plane/pkg/kafka"
	"github.com/jevzo/ogcloud/data-plane/pkg/kafkatopics"
	"github.com/jevzo/ogcloud/data-plane/pkg/valkey"
	"github.com/jevzo/ogcloud/data-plane/pkg/valkeykeys"
)

func TestKafkaRecordsReachTheRightSessions(t *testing.T) {
	ctx, cancel := context.WithCancel(context.Background())
	defer cancel()
	broker := testkafka.Start(t, nil)
	broker.CreateTopic(t, kafkatopics.PlayerTransfer)
	broker.CreateTopic(t, kafkatopics.LiveChannel)
	address := testvalkey.Start(t)
	testvalkey.NewWriter(t, address).Record(t, valkeykeys.Session(playerUUID), &internalv1.PlayerSession{PlayerUuid: playerUUID, ProxyId: "proxy-1"})
	store, err := valkey.Connect(valkey.Settings{Addresses: []string{address}})
	if err != nil {
		t.Fatal(err)
	}
	defer store.Close()
	settings := kafka.Settings{Brokers: broker.Addresses, ClientID: "gateway"}
	producer, err := kafka.NewProducer(settings)
	if err != nil {
		t.Fatal(err)
	}
	defer producer.Close()
	dependencies := testDependencies(store, producer, time.Now)
	session := func(serverID string, nodeType eventsv1.NodeType) *Session {
		s := newSession(Peer{ServerID: serverID}, hello(nodeType), 16, func(error) {})
		dependencies.Sessions.Add(s)
		return s
	}
	playersProxy, otherProxy, subscriber := session("proxy-1", eventsv1.NodeType_NODE_TYPE_PROXY),
		session("proxy-2", eventsv1.NodeType_NODE_TYPE_PROXY), session("lobby-1", eventsv1.NodeType_NODE_TYPE_BACKEND)
	if err := subscriber.live.subscribe("party", true); err != nil {
		t.Fatal(err)
	}
	router := NewRouter(dependencies, deliveryTimeout)
	broadcast, err := kafka.NewBroadcast(settings, slog.New(slog.NewTextHandler(io.Discard, nil)), "ogcloud-gateway-test",
		kafkatopics.PlayerTransfer, kafkatopics.LiveChannel)
	if err != nil {
		t.Fatal(err)
	}
	go func() {
		_ = broadcast.Run(ctx, func(ctx context.Context, record *kgo.Record) { router.Handle(ctx, record.Topic, record.Value) })
	}()
	service := NewService(dependencies, Options{})

	// The broadcast joins at the latest offset once assigned, so each record is sent until it arrives.
	transfer := awaitMessage(t, playersProxy, func() {
		err := producer.Publish(ctx, kafkatopics.PlayerTransfer, playerUUID, &eventsv1.TransferRequested{
			Subject: &eventsv1.TransferRequested_PlayerUuid{PlayerUuid: playerUUID}, Destination: &eventsv1.TransferRequested_GroupId{GroupId: "lobby"},
		})
		if err != nil {
			t.Fatal(err)
		}
	})
	live := awaitMessage(t, subscriber, func() {
		publish := &nodev1.LiveChannelPublish{Channel: "party", TypeId: "Invite", Payload: []byte("x")}
		if err := service.handle(ctx, otherProxy, &nodev1.SessionRequest{Message: &nodev1.SessionRequest_LivePublish{LivePublish: publish}}); err != nil {
			t.Fatal(err)
		}
	})

	if transfer.GetDelivery().GetTransfer().GetGroupId() != "lobby" {
		t.Errorf("proxy-1 got %v", transfer)
	}
	if live.GetLiveMessage().GetSenderServerId() != "proxy-2" || string(live.GetLiveMessage().GetPayload()) != "x" {
		t.Errorf("lobby-1 got %v", live)
	}
	for len(otherProxy.out) > 0 {
		if message := <-otherProxy.out; message.GetDelivery() != nil {
			t.Errorf("proxy-2 got the transfer of a player it does not hold: %v", message)
		}
	}
}

// awaitMessage repeats send until session receives something.
func awaitMessage(t *testing.T, session *Session, send func()) *nodev1.SessionResponse {
	t.Helper()
	deadline := time.After(time.Minute)
	ticker := time.NewTicker(500 * time.Millisecond)
	defer ticker.Stop()
	send()
	for {
		select {
		case message := <-session.out:
			return message
		case <-ticker.C:
			send()
		case <-deadline:
			t.Fatal("nothing arrived")
		}
	}
}
