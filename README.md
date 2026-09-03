# Tank Royale Rumble Client

The Rumble Client runs local Tank Royale battles against the published Rumble catalog. Ranked mode validates the current engine and catalog pin, journals every completed result with replay evidence, and submits batches through the Rumble data repository's issue inbox. Practice mode never creates a ranked record or submission.

This project is part of the [Tank Royale](https://github.com/robocode-dev/tank-royale) ecosystem; the client's public contracts (configuration, journal, and submission format) are documented there.

## Quickstart (Docker, recommended)

Docker is the recommended way to build and run the client: it supplies the complete Java, .NET, Python, and Node.js environment every ranked bot needs, and is the isolation boundary for running reviewed-but-untrusted bot code. Docker Engine or Docker Desktop is required; nothing else.

1. Clone this repository.
2. Build the image: `docker build --tag rumble-client:dev .`
3. Copy `rumble-client.example.json` to `rumble-client.json` and edit it — see [Configuration](#configuration) below. Never commit the resulting file.
4. Check your settings: `docker/rumble.sh validate rumble-client.json`
5. Check the bundled runtimes: `docker/rumble.sh runtimes`
6. Resolve the ranked catalog and prepare the bot cache: `docker/rumble.sh sync rumble-client.json`
7. Run one ranked battle: `docker/rumble.sh run rumble-client.json`
8. Submit pending results: `export RUMBLE_CLIENT_TOKEN=<your token>` then `docker/rumble.sh submit rumble-client.json`

On PowerShell, use `docker/rumble.ps1 <validate|runtimes|sync|run|submit> [config-path] [image]` instead — for example `docker/rumble.ps1 run rumble-client.json`, and set `$env:RUMBLE_CLIENT_TOKEN` before `submit`.

Every command runs the container read-only, with capabilities dropped and resource limits applied. Only `runtimes` also blocks network access outright; `validate`, `sync`, `run`, and `submit` all run with normal outbound network available. `run` needs it because it re-synchronizes the ranked snapshot before executing a battle (the same step `sync` performs) — so full network isolation during battle execution isn't available through this launcher without decoupling that resync step from `--run` in the client itself, which hasn't been done yet. `submit` needs it to reach the GitHub Issues API, and reads `RUMBLE_CLIENT_TOKEN` from your environment — use a GitHub fine-grained token limited to read/write Issues access on the Rumble data repository, and never write it to disk or commit it.

The client tracks posted batches locally and only drops them once their receipt comment appears on the closed issue; retrying an already-accepted submission is acknowledged idempotently rather than double-submitted.

### Using Podman instead of Docker

[Podman](https://podman.io) works as a lighter-weight alternative: it's daemonless and rootless by default, needs no background service, and speaks the same OCI image format and CLI as Docker — this repository's `Dockerfile` and every `docker run` flag the launcher scripts use (`--read-only`, `--cap-drop`, `--tmpfs`, `--mount`, resource limits) are standard OCI/Docker CLI features Podman also implements.

The launcher scripts invoke the `docker` command by name, so either:

- install the `podman-docker` compatibility package, which provides a `docker` command backed by Podman (available on most Linux distributions' package managers), or
- alias it yourself for the session: `alias docker=podman` (Linux/macOS) or `Set-Alias docker podman` (PowerShell).

Either way, `docker build --tag rumble-client:dev .` and every `docker/rumble.sh`/`.ps1` command above then run unchanged against Podman. This hasn't been exercised against every Podman version and platform combination — if you hit a rootless permission or `--tmpfs` incompatibility, please open an issue with the details.

## Building `rumble-client` itself

Most contributors only need the Quickstart above. If you're changing this repository's own Java code, you need to build and test it, which still needs Gradle — but not installed on your machine. Run it inside a Gradle image matching this repository's pinned wrapper version (`gradle/wrapper/gradle-wrapper.properties`, currently 9.6.1), with your checkout bind-mounted:

```shell
docker run --rm -it -v "${PWD}:/workspace" -w /workspace gradle:9.6.1-jdk17 gradle build
```

The same command works unchanged on PowerShell. Note this is a different Gradle version than the `gradle:8.14.3-jdk17` image the `Dockerfile`'s own build stage starts from — that stage still runs `./gradlew` inside it precisely so the wrapper's pinned 9.6.1 is what actually builds the release, regardless of the base image's bundled version. Keep the two in sync if either changes.

This repository currently depends on an unreleased Tank Royale Battle Runner version, built from a local Tank Royale checkout rather than a published Maven artifact — that's why CI and the `Dockerfile`'s own build stage pass `-PtankRoyaleSource=<path>`. To build against a local Tank Royale checkout the same way, mount it alongside your `rumble-client` checkout and add that property:

```shell
docker run --rm -it -v "${PWD}:/workspace" -v "${PWD}/../tank-royale:/tank-royale" -w /workspace \
    gradle:9.6.1-jdk17 gradle -PtankRoyaleSource=/tank-royale build
```

This dependency becomes an ordinary published Maven Central artifact once Tank Royale releases the Battle Runner version this repository pins in `gradle.properties` — at that point this whole section, and the source-mount step, stop being necessary.

If you already have JDK 17 and Gradle installed on your machine, the equivalent host commands work identically: `./gradlew build`, or `./gradlew -PtankRoyaleSource=../tank-royale build`.

## Configuration

Copy `rumble-client.example.json` to `rumble-client.json`. Ranked mode requires a registered `clientId` — see [`rumble-data`'s contributing guide](https://github.com/robocode-dev/rumble-data/blob/main/CONTRIBUTING.md) for the one-time registration pull request; practice mode may omit it. The optional `workDirectory` selects the local cache, journal, and replay-evidence root and defaults to `.rumble-client` beside the configuration file. Do not commit the resulting file or any token.

## Contributing

Read [CONTRIBUTING.md](CONTRIBUTING.md), [SECURITY.md](SECURITY.md), and [GOVERNANCE.md](GOVERNANCE.md) before opening a pull request.
