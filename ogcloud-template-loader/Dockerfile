FROM golang:1.25.5-alpine AS builder

WORKDIR /app
COPY go.mod go.sum ./
RUN go mod download
COPY . .
RUN CGO_ENABLED=0 go build -o /template-loader ./cmd/loader

FROM alpine:3.23

RUN apk add --no-cache ca-certificates
COPY --from=builder /template-loader /template-loader
ENTRYPOINT ["/template-loader"]
