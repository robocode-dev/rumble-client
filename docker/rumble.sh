#!/usr/bin/env sh
set -eu

usage() {
    echo "Usage: docker/rumble.sh <validate|runtimes|sync> [config-path] [image]" >&2
    exit 2
}

command_name="${1:-}"
config_path="${2:-rumble-client.json}"
image="${3:-rumble-client:dev}"

case "$command_name" in
    validate) client_arguments="--validate-config /work/rumble-client.json" ;;
    runtimes) client_arguments="--check-runtimes" ;;
    sync) client_arguments="--sync /work/rumble-client.json" ;;
    *) usage ;;
esac

if [ "$command_name" = "runtimes" ]; then
    exec docker run --rm --read-only --network none --tmpfs /tmp:rw,nosuid,nodev,size=1g \
        --user "$(id -u):$(id -g)" \
        --cpus 4 --memory 8g --pids-limit 512 --cap-drop ALL --security-opt no-new-privileges \
        "$image" --check-runtimes
fi

config_directory=$(CDPATH= cd -- "$(dirname -- "$config_path")" && pwd)
config_name=$(basename -- "$config_path")
absolute_config="$config_directory/$config_name"
state_directory="$config_directory/.rumble-client"
mkdir -p "$state_directory"

exec docker run --rm --read-only --tmpfs /tmp:rw,nosuid,nodev,size=1g \
    --user "$(id -u):$(id -g)" \
    --cpus 4 --memory 8g --pids-limit 512 --cap-drop ALL --security-opt no-new-privileges \
    --mount "type=bind,source=$absolute_config,target=/work/rumble-client.json,readonly" \
    --mount "type=bind,source=$state_directory,target=/work/.rumble-client" \
    "$image" $client_arguments
