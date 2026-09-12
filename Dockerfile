# syntax=docker/dockerfile:1

FROM docker.io/library/eclipse-temurin:11-jdk AS jdk11

FROM docker.io/library/gradle:8.14.3-jdk17 AS build
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

FROM docker.io/library/python:3.14-slim AS python-api
COPY --from=build /tank-royale/VERSION /tank-royale/VERSION
COPY --from=build /tank-royale/schema/schemas /tank-royale/schema/schemas
COPY --from=build /tank-royale/bot-api/python /tank-royale/bot-api/python
WORKDIR /tank-royale/bot-api/python
RUN python -m pip install --no-cache-dir --upgrade pip build PyYAML \
    && python scripts/update_version.py \
    && python scripts/schema_to_python.py \
        -d ../../schema/schemas \
        -o generated/robocode_tank_royale/schema \
    && python -m build --wheel --outdir /out

FROM docker.io/library/ubuntu:26.04
ARG TARGETARCH
COPY src/main/resources/runtime-versions.properties /tmp/runtime-versions.properties
COPY --from=python-api /out/robocode_tank_royale-*.whl /tmp/

RUN set -eux \
    && JAVA_VERSION="$(sed -n 's/^java=//p' /tmp/runtime-versions.properties | tr -d '\r')" \
    && DOTNET_VERSION="$(sed -n 's/^dotnet=//p' /tmp/runtime-versions.properties | tr -d '\r')" \
    && PYTHON_VERSION="$(sed -n 's/^python=//p' /tmp/runtime-versions.properties | tr -d '\r')" \
    && NODE_MAJOR="$(sed -n 's/^node=//p' /tmp/runtime-versions.properties | tr -d '\r')" \
    && PYTHON_BIN="/usr/bin/python${PYTHON_VERSION}" \
    && NODE_VERSION="$(sed -n 's/^nodeInstaller=//p' /tmp/runtime-versions.properties | tr -d '\r')" \
    && test -n "$JAVA_VERSION" \
    && test -n "$DOTNET_VERSION" \
    && test -n "$PYTHON_VERSION" \
    && test -n "$NODE_MAJOR" \
    && test -n "$NODE_VERSION" \
    && test "${NODE_VERSION%%.*}" = "$NODE_MAJOR" \
    && apt-get update \
    && DEBIAN_FRONTEND=noninteractive apt-get install --yes --no-install-recommends \
        ca-certificates curl "dotnet-sdk-${DOTNET_VERSION}.0" git \
        "openjdk-${JAVA_VERSION}-jdk-headless" "python${PYTHON_VERSION}" "python${PYTHON_VERSION}-venv" xz-utils \
    && case "$TARGETARCH" in amd64) node_arch=x64 ;; arm64) node_arch=arm64 ;; *) exit 1 ;; esac \
    && node_archive="node-v${NODE_VERSION}-linux-${node_arch}.tar.xz" \
    && curl --fail --location --proto '=https' --tlsv1.2 \
        "https://nodejs.org/dist/v${NODE_VERSION}/${node_archive}" --output "/tmp/${node_archive}" \
    && curl --fail --location --proto '=https' --tlsv1.2 \
        "https://nodejs.org/dist/v${NODE_VERSION}/SHASUMS256.txt" --output /tmp/SHASUMS256.txt \
    && grep " ${node_archive}$" /tmp/SHASUMS256.txt | (cd /tmp && sha256sum --check --strict -) \
    && tar --extract --xz --file "/tmp/${node_archive}" --directory /usr/local --strip-components=1 \
    && test -x "$PYTHON_BIN" \
    && "$PYTHON_BIN" -m venv /opt/rumble-python \
    && /opt/rumble-python/bin/python -m pip install --no-cache-dir /tmp/robocode_tank_royale-*.whl \
    && ln -sfn python /opt/rumble-python/bin/python3 \
    && ln -sfn "$PYTHON_BIN" "/opt/rumble-python/bin/python${PYTHON_VERSION}" \
    && rm -rf /var/lib/apt/lists/* /tmp/* \
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
