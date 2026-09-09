# Tank Royale Rumble Client

The Rumble Client runs local Tank Royale battles against the published Rumble catalog. Ranked mode validates the current engine and catalog pin, journals every completed result with replay evidence, and submits batches through the Rumble data repository's issue inbox. Practice mode never creates a ranked record or submission.

The project is currently being built under [Tank Royale change CH-012](https://github.com/robocode-dev/tank-royale/tree/main/changes/CH-012-create-rumble-client). The public contracts are owned by [CAP-016](https://github.com/robocode-dev/tank-royale/tree/main/docs/capabilities/CAP-016-rumble-client).

Contributors may use the supported native distribution or the recommended container image. Docker and Podman run the same image, which supplies the complete Java, .NET, Python, and Node.js environment and is the isolation boundary for reviewed bot code; direct execution uses the same client contracts but runs bots with the contributor's host permissions. Production images are published only after Tank Royale releases the engine contracts required by ranked Rumble battles.

## Build

Install JDK 17 and keep a Tank Royale checkout containing BR-049 beside this repository, then run:

```shell
./gradlew --no-configuration-cache -PtankRoyaleSource=../tank-royale build
```

On PowerShell, quote the property argument: `.\gradlew.bat --no-configuration-cache "-PtankRoyaleSource=../tank-royale" build`.

The source substitution is the development dependency path until the Runner API is part of a value-bearing Tank Royale release. It compiles the client against `dev.robocode.tankroyale:robocode-tankroyale-runner` without publishing an interim artifact. CI and the container build pin the accepted Tank Royale merge commit rather than following a moving branch. Configuration caching is disabled for source-substituted builds because the included Tank Royale build does not support it.

The build produces native ZIP and TAR archives under `build/distributions/`. Run `./gradlew run --args="--check-runtimes"` to verify the required Java 17, .NET 8 SDK, Python 3.12, and Node.js 22 installations; the check never installs or changes them.

The client validates configuration and can synchronize the current ranked input snapshot. Run `./gradlew run --args="--validate-config"` to check local settings, then run `./gradlew run --args="--sync"` to resolve the canonical data repository, validate its engine pin, catalog, client registration, and matchmaking advice, and prepare an immutable bot cache at the catalog's exact source commit. Every cached source tree is checked against its catalog SHA-256 before it can be used. Ranked battle selection uses a recorded random seed, prioritizes under-sampled pairings involving `myBots`, and falls back to distinct active catalog bots when no advice is available. Each game type declares how many bots one catalog entry expands to, so TwinDuel selects two team entries for its four pinned participants while `1v1` and melee select individual bots, and a selection never contains two entries that share a member bot. Run `./gradlew run --args="--run"` to execute one pinned ranked battle through Battle Runner and retain its replay evidence locally. Run `./gradlew run --args="--submit"` to post pending records through the `rumble-data` issue inbox. It reads `RUMBLE_CLIENT_TOKEN` only at runtime; use a GitHub fine-grained token limited to read and write Issues access for that repository. The client records posted batches locally and removes records only after their result-data receipt comments appear. See the Docker and Podman development image section below for the isolated multi-runtime container.

## Configuration

Copy `rumble-client.example.json` to `rumble-client.json`. Ranked mode requires a registered `clientId`; practice mode may omit it. The optional `workDirectory` selects the local cache, journal, and replay-evidence root and defaults to `.rumble-client` beside the configuration file. Do not commit the resulting file or any token. A submission token is supplied at runtime only when issue-ops support is available.

## Docker and Podman development image

The non-published development image can be built and run with Docker Engine, Docker Desktop, or Podman. The examples below use Docker; replace `docker` with `podman` when invoking the image directly. On Windows, Podman Desktop needs a running Linux virtual machine and can use WSL2 or Hyper-V as the provider; choose the provider when creating the machine. Podman Desktop/WSL2 has been manually verified for this image on Windows; rootless Linux Podman state-directory behavior remains a separate verification target and is not part of CI.

Build the image with one of these commands:

```shell
docker build --tag rumble-client:dev .
podman build --tag rumble-client:dev .
```

Use the launcher scripts for configuration validation, runtime checks, and snapshot synchronization. The shell launcher selects Docker by default and accepts `CONTAINER_ENGINE=podman`; the PowerShell launcher accepts `-Engine podman` or the same `CONTAINER_ENGINE` environment variable:

```shell
./docker/rumble.sh runtimes
CONTAINER_ENGINE=podman ./docker/rumble.sh runtimes
```

```powershell
.\docker\rumble.ps1 runtimes
.\docker\rumble.ps1 runtimes -Engine podman
$env:CONTAINER_ENGINE = 'podman'
.\docker\rumble.ps1 runtimes
```

For `validate` and `sync`, the launcher mounts only the configuration file and `.rumble-client` state directory. Keep the state directory writable; it contains the bot cache, journal, and replay evidence. The runtime check uses no network, while synchronization requires network access to the configured repositories.

The image contains a pinned Tank Royale Python API and its runtime dependencies in an image-owned virtual environment; no host Python environment or package installation is required. When running bot archives directly through a containerized Battle Runner, mount the archive read-only for Java and Python. C# and TypeScript first-run dependency setup may need to write and change file permissions, so copy those archives into writable container storage such as `/tmp` before booting them. This is especially important for Windows bind mounts, where `chmod` can fail with `EPERM`.

If Podman Desktop on Windows reports `ssh-keygen` cannot be found, install or enable Windows OpenSSH and add `C:\Windows\System32\OpenSSH` to the user `PATH`, then restart the terminal and Podman Desktop.

Submission commands remain unavailable until their later CH-012 implementation tasks land. Their container launcher phases will run battles offline without a submission credential and submission online without starting bot code.

## Contributing

Read [CONTRIBUTING.md](CONTRIBUTING.md), [SECURITY.md](SECURITY.md), and [GOVERNANCE.md](GOVERNANCE.md) before opening a pull request.
