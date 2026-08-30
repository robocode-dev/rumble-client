# syntax=docker/dockerfile:1

FROM gradle:8.14.3-jdk17 AS build
ARG TANK_ROYALE_COMMIT=fd06b97a61c9aa264e6964520a30262f8f8be751
WORKDIR /workspace
COPY gradle gradle
COPY gradlew gradlew.bat build.gradle.kts settings.gradle.kts gradle.properties ./
COPY src src
RUN git clone --filter=blob:none https://github.com/robocode-dev/tank-royale.git /tank-royale \
    && git -C /tank-royale checkout "$TANK_ROYALE_COMMIT" \
    && ./gradlew --no-daemon --no-configuration-cache -PtankRoyaleSource=/tank-royale installDist

FROM ubuntu:24.04
ARG TARGETARCH
COPY src/main/resources/runtime-versions.properties /tmp/runtime-versions.properties

RUN apt-get update \
    && DEBIAN_FRONTEND=noninteractive apt-get install --yes --no-install-recommends \
        ca-certificates curl dotnet-sdk-8.0 git openjdk-17-jdk-headless python3.12 xz-utils \
    && NODE_VERSION="$(sed -n 's/^nodeInstaller=//p' /tmp/runtime-versions.properties)" \
    && test -n "$NODE_VERSION" \
    && case "$TARGETARCH" in amd64) node_arch=x64 ;; arm64) node_arch=arm64 ;; *) exit 1 ;; esac \
    && node_archive="node-v${NODE_VERSION}-linux-${node_arch}.tar.xz" \
    && curl --fail --location --proto '=https' --tlsv1.2 \
        "https://nodejs.org/dist/v${NODE_VERSION}/${node_archive}" --output "/tmp/${node_archive}" \
    && curl --fail --location --proto '=https' --tlsv1.2 \
        "https://nodejs.org/dist/v${NODE_VERSION}/SHASUMS256.txt" --output /tmp/SHASUMS256.txt \
    && grep " ${node_archive}$" /tmp/SHASUMS256.txt | (cd /tmp && sha256sum --check --strict -) \
    && tar --extract --xz --file "/tmp/${node_archive}" --directory /usr/local --strip-components=1 \
    && rm -rf /var/lib/apt/lists/* /tmp/* \
    && groupadd --gid 10001 rumble \
    && useradd --uid 10001 --gid rumble --no-create-home --home-dir /tmp --shell /usr/sbin/nologin rumble

COPY --from=build --chown=10001:10001 /workspace/build/install/rumble-client /opt/rumble-client

ENV HOME=/tmp
WORKDIR /work
USER 10001:10001
ENTRYPOINT ["/opt/rumble-client/bin/rumble-client"]
CMD ["--help"]
