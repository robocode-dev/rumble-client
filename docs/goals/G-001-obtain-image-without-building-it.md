---
id: G-001
type: goal
status: proposed
provenance: inferred
reversal-cost: low
links: [VIS-001]
title: Contributors obtain a ready-to-run container image without building it themselves
---

# G-001 — Contributors obtain a ready-to-run container image without building it themselves

Contributors who want to run ranked battles want the isolated multi-runtime container from the Quickstart, not a build toolchain. Today the README calls the image "the non-published development image" and step 2 of the Quickstart is `docker build --tag rumble-client:dev .` — every contributor pays the four-language build cost (Gradle, a Tank Royale source checkout, Python wheel build, apt package install) before they can run a single battle.

Publishing a versioned image that a contributor can `docker pull` removes that cost from the common path and gives a release a single, referenceable artifact. Local building remains available for development and for contributors changing the client itself.
