---
id: CH-004-open-questions
type: open-questions
status: resolved
links: [CH-004]
title: Where Python comes from, how Java and .NET are fetched, and how the change is verified before acceptance
---

## Does Python come from Astral's standalone build, or is it built from python.org's source?

Decided by the maintainer: Astral's standalone build. The recommendation was: Astral's standalone build (python-build-standalone), checked against the checksums of its release. It unpacks in seconds, is made to run from any folder, and is what `uv` installs. The maintainer named it on 9 November. The alternative is to compile CPython from python.org's source in a build stage: no builder but python.org, but a compiler toolchain and several more minutes on every image build and every monthly refresh.

## Are Java and .NET downloaded in the Dockerfile, or copied from their official container images?

Decided by the maintainer: downloaded in the Dockerfile. The recommendation was: downloaded in the Dockerfile, as Node.js is, from Adoptium's and Microsoft's release sites with their checksums. The Dockerfile then reads every version from the runtime list itself. The alternative is to copy them from the official `eclipse-temurin` and `mcr.microsoft.com/dotnet/sdk` images. That needs no checksum handling, but the image names must carry the version, so every build, the workflows and the verification script must pass the versions in from the list, and a copied .NET SDK may miss libraries the Ubuntu base image lacks.

## How is the change verified before acceptance?

Decided by the maintainer: a local image build. The author may start Docker Desktop. The recommendation was: a local image build and the container verification with Docker Desktop started, plus the updater's unit tests; then the maintainer judges the criteria from those runs. A run of the refresh workflow needs the branch on GitHub, which needs the maintainer's word to push it. Without it, the Python rule is shown by the unit tests and the next scheduled refresh.
