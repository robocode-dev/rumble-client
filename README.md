# Tank Royale Rumble Client

The Rumble Client runs local Tank Royale battles against the published Rumble catalog. Ranked mode validates the current engine and catalog pin, journals every completed result with replay evidence, and submits batches through the Rumble data repository's issue inbox. Practice mode never creates a ranked record or submission.

This project is part of the [Tank Royale](https://github.com/robocode-dev/tank-royale) ecosystem; the client's public contracts (configuration, journal, and submission format) are documented there.

## Quickstart (Docker, recommended)

Docker is the recommended way to build and run the client: it supplies the complete Java, .NET, Python, and Node.js environment every ranked bot needs, and is the isolation boundary for running reviewed-but-untrusted bot code. Docker Engine or Docker Desktop is required for this path.

1. Clone this repository.
2. Build the image: `docker build --tag rumble-client:dev .`
3. Copy `rumble-client.example.json` to `rumble-client.json` and edit it — see [Configuration](#configuration) below. Never commit the resulting file.
4. Check your settings: `docker/rumble.sh validate rumble-client.json`
5. Check the bundled runtimes: `docker/rumble.sh runtimes`
6. Resolve the ranked catalog and prepare the bot cache: `docker/rumble.sh sync rumble-client.json`
7. Run one ranked battle: `docker/rumble.sh run rumble-client.json`
8. Submit pending results: `export RUMBLE_CLIENT_TOKEN=<your token>` then `docker/rumble.sh submit rumble-client.json`

On PowerShell, use `docker/rumble.ps1 <validate|runtimes|sync|run|submit> [config-path] [image]` instead — for example `docker/rumble.ps1 run rumble-client.json`, and set `$env:RUMBLE_CLIENT_TOKEN` before `submit`.

Every command runs the container read-only, with capabilities dropped and resource limits applied. Only `runtimes` blocks network access outright; `validate`, `sync`, `run`, and `submit` use normal outbound network because synchronization and submission need it, and `run` re-synchronizes the ranked snapshot before executing a battle.

The client tracks posted batches locally and only drops them once their receipt comment appears on the closed issue; retrying an already-accepted submission is acknowledged idempotently rather than double-submitted.

## Building `rumble-client` itself

Most contributors only need the Quickstart above. If you're changing this repository's own Java code, you need to build and test it, which still needs Gradle — but not installed on your machine. Run it inside a Gradle image matching this repository's pinned wrapper version (`gradle/wrapper/gradle-wrapper.properties`, currently 9.6.1), with your checkout bind-mounted:

```shell
docker run --rm -it -v "${PWD}:/workspace" -w /workspace gradle:9.6.1-jdk17 gradle build
```

The same command works unchanged on PowerShell. Note this is a different Gradle version than the `gradle:8.14.3-jdk17` image the `Dockerfile`'s own build stage starts from — that stage still runs `./gradlew` inside it precisely so the wrapper's pinned 9.6.1 is what actually builds the release, regardless of the base image's bundled version. Keep the two in sync if either changes.

This repository currently depends on an unreleased Tank Royale Battle Runner version, built from a local Tank Royale checkout rather than a published Maven artifact — that's why CI and the `Dockerfile`'s own build stage pass `-PtankRoyaleSource=<path>`. To build against a local Tank Royale checkout the same way, mount it alongside your `rumble-client` checkout and add that property:

```shell
docker run --rm -it -v "${PWD}:/workspace" -v "${PWD}/../tank-royale:/tank-royale" -w /workspace gradle:9.6.1-jdk17 gradle -PtankRoyaleSource=/tank-royale build
```

This dependency becomes an ordinary published Maven Central artifact once Tank Royale releases the Battle Runner version this repository pins in `gradle.properties` — at that point this source-mount step stops being necessary.

If you already have JDK 17 and Gradle installed on your machine, the equivalent host commands work identically: `./gradlew build`, or `./gradlew -PtankRoyaleSource=../tank-royale build`.

The build produces native ZIP and TAR archives under `build/distributions/`. Run `./gradlew run --args="--check-runtimes"` to verify the required native installations; the check never installs or changes them.

<!-- runtime-versions:start -->
The container and native preflight currently target Java 25, .NET 10, Python 3.14, and Node.js 24 (Node.js installer 24.21.0). This block is refreshed by the scheduled runtime update workflow.
<!-- runtime-versions:end -->

The client validates configuration and can synchronize the current ranked input snapshot. Run `./gradlew run --args="--validate-config"` to check local settings, then run `./gradlew run --args="--sync"` to resolve the canonical data repository, validate its engine pin, catalog, client registration, and matchmaking advice, and prepare an immutable bot cache at the catalog's exact source commit. Every cached source tree is checked against its catalog SHA-256 before it can be used. Ranked battle selection uses a recorded random seed, prioritizes under-sampled pairings involving `myBots`, and falls back to distinct active catalog bots when no advice is available. Each game type declares how many bots one catalog entry expands to, so TwinDuel selects two team entries for its four pinned participants while `1v1` and melee select individual bots, and a selection never contains two entries that share a member bot. Run `./gradlew run --args="--run"` to execute one pinned ranked battle through Battle Runner and retain its replay evidence locally. Run `./gradlew run --args="--submit"` to post pending records through the `rumble-data` issue inbox. It reads `RUMBLE_CLIENT_TOKEN` only at runtime; use a GitHub fine-grained token limited to read and write Issues access for that repository. The client records posted batches locally and removes records only after their result-data receipt comments appear. See the Docker and Podman development image section below for the isolated multi-runtime container.

## Configuration

Copy `rumble-client.example.json` to `rumble-client.json`. Ranked mode requires a registered `clientId` — see [`rumble-data`'s contributing guide](https://github.com/robocode-dev/rumble-data/blob/main/CONTRIBUTING.md) for the one-time registration pull request; practice mode may omit it. The optional `workDirectory` selects the local cache, journal, and replay-evidence root and defaults to `.rumble-client` beside the configuration file. Do not commit the resulting file or any token.

## Docker and Podman development image

The non-published development image can be built and run with Docker Engine, Docker Desktop, or Podman. The examples below use Docker; replace `docker` with `podman` when invoking the image directly. On Windows, Podman Desktop needs a running Linux virtual machine and can use WSL2 or Hyper-V as the provider; choose the provider when creating the machine. Podman Desktop/WSL2 on Windows and rootless Podman on Linux have both been manually verified for this image; neither is part of CI.

Two flag differences from Docker are handled for you by the launcher scripts and do not need manual workarounds:

- The `Dockerfile`'s base images are fully qualified (`docker.io/library/...`) because a stock Podman install has no default unqualified-search-registry, unlike Docker's implicit Docker Hub default.
- `docker/rumble.sh` and `docker/rumble.ps1` add `--userns=keep-id` only when the selected engine is Podman, so the bind-mounted `.rumble-client` state directory stays writable and correctly owned by the invoking host user. Rootless Podman's `--user` does not map to the host UID inside the container's user namespace the way Docker's does; without `--userns=keep-id` the state directory is unwritable.

Build the image with one of these commands:

```shell
docker build --tag rumble-client:dev .
podman build --tag rumble-client:dev .
```

To run the four-language container smoke check locally, build the sample-bot archives and run `CONTAINER_ENGINE=podman TANK_ROYALE_SOURCE=../tank-royale bash scripts/verify-container.sh`; Docker is the default engine.

Use the launcher scripts for configuration validation, runtime checks, snapshot synchronization, ranked battles, and result submission. The shell launcher selects Docker by default and accepts `CONTAINER_ENGINE=podman`; the PowerShell launcher accepts `-Engine podman` or the same `CONTAINER_ENGINE` environment variable:

```shell
./docker/rumble.sh runtimes
CONTAINER_ENGINE=podman ./docker/rumble.sh runtimes
./docker/rumble.sh run rumble-client.json
export RUMBLE_CLIENT_TOKEN=<your token>
./docker/rumble.sh submit rumble-client.json
```

```powershell
./docker/rumble.ps1 runtimes
./docker/rumble.ps1 runtimes -Engine podman
$env:CONTAINER_ENGINE = 'podman'
./docker/rumble.ps1 runtimes
./docker/rumble.ps1 run rumble-client.json
$env:RUMBLE_CLIENT_TOKEN = '<your token>'
./docker/rumble.ps1 submit rumble-client.json
```

For `validate`, `sync`, `run`, and `submit`, the launcher mounts the configuration file and `.rumble-client` state directory. Keep the state directory writable; it contains the bot cache, journal, and replay evidence. The `runtimes` check uses no network, while `validate`, `sync`, and `run` require network access to synchronize the configured repositories and `submit` requires network access to the GitHub Issues API. Submission forwards `RUMBLE_CLIENT_TOKEN` from the environment and never writes it to disk.

The image contains a pinned Tank Royale Python API and its runtime dependencies in an image-owned virtual environment; no host Python environment or package installation is required. When running bot archives directly through a containerized Battle Runner, mount the archive read-only for Java and Python. C# and TypeScript first-run dependency setup may need to write and change file permissions, so copy those archives into writable container storage such as `/tmp` before booting them. This is especially important for Windows bind mounts, where `chmod` can fail with `EPERM`.

If Podman Desktop on Windows reports `ssh-keygen` cannot be found, install or enable Windows OpenSSH and add the OpenSSH installation directory to the user `PATH`, then restart the terminal and Podman Desktop.

## Contributing

Read [CONTRIBUTING.md](CONTRIBUTING.md), [SECURITY.md](SECURITY.md), and [GOVERNANCE.md](GOVERNANCE.md) before opening a pull request.
