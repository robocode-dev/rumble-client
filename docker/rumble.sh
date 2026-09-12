#!/usr/bin/env sh
set -eu

usage() {
    echo "Usage: CONTAINER_ENGINE=docker|podman docker/rumble.sh <validate|runtimes|sync|run|submit> [config-path] [image]" >&2
    exit 2
}

command_name="${1:-}"
config_path="${2:-rumble-client.json}"
image="${3:-rumble-client:dev}"
container_engine="${CONTAINER_ENGINE:-docker}"

case "${container_engine}" in
    docker|podman) ;;
    *) usage ;;
esac

# Rootless Podman's --user does not map to the invoking host UID inside the
# container's user namespace (it resolves through the subuid map instead), so
# a bind-mounted state directory owned by the host user is otherwise
# unwritable. --userns=keep-id closes that gap; Docker has no such flag and
# does not need one, since its --user maps directly.
userns_args=""
if [ "${container_engine}" = "podman" ]; then
    userns_args="--userns=keep-id"
fi

case "${command_name}" in
    validate) client_arguments="--validate-config /work/rumble-client.json" ;;
    runtimes) client_arguments="--check-runtimes" ;;
    sync) client_arguments="--sync /work/rumble-client.json" ;;
    # --run re-synchronizes before executing a battle (same as --sync), so unlike
    # runtimes/validate it cannot be run with --network none here.
    run) client_arguments="--run /work/rumble-client.json" ;;
    submit) client_arguments="--submit /work/rumble-client.json" ;;
    *) usage ;;
esac

if [ "${command_name}" = "runtimes" ]; then
    exec "${container_engine}" run --rm --read-only --network none --tmpfs /tmp:rw,nosuid,nodev,size=1g \
        --user "$(id -u):$(id -g)" ${userns_args} \
        --cpus 4 --memory 8g --pids-limit 512 --cap-drop ALL --security-opt no-new-privileges \
        "${image}" --check-runtimes
fi

config_directory=$(CDPATH= cd -- "$(dirname -- "${config_path}")" && pwd)
config_name=$(basename -- "${config_path}")
absolute_config="${config_directory}/${config_name}"
state_directory="${config_directory}/.rumble-client"
mkdir -p "${state_directory}"

env_args=""
if [ "${command_name}" = "submit" ]; then
    : "${RUMBLE_CLIENT_TOKEN:?RUMBLE_CLIENT_TOKEN must be set in the environment to submit results}"
    env_args="--env RUMBLE_CLIENT_TOKEN"
fi

exec "${container_engine}" run --rm --read-only --tmpfs /tmp:rw,nosuid,nodev,size=1g \
    --user "$(id -u):$(id -g)" ${userns_args} \
    --cpus 4 --memory 8g --pids-limit 512 --cap-drop ALL --security-opt no-new-privileges \
    --mount "type=bind,source=${absolute_config},target=/work/rumble-client.json,readonly" \
    --mount "type=bind,source=${state_directory},target=/work/.rumble-client" \
    ${env_args} \
    "${image}" ${client_arguments}
