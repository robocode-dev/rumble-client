# Tank Royale Rumble Client

The Rumble Client runs local Tank Royale battles against the published Rumble catalog. Ranked mode validates the current engine and catalog pin, journals every completed result with replay evidence, and submits batches through the Rumble data repository's issue inbox. Practice mode never creates a ranked record or submission.

The project is currently being built under [Tank Royale change CH-012](https://github.com/robocode-dev/tank-royale/tree/main/changes/CH-012-create-rumble-client). The public contracts are owned by [CAP-016](https://github.com/robocode-dev/tank-royale/tree/main/docs/capabilities/CAP-016-rumble-client).

Contributors may use the supported native distribution or the recommended Docker image. Docker supplies the complete Java, .NET, Python, and Node.js environment and is the isolation boundary for reviewed bot code; direct execution uses the same client contracts but runs bots with the contributor's host permissions. Production images are published only after Tank Royale releases the engine contracts required by ranked Rumble battles.

## Build

Install JDK 17, then run:

```shell
./gradlew build
```

The build produces native ZIP and TAR archives under `build/distributions/`. Run `./gradlew run --args="--check-runtimes"` to verify the required Java 17, .NET 8 SDK, Python 3.12, and Node.js 22 installations; the check never installs or changes them.

The client validates configuration and can synchronize the current ranked input snapshot. Run `./gradlew run --args="--validate-config"` to check local settings, then run `./gradlew run --args="--sync"` to resolve the canonical data repository, validate its engine pin, catalog, client registration, and matchmaking advice, and prepare an immutable bot cache at the catalog's exact source commit. Every cached source tree is checked against its catalog SHA-256 before it can be used. Ranked battle selection uses a recorded random seed, prioritizes under-sampled pairings involving `myBots`, and falls back to distinct active catalog bots when no advice is available. Battle Runner execution, persistence, issue-ops transport, and the runtime container are added in subsequent CH-012 tasks.

## Configuration

Copy `rumble-client.example.json` to `rumble-client.json`. Ranked mode requires a registered `clientId`; practice mode may omit it. The optional `workDirectory` selects the local cache, journal, and replay-evidence root and defaults to `.rumble-client` beside the configuration file. Do not commit the resulting file or any token. A submission token is supplied at runtime only when issue-ops support is available.

## Docker development image

Docker Engine or Docker Desktop is required. Build the current non-published development image with `docker build --tag rumble-client:dev .`, then use `docker/rumble.sh` or `docker/rumble.ps1` to validate configuration, check the bundled runtimes, or synchronize the ranked snapshot. Docker execution uses the default `.rumble-client` work directory beside the configuration file. The launchers expose only that configuration file and state directory to the container and apply a read-only root filesystem, dropped capabilities, finite resource limits, and no external network for the runtime check.

Battle and submission commands remain unavailable until their later CH-012 implementation tasks land. Their Docker launcher phases will run battles offline without a submission credential and submission online without starting bot code.

## Contributing

Read [CONTRIBUTING.md](CONTRIBUTING.md), [SECURITY.md](SECURITY.md), and [GOVERNANCE.md](GOVERNANCE.md) before opening a pull request.
