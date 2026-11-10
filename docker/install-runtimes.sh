#!/usr/bin/env bash
# Installs the bot runtimes that the runtime list names, each from its official source, and checks every download
# against the checksum that source publishes. A checksum that does not match, or that cannot be found, stops the build.
#
# Usage: install-runtimes.sh <runtime-versions.properties> <amd64|arm64>
#
#   Java     Eclipse Temurin JDK from Adoptium                 /opt/java
#   .NET     .NET SDK from Microsoft's .NET release site       /opt/dotnet
#   Python   CPython, Astral's python-build-standalone build   /opt/python
#   Node.js  Node.js from nodejs.org                           /usr/local
#
# The newest patch release of each version line in the list is installed. Needs bash, curl, jq, tar, xz and the
# sha256sum and sha512sum tools.
set -euo pipefail

list="$1"
arch="$2"

version_of() {
    local version
    version="$(sed -n "s/^$1=//p" "$list" | tr -d '\r')"
    if [ -z "$version" ]; then
        echo "The runtime list names no version for $1" >&2
        exit 1
    fi
    printf '%s' "$version"
}

java_version="$(version_of java)"
dotnet_version="$(version_of dotnet)"
python_version="$(version_of python)"
node_major="$(version_of node)"
node_version="$(version_of nodeInstaller)"
if [ "${node_version%%.*}" != "$node_major" ]; then
    echo "The Node.js installer $node_version is not of Node.js $node_major" >&2
    exit 1
fi

case "$arch" in
    amd64) adoptium_arch=x64; dotnet_rid=linux-x64; python_platform=x86_64-unknown-linux-gnu; node_arch=x64 ;;
    arm64) adoptium_arch=aarch64; dotnet_rid=linux-arm64; python_platform=aarch64-unknown-linux-gnu; node_arch=arm64 ;;
    *) echo "Unsupported architecture: $arch" >&2; exit 1 ;;
esac

work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT

fetch() {
    curl --fail --silent --show-error --location --proto '=https' --tlsv1.2 --retry 3 "$1" --output "$2"
}

# check <sha256|sha512> <published checksum> <file>
check() {
    if [ -z "$2" ] || [ "$2" = null ]; then
        echo "No published checksum was found for $3" >&2
        exit 1
    fi
    printf '%s  %s\n' "$2" "$3" | "$1sum" --check --strict -
}

# unpack <archive> <folder>: the archive's one top-level folder becomes the folder.
unpack() {
    mkdir -p "$2"
    tar --extract --file "$1" --directory "$2" --strip-components=1
}

# Java: Adoptium's API names the newest Temurin JDK of the version and its SHA-256 checksum.
fetch "https://api.adoptium.net/v3/assets/latest/${java_version}/hotspot?architecture=${adoptium_arch}&image_type=jdk&os=linux&vendor=eclipse" "$work/java.json"
java_url="$(jq -r '[.[] | .binary.package | select(.link | endswith(".tar.gz"))][0].link // empty' "$work/java.json")"
java_sum="$(jq -r '[.[] | .binary.package | select(.link | endswith(".tar.gz"))][0].checksum // empty' "$work/java.json")"
test -n "$java_url"
fetch "$java_url" "$work/java.tar.gz"
check sha256 "$java_sum" "$work/java.tar.gz"
unpack "$work/java.tar.gz" /opt/java

# .NET: Microsoft's release metadata for the channel names the newest SDK, its files and their SHA-512 checksums.
fetch "https://dotnetcli.blob.core.windows.net/dotnet/release-metadata/${dotnet_version}.0/releases.json" "$work/dotnet.json"
dotnet_sdk="$(jq -r '."latest-sdk" // empty' "$work/dotnet.json")"
test -n "$dotnet_sdk"
dotnet_file='[.releases[] | (.sdks // [.sdk])[] | select(.version == $sdk) | .files[] | select(.rid == $rid and (.name | endswith(".tar.gz")))][0]'
dotnet_url="$(jq -r --arg sdk "$dotnet_sdk" --arg rid "$dotnet_rid" "$dotnet_file | .url // empty" "$work/dotnet.json")"
dotnet_sum="$(jq -r --arg sdk "$dotnet_sdk" --arg rid "$dotnet_rid" "$dotnet_file | .hash // empty" "$work/dotnet.json")"
test -n "$dotnet_url"
fetch "$dotnet_url" "$work/dotnet.tar.gz"
check sha512 "$dotnet_sum" "$work/dotnet.tar.gz"
mkdir -p /opt/dotnet
tar --extract --file "$work/dotnet.tar.gz" --directory /opt/dotnet

# Python: python-build-standalone's latest release names its download folder, whose SHA256SUMS file lists every build
# with its checksum. The newest patch release of the Python version in the list is taken.
fetch "https://raw.githubusercontent.com/astral-sh/python-build-standalone/latest-release/latest-release.json" "$work/python-release.json"
python_tag="$(jq -r '.tag // empty' "$work/python-release.json")"
python_prefix="$(jq -r '.asset_url_prefix // empty' "$work/python-release.json")"
test -n "$python_tag"
case "$python_prefix" in
    https://github.com/astral-sh/python-build-standalone/releases/download/*) ;;
    *) echo "Unexpected python-build-standalone download folder: $python_prefix" >&2; exit 1 ;;
esac
fetch "$python_prefix/SHA256SUMS" "$work/SHA256SUMS"
python_pattern="^[0-9a-f]{64}  cpython-${python_version//./\\.}\\.[0-9]+\\+${python_tag}-${python_platform}-install_only_stripped\\.tar\\.gz$"
python_line="$(grep -E "$python_pattern" "$work/SHA256SUMS" | sort -b -V -k2 | tail -n 1 || true)"
if [ -z "$python_line" ]; then
    echo "python-build-standalone ${python_tag} has no build of Python ${python_version} for ${python_platform}" >&2
    exit 1
fi
python_sum="${python_line%%  *}"
python_name="${python_line#*  }"
fetch "$python_prefix/$python_name" "$work/python.tar.gz"
check sha256 "$python_sum" "$work/python.tar.gz"
unpack "$work/python.tar.gz" /opt/python
test -x "/opt/python/bin/python${python_version}"

# Node.js: nodejs.org lists every file of a release with its SHA-256 checksum.
node_archive="node-v${node_version}-linux-${node_arch}.tar.xz"
fetch "https://nodejs.org/dist/v${node_version}/${node_archive}" "$work/${node_archive}"
fetch "https://nodejs.org/dist/v${node_version}/SHASUMS256.txt" "$work/SHASUMS256.txt"
node_sum="$(awk -v name="$node_archive" '$2 == name { print $1 }' "$work/SHASUMS256.txt")"
check sha256 "$node_sum" "$work/${node_archive}"
tar --extract --xz --file "$work/${node_archive}" --directory /usr/local --strip-components=1

echo "Installed Java ${java_version} ($(basename "$java_url")), .NET SDK ${dotnet_sdk}, ${python_name%%+*} and Node.js ${node_version}"
