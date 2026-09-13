---
id: CH-003
type: change
status: open
links: [G-001]
title: CI builds the native client on Linux only
---

# CH-003 — CI builds the native client on Linux only

This change is plan-less: no plan exists in this repository, and it follows CH-002 (launchers default to the published GHCR image) as scoped by the maintainer.

## What

`.github/workflows/build.yml`'s `build` job stops running on a Windows and macOS matrix and runs only on `ubuntu-latest`. The `docker` job is unchanged. The README states that CI builds and tests the native client on Linux only, so running natively on Windows or macOS is possible but not CI-tested. ADR-002 records the choice.

Native execution stays supported as `SECURITY.md` describes; this change narrows what CI verifies, not what the client does.

## Why

The three-OS matrix arrived with the native ZIP/TAR distributions (commit c1c7d32). Since CH-002, the published container image is the default way to run the client, and Docker Desktop on Windows and macOS runs that same Linux image. Building only on Linux is the usual setup for a container-shipped client, and the Windows and macOS jobs cost CI time for a path few contributors use.
