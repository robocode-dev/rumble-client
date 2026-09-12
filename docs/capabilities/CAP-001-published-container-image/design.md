---
id: CAP-003
type: design
status: draft
links: [CAP-001]
title: How the published container image works
---

# Design — CAP-001 Published container image

```mermaid
flowchart LR
  Maintainer -->|update CHANGELOG.md and gradle.properties, git tag vX.Y.Z| Tag[Tag pushed]
  Tag --> Publish[publish.yml workflow]
  Publish -->|docker build, --build-arg TANK_ROYALE_COMMIT| Image[Image built]
  Image -->|scripts/verify-container.sh| Verify[Smoke-verified]
  Verify -->|docker push| GHCR[ghcr.io/robocode-dev/rumble-client]
  GHCR -->|set visibility public, then anonymous docker pull| Confirm[Confirmed publicly pullable]
  Confirm --> Contributor
```

`.github/workflows/publish.yml` triggers only on `push: tags: ['v*.*.*']`, separate from `build.yml`'s existing `docker` job (which builds and smoke-verifies the image on every push/PR but never pushes it anywhere). Both jobs build the same root `Dockerfile` with the same `TANK_ROYALE_COMMIT` build argument read from the `TANK_ROYALE_COMMIT` file, and both run `scripts/verify-container.sh` before the image is trusted; `publish.yml` additionally logs into `ghcr.io` with the workflow's `GITHUB_TOKEN` and pushes the verified image as `ghcr.io/robocode-dev/rumble-client:<version>` and `:latest`.

A package newly created on GHCR via `GITHUB_TOKEN` is not guaranteed to be public, which would silently defeat this capability's purpose (nobody could `docker pull` it without credentials). After pushing, the workflow explicitly sets the package's visibility to public via the GitHub API, then proves the fix worked by logging out, removing the local image, and pulling it back down anonymously — a failure at that last step fails the workflow run rather than leaving a package a contributor cannot actually use.

Versioning is manual (see the repository's `RELEASING.md`): a maintainer edits `CHANGELOG.md`'s `## [Unreleased]` section into a dated version heading, sets the plain (non-`-SNAPSHOT`) version in `gradle.properties`, commits, and pushes a matching `vX.Y.Z` tag. The tag is what the workflow reacts to; `gradle.properties` is not read by CI to decide what to publish.
