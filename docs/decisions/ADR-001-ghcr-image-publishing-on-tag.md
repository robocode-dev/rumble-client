---
id: ADR-001
type: decision
status: inferred
author: agent
accepted-by: []
links: [CAP-001]
title: Publish the container image to GHCR, triggered only by version tags
---

# ADR-001 — Publish the container image to GHCR, triggered only by version tags

The container image (CAP-001) is published to GitHub Container Registry (`ghcr.io/robocode-dev/rumble-client`) rather than Docker Hub or another registry, because it authenticates with the workflow's own `GITHUB_TOKEN` — no separate account, org, or long-lived secret to provision or rotate — and keeps the published artifact next to its source repository.

Publishing is triggered only by pushing a `vX.Y.Z` tag, never by ordinary pushes to `main`. This keeps every image in the registry mapped to a real, changelogged version and keeps unstable/untagged commits out of the public registry; it also keeps `build.yml`'s existing per-push/PR `docker` job (build and smoke-verify only) unchanged, since publishing is a separate workflow that only runs on the tag event.

Versioning and the changelog stay manual (no release-bot/Conventional-Commits automation): a human decides when a version is cut, edits `CHANGELOG.md` and `gradle.properties`, and pushes the tag.
