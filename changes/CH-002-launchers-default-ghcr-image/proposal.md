---
id: CH-002
type: change
status: open
links: [CAP-001, G-001]
title: Launchers default to the published GHCR image
---

# CH-002 — Launchers default to the published GHCR image

This change is plan-less: it serves G-001 directly and no plan exists in this repository.

## What

`docker/rumble.sh` and `docker/rumble.ps1` default their image argument to `ghcr.io/robocode-dev/rumble-client:latest` instead of the local-only name `rumble-client:dev`. Passing an explicit image argument keeps working, so a locally built image is still usable by naming it.

The README Quickstart and container section drop the `docker tag` workaround: pulling the published image is the default path, and building the image or the Java code yourself moves under "Building `rumble-client` itself". CAP-001 is revised, because it currently states the launchers are unchanged, and gains a criterion for the new default.

## Why

G-001 wants contributors to run battles without building the image. CH-001 published the image, but the launchers still looked for `rumble-client:dev`, so the published image only worked after an extra `docker tag` step or a third argument on every command. Defaulting to the published image makes the common path pull-and-run.
