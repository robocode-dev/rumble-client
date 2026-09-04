# Tank Royale Rumble Client

The Rumble Client lets your computer contribute ranked battles to the Tank Royale Rumble. It downloads the reviewed bot catalog, chooses an under-sampled matchup, runs the battle locally, keeps replay evidence, and submits the result through the Rumble data repository.

For the complete newcomer-friendly walkthrough, including registration and token setup, read [Run ranked Rumble battles](https://robocode.dev/rumble/client-guide). This README is the technical reference for the source checkout.

## Current status

Native source execution supports configuration validation, runtime checks, synchronization, one ranked battle per `--run`, and result submission. Practice configurations can be validated, but the current `--run` path is ranked-only.

There is no published production container image yet. The Dockerfile builds a development image containing Java, .NET, Python, and Node.js, while the checked-in Docker launchers currently expose only `validate`, `runtimes`, and `sync`.

## Build from source

Install JDK 17 and keep a Tank Royale checkout beside this repository:

```text
work/
├── tank-royale/
└── rumble-client/
```

On Linux or macOS:

```shell
./gradlew --no-configuration-cache -PtankRoyaleSource=../tank-royale build
```

On PowerShell:

```powershell
.\gradlew.bat --no-configuration-cache "-PtankRoyaleSource=../tank-royale" build
```

The build produces ZIP and TAR distributions under `build/distributions/`. The source substitution supplies the local Battle Runner and the sample-bot build used by the test suite; CI uses a pinned Tank Royale commit for the same purpose.

## Configure the client

Copy `rumble-client.example.json` to `rumble-client.json`. Ranked mode requires a `clientId` registered to your GitHub account in `rumble-data`. The optional `workDirectory` selects the cache, journal, and replay-evidence root and defaults to `.rumble-client` beside the configuration file.

Use one game type per configuration with the current command-line client. `--run` executes one battle using the first configured game type in contract-name order. `myBots` may list the names of active bots or teams owned by you, without version numbers; under-sampled matchups involving those entries receive priority. `battlesPerSession` is validated for the session contract, but the current one-battle command does not consume it.

Do not commit `rumble-client.json`, `.rumble-client`, or any access token.

## Commands

| Command | Purpose |
|---------|---------|
| `--help` | Show command-line usage. |
| `--check-runtimes` | Check Java 17, .NET 8 SDK, Python 3.12, and Node.js 22. |
| `--validate-config [path]` | Validate a ranked or practice configuration without synchronizing or running a battle. |
| `--sync [path]` | Validate the current ranked input snapshot and prepare the pinned bot cache. |
| `--run [path]` | Synchronize and run one ranked battle, then append it to the local journal. |
| `--submit [path]` | Synchronize, submit pending journal records, and process published receipts. |

The default path is `rumble-client.json`. During development, invoke commands through Gradle, for example:

```shell
./gradlew run --args="--check-runtimes"
./gradlew run --args="--validate-config"
./gradlew run --args="--sync"
./gradlew run --args="--run"
```

Completed ranked battles retain replay evidence under the configured work directory. Aborted, incomplete, identity-mismatched, or behavior-incompatible battles do not create submittable records.

## Submit results

`--submit` reads `RUMBLE_CLIENT_TOKEN` at runtime. Use a fine-grained GitHub token limited to read and write Issues access for `robocode-dev/rumble-data`. The client neither requests nor needs permission to write repository contents, branches, releases, packages, or Pages.

The client keeps a submitted record in its journal until the corresponding accepted-result receipt is visible. A network failure or interrupted submission is therefore safe to retry. Records from an obsolete behavior-version epoch are quarantined instead of being submitted under different game behavior.

## Docker development image

Build the current image locally:

```shell
docker build --tag rumble-client:dev .
```

Use `docker/rumble.sh` or `docker/rumble.ps1` for `validate`, `runtimes`, and `sync`. The launchers mount only the configuration and state directory, run with a read-only root filesystem, drop capabilities, and apply finite resource limits. The runtime check also disables networking.

Use the native build for ranked `run` and `submit` until those Docker launcher phases are available.

## Contributing to this repository

Read [CONTRIBUTING.md](CONTRIBUTING.md), [SECURITY.md](SECURITY.md), and [GOVERNANCE.md](GOVERNANCE.md) before opening a pull request.
