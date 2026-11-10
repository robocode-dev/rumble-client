---
id: CH-004
type: change
status: open
links: [G-001, CAP-001, CAP-004]
title: The image installs every bot runtime from its official source, and Python moves to a new version only from its first patch release
---

# The image installs every bot runtime from its official source, and Python moves to a new version only from its first patch release

The container image runs bots in four languages. Node.js is already downloaded from nodejs.org and checked against the checksum nodejs.org publishes. Java, .NET and Python come from the Ubuntu 26.04 base image's package manager, apt, as `openjdk-25-jdk-headless`, `dotnet-sdk-10.0` and `python3.14`. The versions themselves come from the one runtime list, `src/main/resources/runtime-versions.properties`, which the monthly refresh workflow updates from each runtime's official release feed.

The refresh of 1 November 2026 failed. The Python feed had published 3.15.0, so the refresh moved the list to Python 3.15, and the image build then failed because Ubuntu 26.04 has no `python3.15` package. Ubuntu adds a new runtime version long after its release, if at all during a release's life, so the same will happen to Java and .NET at their next long-term release. The next scheduled refresh runs on 1 December 2026 and will fail the same way.

This change is plan-less: no plan in this repository promises it. It carries out the runtime decisions the maintainer made on 9 November 2026 while working on Tank Royale's Download bots screen, which reads its versions from this same list.

## Vision

The change proceeds under VIS-001, the inferred draft vision that the Rumble Client lets people contribute reproducible ranked battles from their own computers, with explicit runtime checks. The vision is unchanged.

## Why

A runtime the image cannot install stops every contributor's image refresh, and the list of versions loses its meaning: it says one version while the image could only ever hold what Ubuntu ships. The maintainer decided that the Rumble follows the newest long-term release of each runtime, that the versions live in this one list, and that the image fetches each runtime from its official source, as it already does for Node.js.

A Python minor version is also not ready for the Rumble on its first day. Its first release, x.y.0, is followed within weeks by fixes, and builds and packages for it appear over the following weeks. The maintainer decided that the Rumble moves to a new Python version only once its first patch release, x.y.1, is out.

## What changes

- **Java** is the Eclipse Temurin JDK for the Java version in the list, downloaded from Adoptium, the project that builds Temurin, and checked against the SHA-256 checksum Adoptium publishes for that file.
- **.NET** is the .NET SDK for the .NET version in the list, downloaded from Microsoft's .NET release site, the one the release feed already names, and checked against the SHA-512 checksum in Microsoft's release metadata for that channel.
- **Python** is CPython for the Python version in the list, as the relocatable standalone build that Astral publishes for Linux in python-build-standalone, built from python.org's source, and checked against the SHA-256 checksums published with each of its releases. python.org itself publishes no Linux build that can be unpacked and run. The image's Python environment for bots is made from that Python, as today.
- **Node.js** stays as it is.
- **Nothing from apt** is a bot runtime. apt still installs the few tools the image needs, such as certificates, `curl`, `git` and `xz-utils`. A download whose checksum does not match stops the build.
- **The newest patch** of each version line in the list is installed when the image is built, as apt does today. The list keeps naming lines, such as Java 25, .NET 10 and Python 3.14, and the generated block in the README stays as it is.
- **Python waits for its first patch release.** The refresh moves Python to a new minor version only when that version's x.y.1 release is published. Until then it keeps the newest minor version that has one: between 9 and 21 October 2026, with 3.15.0 out and 3.15.1 not yet, the list would have stayed at 3.14. Java, .NET and Node.js keep their rule: the newest long-term release. Python 3.15.1 came out on 21 October 2026, so the refresh of 1 December will propose Python 3.15 in a pull request, and with this change the image can install it; this change itself keeps the list at 3.14.
- **One place for the Python version** also in the image build. The stage that builds the Python Bot API wheel names `python:3.14-slim` itself today. It will build the wheel with the Python that the list names instead, so no file but the list names a runtime version.
- **A new capability** describes the image's bot runtimes: where each comes from, how it is checked, and how the refresh chooses versions. The published-image capability keeps only how the image is published.
- **Documentation:** the README's section on the image and the architecture overview say where the runtimes come from. The changelog gets an entry for the next release.

## Challenge the commitment

The riskiest assumption is that a standalone Python build behaves in the image as the apt package does, with `venv`, `pip` and the Bot API's dependencies. Astral's builds are the ones `uv` installs for millions of users, and they are made to be unpacked anywhere, but they are not made by python.org. The credible alternative is to build CPython from python.org's source in a stage of its own. It needs no builder but python.org, but it adds a compiler toolchain and several minutes to every image build, and the monthly refresh builds the image too. The maintainer named the standalone build in the decision of 9 November; it is also the first open question.

A second risk is the checksums: each source publishes them differently, so a mistake in reading them could let a wrong file through. Each check fails closed: a checksum that cannot be found stops the build like one that does not match.

The cost of doing nothing is concrete: the refresh fails again on 1 December, and the next Java or .NET long-term release fails the image build in the same way.

## Acceptance criteria

The new capability carries these criteria. All three are verified by the maintainer from runs of the image build and the refresh, because the evidence is a container build and a workflow run, which this repository's tests do not run.

- **The image installs every bot runtime from its official source.** The image built from the Dockerfile holds Java, .NET, Python and Node.js at the versions the runtime list names, each downloaded from its official source and checked against the checksum that source publishes, and none installed by the base image's package manager. A download whose checksum does not match, or cannot be found, stops the build. (Human)
- **The image runs a battle on every platform.** The container verification builds the image and runs its checks and one-round battles with Java, C#, Python and TypeScript bots, and `rumble-client runtimes` in the image reports the versions the list names. (Human)
- **The refresh moves Python only from its first patch release.** When the newest Python minor version has only its x.y.0 release, the refresh keeps the newest minor version that has an x.y.1 release; when x.y.1 is out, the refresh moves the list to x.y. The rule for Java, .NET and Node.js is unchanged. (Human)

The refresh's choice of versions is also covered by the updater's Python unit tests, which are supporting tests rather than acceptance evidence.

## Verification

- The updater's unit tests, extended for the Python rule.
- A local image build and `scripts/verify-container.sh` with Docker Desktop, which is installed on the maintainer's computer but not running; the change needs it running, or a build in CI.
- A manual run of the refresh workflow from the change's branch is not possible without pushing the branch, so the maintainer is asked whether to push it for that run, or to accept on the local build and the unit tests and watch the refresh of 1 December.

## Out of scope

- The build stages' own Java versions, the JDK 11 and 17 that compile the client itself. They are build tools, not bot runtimes.
- Changing the base image away from Ubuntu 26.04.
- Pinning exact patch versions in the list.
- The other copies of the versions outside this repository, in rumble-bots and in Tank Royale, which read or will read this list.

## Identity allocation

The change, its three criteria and the new capability were allocated locally in this clone. The remote had no other `ch-*` branch and no `clue/id-allocator` branch when it was read. The reservations are visible only in this clone until the change is pushed, so `clue id coordinate` comes before a second contributor starts.

## Human evidence

For the three criteria of the bot runtimes in the image: the image installs every bot runtime from its official source, it runs a battle on every platform, and the refresh moves Python only from its first patch release.

- **Observer:** the maintainer, judging from the author's report of runs made on the maintainer's computer on 10 November 2026; the maintainer did not run the image or the checks themselves.
- **Conditions:** Docker Desktop 29.7.2 on Windows 11, the image built for amd64 from this change's Dockerfile with the pinned Tank Royale commit, and the container verification run from Git Bash with the Tank Royale checkout beside this repository.
- **Reported:** the build downloaded the Eclipse Temurin JDK 25.0.5 from Adoptium, the .NET SDK 10.0.404 from Microsoft, CPython 3.14.10 as Astral's standalone build and Node.js 24.21.0 from nodejs.org, and each passed its checksum check; the built image held no Java, .NET, Python or Node.js package from apt. A wrong checksum and a missing checksum each made the checksum check fail. The container verification reported Java 25, .NET 10, Python 3.14 and Node.js 24 as required, confirmed that the image does not run as root, and ran four one-round battles: Python against Java, Java against Python, C# against Java and TypeScript against Java, all of which finished. The updater's unit tests passed, and two of them failed when the Python rule was removed. A dry run of the refresh against the live feeds, written to copies only, chose Python 3.15 and Node.js 26, because Python 3.15.1 came out on 21 October 2026 and Node.js 26 is now a long-term release.
- **Judgment:** the maintainer approved the criteria on that report.
- **Not observed:** an arm64 build, a run of the refresh workflow on GitHub, the publish workflow, and the image on Podman.
