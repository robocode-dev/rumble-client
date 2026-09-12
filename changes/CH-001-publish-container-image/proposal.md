---
id: CH-001
type: change
status: open
links: []
title: Publish container image to GHCR on tagged releases
plan: none
---

# CH-001 — Publish container image to GHCR on tagged releases

## What

Add a capability (CAP-001) and the concrete mechanics for it: a `CHANGELOG.md`, a `RELEASING.md` describing the manual release steps, and a new `.github/workflows/publish.yml` that builds the existing `Dockerfile` and pushes it to `ghcr.io/robocode-dev/rumble-client` whenever a `vX.Y.Z` tag is pushed. Update the README's container section to stop calling the image "non-published" and add pull instructions.

## Why

The README currently tells every contributor to `docker build --tag rumble-client:dev .` before they can run a battle — there is no way to obtain a ready-built image. This change publishes one on each maintainer-cut release, without changing how the image is built, verified, or run.

## Plan

This change is plan-less: no `docs/plans/` entry exists yet for release/distribution work, and the user asked for this specific capability directly rather than as part of a larger campaign.
