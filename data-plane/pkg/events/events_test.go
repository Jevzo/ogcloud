package events

import (
	"crypto/rand"
	"testing"
	"time"

	"github.com/jevzo/ogcloud/data-plane/pkg/ids"
)

func TestMetadataCarriesIdTimeProducerAndActor(t *testing.T) {
	now := time.Date(2026, 11, 2, 12, 0, 0, 0, time.UTC)
	clock := func() time.Time { return now }
	stamper := NewStamper(ids.New(clock, rand.Reader), clock, "gateway")

	metadata, err := stamper.Metadata(Player("p-1"))
	if err != nil {
		t.Fatalf("Metadata: %v", err)
	}

	if metadata.GetEventId() == "" || !metadata.GetOccurredAt().AsTime().Equal(now) || metadata.GetProducer() != "gateway" {
		t.Errorf("metadata = %v", metadata)
	}
	if metadata.GetActor().GetPlayerUuid() != "p-1" || System("gateway").GetSystem() != "gateway" {
		t.Errorf("actor = %v", metadata.GetActor())
	}
}
