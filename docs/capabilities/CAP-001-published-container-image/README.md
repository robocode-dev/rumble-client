---
id: CAP-001
type: capability
status: draft
provenance: inferred
reversal-cost: low
links: [G-001]
goal: G-001
title: Published container image
---

# CAP-001 — Published container image

The system publishes a versioned, pullable container image so a contributor can run ranked battles without building the four-language image themselves. Publishing happens once per human-authored release: a maintainer updates `CHANGELOG.md` and the Gradle version, pushes a `vX.Y.Z` tag, and CI builds the existing `Dockerfile` and pushes it to GHCR under that version and `latest`.

This capability does not change how the image is built (the `Dockerfile` and `scripts/verify-container.sh` verification are unchanged) or how it runs (the launchers' container flags are unchanged). It adds a distribution path, and `docker/rumble.sh` / `rumble.ps1` default to the published `ghcr.io/robocode-dev/rumble-client:latest` image, so a contributor can pull and run without building or tagging anything. Local building remains supported for development, per the README's "Building `rumble-client` itself" section: pass the locally built image name as the launcher's image argument.

See `criteria.md` for acceptance criteria and `design.md` for how the publish workflow fits together.
