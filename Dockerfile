# syntax=docker/dockerfile:1

FROM docker.io/library/eclipse-temurin:11-jdk AS jdk11

FROM docker.io/library/gradle:9.7.1-jdk17 AS build
ARG TANK_ROYALE_COMMIT
WORKDIR /workspace
COPY --from=jdk11 /opt/java/openjdk /opt/java/openjdk-11
COPY gradle gradle
COPY gradlew gradlew.bat build.gradle.kts settings.gradle.kts gradle.properties ./
COPY src src
RUN test -n "$TANK_ROYALE_COMMIT" \
    && git clone --filter=blob:none https://github.com/robocode-dev/tank-royale.git /tank-royale \
    && git -C /tank-royale checkout "$TANK_ROYALE_COMMIT" \
    && ./gradlew --no-daemon --no-configuration-cache \
        -Dorg.gradle.java.installations.paths=/opt/java/openjdk,/opt/java/openjdk-11 \
        -PtankRoyaleSource=/tank-royale installDist

# The bot runtimes, each from its official source at the version the runtime list names; see docker/install-runtimes.sh.
# apt installs only tools and the libraries the runtimes need, never a runtime.
FROM docker.io/library/ubuntu:26.04 AS runtimes
ARG TARGETARCH
COPY src/main/resources/runtime-versions.properties /tmp/runtime-versions.properties
COPY docker/install-runtimes.sh /tmp/install-runtimes.sh
RUN set -eux \
    && apt-get update \
    && DEBIAN_FRONTEND=noninteractive apt-get install --yes --no-install-recommends \
        ca-certificates curl git jq libstdc++6 tzdata xz-utils zlib1g \
    && icu="$(apt-cache search --names-only '^libicu[0-9]+$' | cut -d ' ' -f 1 | sort -V | tail -n 1)" \
    && test -n "$icu" \
    && DEBIAN_FRONTEND=noninteractive apt-get install --yes --no-install-recommends "$icu" \
    && bash /tmp/install-runtimes.sh /tmp/runtime-versions.properties "$TARGETARCH" \
    && apt-get purge --yes --auto-remove jq \
    && rm -rf /var/lib/apt/lists/* /tmp/*

ENV JAVA_HOME=/opt/java
ENV DOTNET_ROOT=/opt/dotnet
ENV PATH="/opt/java/bin:/opt/dotnet:${PATH}"

# The Python Bot API wheel, built with the Python the runtime list names, in a build environment of its own.
FROM runtimes AS python-api
COPY --from=build /tank-royale/VERSION /tank-royale/VERSION
COPY --from=build /tank-royale/schema/schemas /tank-royale/schema/schemas
COPY --from=build /tank-royale/bot-api/python /tank-royale/bot-api/python
WORKDIR /tank-royale/bot-api/python
RUN /opt/python/bin/python3 -m venv /tmp/build-python \
    && /tmp/build-python/bin/python -m pip install --no-cache-dir --upgrade pip build PyYAML \
    && /tmp/build-python/bin/python scripts/update_version.py \
    && /tmp/build-python/bin/python scripts/schema_to_python.py \
        -d ../../schema/schemas \
        -o generated/robocode_tank_royale/schema \
    && /tmp/build-python/bin/python -m build --wheel --outdir /out

FROM runtimes
COPY --from=python-api /out/robocode_tank_royale-*.whl /tmp/

RUN set -eux \
    && /opt/python/bin/python3 -m venv /opt/rumble-python \
    && /opt/rumble-python/bin/python -m pip install --no-cache-dir /tmp/robocode_tank_royale-*.whl \
    && rm -rf /tmp/* \
    && groupadd --gid 10001 rumble \
    && useradd --uid 10001 --gid rumble --no-create-home --home-dir /tmp --shell /usr/sbin/nologin rumble

COPY --from=build --chown=10001:10001 /workspace/build/install/rumble-client /opt/rumble-client

ENV HOME=/tmp
ENV PATH="/opt/rumble-python/bin:${PATH}"
# Allow existing bots targeting an older .NET runtime to roll forward to the image SDK.
ENV DOTNET_ROLL_FORWARD=LatestMajor
WORKDIR /work
USER 10001:10001
ENTRYPOINT ["/opt/rumble-client/bin/rumble-client"]
CMD ["--help"]
