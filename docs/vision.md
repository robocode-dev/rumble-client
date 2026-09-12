---
id: VIS-001
type: vision
status: draft
provenance: inferred
reversal-cost: low
links: []
title: Enable community-run ranked Tank Royale battles
---

# VIS-001 — Enable community-run ranked Tank Royale battles

The Rumble Client lets people contribute ranked Tank Royale battles from their own computers. It serves the Tank Royale community by turning the reviewed bot catalog and matchmaking advice into reproducible local battles whose replay evidence and result records can be submitted to the shared Rumble data repository.

The client covers catalog synchronization, bot caching, ranked battle selection and execution, local journal and replay evidence, and idempotent result submission. It deliberately does not define the Tank Royale game engine, bot implementations, catalog review process, or the server-side data repository; it consumes those contracts.

The direction is constrained by reproducibility, immutable source verification, safe execution of reviewed-but-untrusted bot code, explicit runtime checks, and recoverable submission. Success means contributors can run the documented container workflow, produce valid ranked results, and retry submission without duplicating accepted records.
