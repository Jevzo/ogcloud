// Package events stamps the events the data plane publishes with their metadata (D-0064).
package events

import (
	"fmt"
	"time"

	"google.golang.org/protobuf/types/known/timestamppb"

	eventsv1 "github.com/jevzo/ogcloud/data-plane/pkg/gen/proto/ogcloud/events/v1"
	"github.com/jevzo/ogcloud/data-plane/pkg/ids"
)

// Stamper creates metadata for one producing service.
type Stamper struct {
	ids      *ids.Generator
	now      func() time.Time
	producer string
}

// NewStamper stamps events as producer, e.g. gateway.
func NewStamper(ids *ids.Generator, now func() time.Time, producer string) *Stamper {
	return &Stamper{ids: ids, now: now, producer: producer}
}

// Metadata returns a fresh event id and time for an event caused by actor.
func (s *Stamper) Metadata(actor *eventsv1.Actor) (*eventsv1.EventMetadata, error) {
	id, err := s.ids.Next()
	if err != nil {
		return nil, fmt.Errorf("event id: %w", err)
	}
	return &eventsv1.EventMetadata{EventId: id, OccurredAt: timestamppb.New(s.now()), Producer: s.producer, Actor: actor}, nil
}

// System is the actor for what a service does on its own.
func System(component string) *eventsv1.Actor {
	return &eventsv1.Actor{Subject: &eventsv1.Actor_System{System: component}}
}

// Player is the actor for what a player caused, e.g. an in-game admin command.
func Player(uuid string) *eventsv1.Actor {
	return &eventsv1.Actor{Subject: &eventsv1.Actor_PlayerUuid{PlayerUuid: uuid}}
}
