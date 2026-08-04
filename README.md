# Tank Royale Rumble Client

The Rumble Client runs local Tank Royale battles against the published Rumble catalog. Ranked mode validates the current engine and catalog pin, journals every completed result with replay evidence, and submits batches through the Rumble data repository's issue inbox. Practice mode never creates a ranked record or submission.

The project is currently being built under [Tank Royale change CH-012](https://github.com/robocode-dev/tank-royale/tree/main/changes/CH-012-create-rumble-client). The public contracts are owned by [CAP-016](https://github.com/robocode-dev/tank-royale/tree/main/docs/capabilities/CAP-016-rumble-client).

## Build

Install JDK 17, then run:

```shell
./gradlew build
```

The client validates configuration and can synchronize the current ranked input snapshot. Run `./gradlew run --args="--validate-config"` to check local settings, then run `./gradlew run --args="--sync"` to resolve the canonical data repository and validate its engine pin, catalog, client registration, and matchmaking advice. Ranked battle selection uses a recorded random seed, prioritizes under-sampled pairings involving `myBots`, and falls back to distinct active catalog bots when no advice is available. Battle Runner execution, persistence, issue-ops transport, and the runtime container are added in subsequent CH-012 tasks.

## Configuration

Copy `rumble-client.example.json` to `rumble-client.json`. Ranked mode requires a registered `clientId`; practice mode may omit it. The optional `workDirectory` selects the local cache, journal, and replay-evidence root and defaults to `.rumble-client` beside the configuration file. Do not commit the resulting file or any token. A submission token is supplied at runtime only when issue-ops support is available.

## Contributing

Read [CONTRIBUTING.md](CONTRIBUTING.md), [SECURITY.md](SECURITY.md), and [GOVERNANCE.md](GOVERNANCE.md) before opening a pull request.
