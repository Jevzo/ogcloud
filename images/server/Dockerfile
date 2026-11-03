# Server images (D-0046, D-0128, D-0139): a shared base and one stage per platform. images/docker-bake.json,
# generated from contracts/runtime-profiles.yaml, picks the stage and passes the pinned jar and JVM flags.

# Temurin's JRE on Ubuntu; its shell runs the entrypoint (D-0128).
FROM eclipse-temurin:25.0.5_8-jre-noble@sha256:a98128a57de944a72bd31fbc79b898edd5a5864d3652071d5bbd2160ab7d7dce AS base
ARG SERVER_URL
ARG SERVER_SHA256
ARG SERVER_SOURCE
ARG JVM_FLAGS
# WORKDIR creates the directory; ADD would give it the jar's mode, leaving it unreadable for the server's user.
WORKDIR /opt/ogcloud/server
ADD --chmod=0644 --checksum=sha256:${SERVER_SHA256} ${SERVER_URL} server.jar
COPY --chmod=0755 start.sh /opt/ogcloud/start.sh
COPY licenses/ /licenses/
# The platform is GPL-3.0, so the image says where its exact source is (D-0046). /data belongs to the server's
# user (uid 1000, as the controller's pods run) for runs without a mounted volume.
RUN printf '%s\n' \
      "This image contains a server built from ${SERVER_SOURCE}," \
      "licensed under the GNU General Public License v3 (GPL-3.0.txt). Its complete corresponding source is" \
      "at that address; if it ever becomes unavailable there, ask the OgCloud maintainers for a copy at" \
      "https://github.com/Jevzo/ogcloud/issues." > /licenses/SOURCE.txt \
 && install -d -o 1000 -g 1000 /data
ENV DEFAULT_JVM_FLAGS="${JVM_FLAGS}"
WORKDIR /data
EXPOSE 25565
ENTRYPOINT ["/opt/ogcloud/start.sh"]

FROM base AS paper
# Paperclip fetches Mojang's server and patches it here, once, so pods never reach Mojang (D-0139).
RUN java -Dpaperclip.patchonly=true -DbundlerRepoDir=/opt/ogcloud/server -jar /opt/ogcloud/server/server.jar
ENV SERVER_ARGS="-DbundlerRepoDir=/opt/ogcloud/server -jar /opt/ogcloud/server/server.jar --nogui"
USER 1000:1000

FROM base AS pandaspigot
# PandaSpigot's older paperclip patches into cache/; the patched jar runs on its own, so only it is kept.
WORKDIR /opt/ogcloud/server
RUN java -Dpaperclip.patchonly=true -jar server.jar \
 && mv cache/patched_*.jar patched.jar \
 && rm -r cache server.jar \
 && mv patched.jar server.jar
WORKDIR /data
ENV SERVER_ARGS="-jar /opt/ogcloud/server/server.jar nogui"
USER 1000:1000

FROM base AS velocity
ENV SERVER_ARGS="-jar /opt/ogcloud/server/server.jar"
USER 1000:1000
