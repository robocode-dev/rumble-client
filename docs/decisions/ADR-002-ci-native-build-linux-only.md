---
id: ADR-002
type: decision
status: inferred
author: agent
accepted-by: []
links: [CAP-001]
title: CI builds and tests the native client on Linux only
---

# ADR-002 — CI builds and tests the native client on Linux only

The `build` job in `.github/workflows/build.yml` builds and tests the native client on `ubuntu-latest` only, not on Windows or macOS. The published container image (CAP-001) is the default way to run the client, and Docker Desktop and Podman Desktop on Windows and macOS run that same Linux image, so the Linux build covers the supported default path on every host OS.

Native execution remains supported (see `SECURITY.md`), but native Windows and macOS runs are not verified by CI. Restoring an OS to the matrix is the way to promise CI coverage for it again; do so when native use on that OS matters enough to pay its CI time.

This affects the durable technology choices in the [architecture overview](../architecture/README.md).
