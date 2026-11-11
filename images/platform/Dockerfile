# Platform images (D-0128): thin layers over the artifacts `mise run images:build` stages in build/images with the
# toolchains mise pins, so no build tool version lives here. images/docker-bake.hcl picks the stage per image.

# Spring Boot services, unpacked into Spring's layers so unchanged dependencies are shared between versions.
FROM gcr.io/distroless/java25-debian13@sha256:d72c7b79a8c4babd6a69cf00979c9eed7f87e3e2b7d6871c6a0e9f91ff626cea AS spring
ARG SERVICE
WORKDIR /app
COPY --from=artifacts ${SERVICE}/dependencies/ ./
COPY --from=artifacts ${SERVICE}/spring-boot-loader/ ./
COPY --from=artifacts ${SERVICE}/snapshot-dependencies/ ./
COPY --from=artifacts ${SERVICE}/application/ ./
USER 1000:1000
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "org.springframework.boot.loader.launch.JarLauncher"]

FROM spring AS api
# The branded login pages the API serves (D-0212), built from apps/web.
COPY --from=artifacts login/ /app/login/

FROM spring AS controller
# OgCloud's own plugins, which the controller puts into the runtime bundles (D-0079).
COPY --from=artifacts plugins/ /opt/ogcloud/plugins/

FROM gcr.io/distroless/static-debian13@sha256:448810357cc8e67b7208988abdba0f5ec3d3aebee0a5b13eb624979792cffc0f AS static

# Go services: static binaries on distroless static (D-0128).
FROM static AS go
ARG BINARY
ARG TARGETARCH
COPY --from=artifacts linux-${TARGETARCH}/${BINARY} /app/service
USER 1000:1000
ENTRYPOINT ["/app/service"]

# Caddy's image grants its binary a file capability, which a pod that drops every capability cannot execute; a plain
# copy leaves the capability behind.
FROM caddy:2.11.9@sha256:0c2243efcf437ab7f7a978f1027fcf0d4f9be8a75df27b1b3fbc5dbe0ffa8cf0 AS caddy
RUN cp /usr/bin/caddy /tmp/caddy

# The dashboard (D-0214): Caddy's static binary on distroless static, serving the web app's build.
FROM static AS web
COPY --from=caddy /tmp/caddy /usr/bin/caddy
COPY web/Caddyfile /etc/caddy/Caddyfile
COPY --from=artifacts web/ /srv/
# Caddy wants somewhere to write even with nothing to keep; the root filesystem is read-only, /tmp is not.
ENV XDG_CONFIG_HOME=/tmp/caddy XDG_DATA_HOME=/tmp/caddy
USER 1000:1000
ENTRYPOINT ["/usr/bin/caddy", "run", "--config", "/etc/caddy/Caddyfile", "--adapter", "caddyfile"]
