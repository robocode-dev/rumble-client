---
id: G-001
type: goal
status: accepted
links: [VIS-001]
title: Contributors obtain a ready-to-run container image without building it themselves
---

# G-001 — Contributors obtain a ready-to-run container image without building it themselves

Contributors who want to run ranked battles want the isolated multi-runtime container from the Quickstart, not a build toolchain. Before this goal was met, the README called the image "the non-published development image" and step 2 of the Quickstart was `docker build --tag rumble-client:dev .` — every contributor paid the four-language build cost (Gradle, a Tank Royale source checkout, Python wheel build, apt package install) before they could run a single battle.

Publishing a versioned image that a contributor can `docker pull` removes that cost from the common path and gives a release a single, referenceable artifact. Local building remains available for development and for contributors changing the client itself. Delivered by CAP-001 (`ghcr.io/robocode-dev/rumble-client`, first published in the 0.1.0 release).
