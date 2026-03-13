package proxy

import (
	"io"
	"net"

	"github.com/pires/go-proxyproto"
	"go.uber.org/zap"

	"github.com/ogwars/ogcloud-loadbalancer/internal/kafka"
	"github.com/ogwars/ogcloud-loadbalancer/internal/protocol"
)

type Handler struct {
	pool          *Pool
	network       *kafka.NetworkState
	proxyProtocol bool
	logger        *zap.Logger
}

func NewHandler(pool *Pool, network *kafka.NetworkState, proxyProtocol bool, logger *zap.Logger) *Handler {
	return &Handler{
		pool:          pool,
		network:       network,
		proxyProtocol: proxyProtocol,
		logger:        logger,
	}
}

func (h *Handler) Handle(clientConn net.Conn) {
	defer clientConn.Close()

	hs, rawHandshake, err := protocol.ReadHandshake(clientConn)
	if err != nil {
		h.logger.Debug("failed to read handshake", zap.Error(err))
		return
	}

	switch hs.NextState {
	case protocol.StatusNextState:
		h.handleStatus(clientConn, hs.ProtocolVersion)
	case protocol.LoginNextState:
		h.handleLogin(clientConn, rawHandshake)
	default:
		h.logger.Debug("unknown next state", zap.Int32("nextState", hs.NextState))
	}
}

func (h *Handler) handleStatus(
	clientConn net.Conn,
	clientProtocolVersion int32,
) {
	motd := h.network.GetMOTD()
	maxPlayers := h.network.GetMaxPlayers()
	onlinePlayers := h.network.GetOnlinePlayers()
	versionName, protocolVersion := h.network.ResolveStatusVersion(clientProtocolVersion)

	if err := protocol.HandleStatusRequest(clientConn, versionName, motd, maxPlayers, onlinePlayers, protocolVersion); err != nil {
		h.logger.Debug("status request failed", zap.Error(err))
	}
}

func (h *Handler) handleLogin(clientConn net.Conn, rawHandshake []byte) {
	backend, err := h.pool.SelectBackend(h.network.GetProxyRoutingStrategy())
	if err != nil {
		h.logger.Warn("no backend available for login", zap.Error(err))
		return
	}

	backendConn, err := h.pool.Dial(backend)
	if err != nil {
		h.logger.Error("failed to dial backend", zap.String("proxyId", backend.ProxyID), zap.Error(err))
		return
	}
	defer func() {
		backendConn.Close()
		h.pool.ReleaseBackend(backend)
	}()

	if h.proxyProtocol {
		header := proxyproto.HeaderProxyFromAddrs(2, clientConn.RemoteAddr(), backendConn.LocalAddr())
		if _, err := header.WriteTo(backendConn); err != nil {
			h.logger.Error("failed to write proxy protocol header", zap.Error(err))
			return
		}
	}

	if _, err := backendConn.Write(rawHandshake); err != nil {
		h.logger.Error("failed to forward handshake", zap.Error(err))
		return
	}

	done := make(chan struct{}, 2)

	go func() {
		_, _ = io.Copy(backendConn, clientConn)
		done <- struct{}{}
	}()

	go func() {
		_, _ = io.Copy(clientConn, backendConn)
		done <- struct{}{}
	}()

	<-done
}
