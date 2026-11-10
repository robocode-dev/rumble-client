---
id: CAP-006
type: design
status: active
links: [CAP-004]
title: How the image installs and refreshes its bot runtimes
---

# How the image installs and refreshes its bot runtimes

## One list

`src/main/resources/runtime-versions.properties` names a version line for each runtime: `java`, `dotnet`, `python` and `node`, plus `nodeInstaller`, the exact Node.js release. The Dockerfile, the native preflight (`--check-runtimes`), the workflows and the README's generated block read this file; nothing else names a runtime version. The Dockerfile's own build stages use a Gradle image with JDK 17 and a JDK 11 to compile the client; those are build tools, not bot runtimes.

## Installing the runtimes

The Dockerfile's `runtimes` stage starts from Ubuntu 26.04. apt installs only tools and libraries: certificates, `curl`, `git`, `jq` (removed again after the install), `xz-utils`, `tzdata`, the C++ runtime, zlib and the newest ICU library, which .NET needs. `docker/install-runtimes.sh` then installs each runtime from its official source and checks it before unpacking:

| Runtime | Source | How the file and its checksum are found | Installed in |
|---|---|---|---|
| Java | Eclipse Temurin JDK from Adoptium | Adoptium's API for the newest release of the version, which names the archive and its SHA-256 | `/opt/java`, with `JAVA_HOME` and `PATH` |
| .NET | .NET SDK from Microsoft's .NET release site | The channel's release metadata, whose `latest-sdk` names the SDK, its archive per platform and its SHA-512 | `/opt/dotnet`, with `DOTNET_ROOT` and `PATH` |
| Python | CPython, Astral's python-build-standalone `install_only_stripped` build | The project's `latest-release.json` names the release; its `SHA256SUMS` lists every build with its SHA-256, and the newest patch release of the version is taken | `/opt/python` |
| Node.js | nodejs.org | The release's `SHASUMS256.txt` | `/usr/local` |

A checksum that does not match, or that cannot be found, stops the build. The newest patch release of each version line is taken when the image is built, so two builds a month apart may hold different patch releases.

python.org publishes no Linux build that can be unpacked and run. Astral builds CPython from python.org's source as a relocatable build, the one `uv` installs. The bots' Python environment, `/opt/rumble-python`, is a virtual environment made from it with the Python Bot API installed, and is first on `PATH`. The Bot API wheel is built in the `python-api` stage with the same Python, in a build environment of its own, so the wheel's build tools never reach the image.

## Refreshing the list

`.github/workflows/update-runtimes.yml` runs on the first of each month and on demand. `scripts/update_runtime_versions.py` reads the official release feeds of Adoptium, Microsoft .NET, python.org and Node.js and chooses:

- Java, .NET and Node.js: the newest long-term release that is still supported.
- Python: the newest minor version whose x.y.1 release is published, because x.y.0 is followed within weeks by fixes, and builds for it appear over the following weeks.

When the list changes, the workflow builds the image, runs the container verification with battles on all four platforms and opens a pull request that a person reviews. Nothing is merged or published by itself.
