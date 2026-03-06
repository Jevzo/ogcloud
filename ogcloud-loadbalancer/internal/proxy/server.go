package proxy

import (
	"errors"
	"net"
	"sync"
	"time"

	"go.uber.org/zap"
)

type Server struct {
	handler      *Handler
	listener     net.Listener
	wg           sync.WaitGroup
	drainTimeout time.Duration
	logger       *zap.Logger
}

func NewServer(handler *Handler, drainTimeout time.Duration, logger *zap.Logger) *Server {
	return &Server{
		handler:      handler,
		drainTimeout: drainTimeout,
		logger:       logger,
	}
}

func (s *Server) Start(addr string) error {
	listener, err := net.Listen("tcp", addr)
	if err != nil {
		return err
	}
	s.listener = listener

	s.logger.Info("TCP server listening", zap.String("addr", addr))

	for {
		conn, err := listener.Accept()
		if err != nil {
			if errors.Is(err, net.ErrClosed) {
				return nil
			}
			s.logger.Error("accept error", zap.Error(err))
			continue
		}

		s.wg.Add(1)
		go func() {
			defer s.wg.Done()
			s.handler.Handle(conn)
		}()
	}
}

func (s *Server) Stop() {
	if s.listener != nil {
		s.listener.Close()
	}

	done := make(chan struct{})
	go func() {
		s.wg.Wait()
		close(done)
	}()

	select {
	case <-done:
		s.logger.Info("all connections drained gracefully")
	case <-time.After(s.drainTimeout):
		s.logger.Warn("drain timeout reached, forcing shutdown",
			zap.Duration("timeout", s.drainTimeout))
	}
}
