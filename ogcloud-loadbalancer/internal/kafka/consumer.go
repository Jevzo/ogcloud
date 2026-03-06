package kafka

import (
	"context"
	"encoding/json"
	"fmt"
	"time"

	"github.com/IBM/sarama"
	"go.uber.org/zap"
)

const (
	TopicNetworkUpdate   = "ogcloud.network.update"
	TopicProxyHeartbeat  = "ogcloud.proxy.heartbeat"
	TopicServerLifecycle = "ogcloud.server.lifecycle"

	ProtocolVersion1_21_11 = 774
	proxyServerType       = "PROXY"

	initialBackoff = 1 * time.Second
	maxBackoff     = 30 * time.Second
	backoffFactor  = 2
)

var removableProxyStates = map[string]struct{}{
	"DRAINING": {},
	"STOPPING": {},
	"STOPPED":  {},
}

type BackendUpdater interface {
	UpdateBackend(proxyID, address string, playerCount int, maxPlayers int)
	RemoveBackend(proxyID string)
}

type Consumer struct {
	client  sarama.ConsumerGroup
	network *NetworkState
	pool    BackendUpdater
	logger  *zap.Logger
	topics  []string
}

func NewConsumer(brokers []string, groupID string, network *NetworkState, pool BackendUpdater, logger *zap.Logger) (*Consumer, error) {
	config := sarama.NewConfig()
	config.Consumer.Offsets.Initial = sarama.OffsetOldest
	config.Consumer.Group.Rebalance.GroupStrategies = []sarama.BalanceStrategy{sarama.NewBalanceStrategyRange()}

	client, err := sarama.NewConsumerGroup(brokers, groupID, config)
	if err != nil {
		return nil, fmt.Errorf("create consumer group: %w", err)
	}

	return &Consumer{
		client:  client,
		network: network,
		pool:    pool,
		logger:  logger,
		topics:  []string{TopicNetworkUpdate, TopicProxyHeartbeat, TopicServerLifecycle},
	}, nil
}

func (c *Consumer) Start(ctx context.Context) {
	handler := &consumerHandler{
		network: c.network,
		pool:    c.pool,
		logger:  c.logger,
	}

	go func() {
		backoff := initialBackoff
		for {
			if err := c.client.Consume(ctx, c.topics, handler); err != nil {
				c.logger.Error("kafka consumer error", zap.Error(err), zap.Duration("backoff", backoff))

				select {
				case <-time.After(backoff):
				case <-ctx.Done():
					return
				}

				backoff *= backoffFactor
				if backoff > maxBackoff {
					backoff = maxBackoff
				}
				continue
			}
			// Reset backoff on successful consume session
			backoff = initialBackoff

			if ctx.Err() != nil {
				return
			}
		}
	}()
}

func (c *Consumer) Close() error {
	return c.client.Close()
}

type consumerHandler struct {
	network *NetworkState
	pool    BackendUpdater
	logger  *zap.Logger
}

func (h *consumerHandler) Setup(_ sarama.ConsumerGroupSession) error   { return nil }
func (h *consumerHandler) Cleanup(_ sarama.ConsumerGroupSession) error { return nil }

func (h *consumerHandler) ConsumeClaim(session sarama.ConsumerGroupSession, claim sarama.ConsumerGroupClaim) error {
	for msg := range claim.Messages() {
		switch msg.Topic {
		case TopicNetworkUpdate:
			var event NetworkUpdateEvent
			if err := json.Unmarshal(msg.Value, &event); err != nil {
				h.logger.Error("failed to unmarshal network update", zap.Error(err))
				continue
			}
			h.network.update(event)
			h.logger.Info("network settings updated",
				zap.Bool("maintenance", event.Maintenance),
				zap.Int("maxPlayers", event.MaxPlayers),
			)

		case TopicProxyHeartbeat:
			var event ProxyHeartbeatEvent
			if err := json.Unmarshal(msg.Value, &event); err != nil {
				h.logger.Error("failed to unmarshal proxy heartbeat", zap.Error(err))
				continue
			}
			address := fmt.Sprintf("%s:%d", event.PodIP, event.Port)
			h.pool.UpdateBackend(event.ProxyID, address, event.PlayerCount, event.MaxPlayers)
			h.network.UpdateProxyPlayerCount(event.ProxyID, event.PlayerCount)

		case TopicServerLifecycle:
			var event ServerLifecycleEvent
			if err := json.Unmarshal(msg.Value, &event); err != nil {
				h.logger.Error("failed to unmarshal lifecycle event", zap.Error(err))
				continue
			}
			if shouldRemoveProxyBackend(event) {
				h.pool.RemoveBackend(event.ServerID)
				h.network.RemoveProxyPlayerCount(event.ServerID)
				h.logger.Info("removed proxy backend due to lifecycle event",
					zap.String("proxyId", event.ServerID),
					zap.String("state", event.State),
				)
			}
		}

		session.MarkMessage(msg, "")
	}
	return nil
}

func shouldRemoveProxyBackend(event ServerLifecycleEvent) bool {
	if event.Type != proxyServerType {
		return false
	}

	_, exists := removableProxyStates[event.State]
	return exists
}
