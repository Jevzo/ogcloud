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

FROM spring AS controller
# OgCloud's own plugins, which the controller puts into the runtime bundles (D-0079).
COPY --from=artifacts plugins/ /opt/ogcloud/plugins/

# Go services: static binaries on distroless static (D-0128).
FROM gcr.io/distroless/static-debian13@sha256:448810357cc8e67b7208988abdba0f5ec3d3aebee0a5b13eb624979792cffc0f AS go
ARG BINARY
ARG TARGETARCH
COPY --from=artifacts linux-${TARGETARCH}/${BINARY} /app/service
USER 1000:1000
ENTRYPOINT ["/app/service"]
