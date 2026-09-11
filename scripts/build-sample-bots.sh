#!/usr/bin/env bash
set -euo pipefail

TANK_ROYALE_SOURCE="$1"

(
    cd "${TANK_ROYALE_SOURCE}"
    ./gradlew --no-daemon --no-configuration-cache -x :bot-api:dotnet:test \
        :sample-bots:java:build :sample-bots:csharp:build :sample-bots:python:build :sample-bots:typescript:build
)
