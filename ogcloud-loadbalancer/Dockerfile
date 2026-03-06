FROM golang:1.25.5-alpine AS builder

WORKDIR /app
COPY go.mod go.sum ./
RUN go mod download
COPY . .
RUN CGO_ENABLED=0 go build -o /loadbalancer ./cmd/loadbalancer

FROM alpine:3.23

RUN apk add --no-cache ca-certificates
COPY --from=builder /loadbalancer /loadbalancer
ENTRYPOINT ["/loadbalancer"]
