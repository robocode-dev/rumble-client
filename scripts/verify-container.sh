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

TANK_ROYALE_COMMIT="$(tr -d '[:space:]' < TANK_ROYALE_COMMIT)"

"${CONTAINER_ENGINE}" build --tag "${IMAGE_TAG}" --build-arg "TANK_ROYALE_COMMIT=${TANK_ROYALE_COMMIT}" .
"${CONTAINER_ENGINE}" run --rm --read-only --network none --tmpfs /tmp:rw,nosuid,nodev,noexec,size=1g \
    --cap-drop ALL --security-opt no-new-privileges "${IMAGE_TAG}" --check-runtimes
test "$("${CONTAINER_ENGINE}" run --rm --entrypoint id "${IMAGE_TAG}" -u)" != "0"

RUNTIME_UID=10001
RUNTIME_GID=10001

DOTNET_PREP_VOLUME="rumble-client-smoke-dotnet-$$"
TYPESCRIPT_PREP_VOLUME="rumble-client-smoke-typescript-$$"

cleanup_prep_volumes() {
    "${CONTAINER_ENGINE}" volume rm "${DOTNET_PREP_VOLUME}" "${TYPESCRIPT_PREP_VOLUME}" >/dev/null 2>&1 || true
}

trap cleanup_prep_volumes EXIT

own_prep_volume() {
    local volume_name="$1"

    "${CONTAINER_ENGINE}" run --rm --read-only --network none --tmpfs /tmp:rw,nosuid,nodev,noexec,size=1g \
        --cap-drop ALL --security-opt no-new-privileges --user 0:0 \
        --mount "type=volume,source=${volume_name},target=/work/bot" \
        --entrypoint chown "${IMAGE_TAG}" \
        "${RUNTIME_UID}:${RUNTIME_GID}" /work/bot
}

prepare_dotnet_archive() {
    "${CONTAINER_ENGINE}" volume create "${DOTNET_PREP_VOLUME}" >/dev/null
    own_prep_volume "${DOTNET_PREP_VOLUME}"
    "${CONTAINER_ENGINE}" run --rm --read-only --tmpfs /tmp:rw,nosuid,nodev,noexec,size=1g \
        --cap-drop ALL --security-opt no-new-privileges --user "${RUNTIME_UID}:${RUNTIME_GID}" \
        --mount "type=bind,source=${TANK_ROYALE_SOURCE}/sample-bots/csharp/build/archive,target=/mnt/source,readonly" \
        --mount "type=volume,source=${DOTNET_PREP_VOLUME},target=/work/bot" \
        --entrypoint sh "${IMAGE_TAG}" \
        -c 'set -eu; cp -r /mnt/source/. /work/bot/; dotnet restore /work/bot/Target/Target.csproj --packages /work/bot/.nuget'
}

prepare_typescript_archive() {
    "${CONTAINER_ENGINE}" volume create "${TYPESCRIPT_PREP_VOLUME}" >/dev/null
    own_prep_volume "${TYPESCRIPT_PREP_VOLUME}"
    "${CONTAINER_ENGINE}" run --rm --read-only --tmpfs /tmp:rw,nosuid,nodev,noexec,size=1g \
        --cap-drop ALL --security-opt no-new-privileges --user "${RUNTIME_UID}:${RUNTIME_GID}" \
        --mount "type=bind,source=${TANK_ROYALE_SOURCE}/sample-bots/typescript/build/archive,target=/mnt/source,readonly" \
        --mount "type=volume,source=${TYPESCRIPT_PREP_VOLUME},target=/work/bot" \
        --entrypoint sh "${IMAGE_TAG}" \
        -c 'set -eu; cp -r /mnt/source/. /work/bot/; cd /work/bot; npm install --prefer-offline; touch deps/.deps_installed'
}

run_readonly_smoke() {
    local first_language="$1"
    local first_bot="$2"
    local second_language="$3"
    local second_bot="$4"

    "${CONTAINER_ENGINE}" run --rm --read-only --network none --tmpfs /tmp:rw,nosuid,nodev,noexec,size=1g \
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
    local -a first_mount
    local -a runtime_env=()

    if [ "${first_language}" = csharp ]; then
        first_mount=(--mount "type=volume,source=${DOTNET_PREP_VOLUME},target=/mnt/bots-one,readonly")
        runtime_env=(--env "NUGET_PACKAGES=/tmp/bots-one/.nuget")
    elif [ "${first_language}" = typescript ]; then
        first_mount=(--mount "type=volume,source=${TYPESCRIPT_PREP_VOLUME},target=/mnt/bots-one,readonly")
    else
        first_mount=(--mount "type=bind,source=${TANK_ROYALE_SOURCE}/sample-bots/${first_language}/build/archive,target=/mnt/bots-one,readonly")
    fi

    "${CONTAINER_ENGINE}" run --rm --read-only --network none --tmpfs /tmp:rw,exec,nosuid,nodev,size=1g \
        --cap-drop ALL --security-opt no-new-privileges \
        "${first_mount[@]}" \
        --mount "type=bind,source=${TANK_ROYALE_SOURCE}/sample-bots/${second_language}/build/archive,target=/mnt/bots-two,readonly" \
        --mount "type=bind,source=${SMOKE_SCRIPT},target=/work/smoke-battle.jsh,readonly" \
        --env "SMOKE_BOT_ONE=/tmp/bots-one/${first_bot}" \
        --env "SMOKE_BOT_TWO=/tmp/bots-two/${second_bot}" \
        ${runtime_env[@]+"${runtime_env[@]}"} \
        --entrypoint sh "${IMAGE_TAG}" \
        -c 'set -eu; cp -r /mnt/bots-one /tmp/bots-one; cp -r /mnt/bots-two /tmp/bots-two; chmod -R u+rw /tmp/bots-one /tmp/bots-two; find /tmp/bots-one /tmp/bots-two -type f -name "*.sh" -exec chmod u+x {} +; find /tmp/bots-one /tmp/bots-two -type f -path "*/bin/Release/*" -exec chmod u+x {} +; jshell --class-path "/opt/rumble-client/lib/*" /work/smoke-battle.jsh; test -f /tmp/rumble-smoke-success'
}

prepare_dotnet_archive
prepare_typescript_archive
run_readonly_smoke python Target java Walls
run_readonly_smoke java Target python Walls
run_writable_smoke csharp Target java Walls
run_writable_smoke typescript Target java Walls
