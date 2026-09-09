#!/usr/bin/env bash
set -euo pipefail

IMAGE_TAG="${IMAGE_TAG:-rumble-client:test}"
TANK_ROYALE_SOURCE="${TANK_ROYALE_SOURCE:-../tank-royale}"
SMOKE_SCRIPT="${PWD}/docker/smoke-battle.jsh"
CONTAINER_ENGINE="${CONTAINER_ENGINE:-docker}"

command -v "${CONTAINER_ENGINE}" >/dev/null || {
    echo "Container engine not found: ${CONTAINER_ENGINE}" >&2
    exit 1
}

"${CONTAINER_ENGINE}" build --tag "${IMAGE_TAG}" .
"${CONTAINER_ENGINE}" run --rm --read-only --network none --tmpfs /tmp:rw,nosuid,nodev,size=1g \
    --cap-drop ALL --security-opt no-new-privileges "${IMAGE_TAG}" --check-runtimes
test "$("${CONTAINER_ENGINE}" run --rm --entrypoint id "${IMAGE_TAG}" -u)" != "0"

run_readonly_smoke() {
    local first_language="$1"
    local first_bot="$2"
    local second_language="$3"
    local second_bot="$4"

    "${CONTAINER_ENGINE}" run --rm --read-only --network none --tmpfs /tmp:rw,nosuid,nodev,size=1g \
        --cap-drop ALL --security-opt no-new-privileges \
        --mount "type=bind,source=${TANK_ROYALE_SOURCE}/sample-bots/${first_language}/build/archive,target=/work/bots/one,readonly" \
        --mount "type=bind,source=${TANK_ROYALE_SOURCE}/sample-bots/${second_language}/build/archive,target=/work/bots/two,readonly" \
        --mount "type=bind,source=${SMOKE_SCRIPT},target=/work/smoke-battle.jsh,readonly" \
        --env "SMOKE_BOT_ONE=/work/bots/one/${first_bot}" \
        --env "SMOKE_BOT_TWO=/work/bots/two/${second_bot}" \
        --entrypoint sh "${IMAGE_TAG}" \
        -c 'set -eu; jshell --class-path "/opt/rumble-client/lib/*" /work/smoke-battle.jsh; test -f /tmp/rumble-smoke-success'
}

run_writable_smoke() {
    local first_language="$1"
    local first_bot="$2"
    local second_language="$3"
    local second_bot="$4"

    "${CONTAINER_ENGINE}" run --rm --tmpfs /tmp:rw,exec,nosuid,nodev,size=1g \
        --cap-drop ALL --security-opt no-new-privileges \
        --mount "type=bind,source=${TANK_ROYALE_SOURCE}/sample-bots/${first_language}/build/archive,target=/mnt/bots-one,readonly" \
        --mount "type=bind,source=${TANK_ROYALE_SOURCE}/sample-bots/${second_language}/build/archive,target=/mnt/bots-two,readonly" \
        --mount "type=bind,source=${SMOKE_SCRIPT},target=/work/smoke-battle.jsh,readonly" \
        --env "SMOKE_BOT_ONE=/tmp/bots-one/${first_bot}" \
        --env "SMOKE_BOT_TWO=/tmp/bots-two/${second_bot}" \
        --entrypoint sh "${IMAGE_TAG}" \
        -c 'set -eu; cp -a /mnt/bots-one /tmp/bots-one; cp -a /mnt/bots-two /tmp/bots-two; chmod -R u+rw /tmp/bots-one /tmp/bots-two; find /tmp/bots-one /tmp/bots-two -type f -name "*.sh" -exec chmod u+x {} +; find /tmp/bots-one /tmp/bots-two -type f -path "*/bin/Release/*" -exec chmod u+x {} +; jshell --class-path "/opt/rumble-client/lib/*" /work/smoke-battle.jsh; test -f /tmp/rumble-smoke-success'
}

run_readonly_smoke python Target java Walls
run_readonly_smoke java Target python Walls
run_writable_smoke csharp Target java Walls
run_writable_smoke typescript Target java Walls
