---
id: TASK-002
type: tasks
status: open
links: [CH-003]
title: Tasks for CH-003
---

# Tasks — CH-003

- [ ] Record ADR-002: CI builds and tests the native client on Linux only, while the container image remains the cross-platform path.
- [ ] Reduce `.github/workflows/build.yml`'s `build` job to `ubuntu-latest`, removing the OS matrix and the Windows-only step.
- [ ] State in `README.md` that native Windows and macOS runs are not CI-tested.
- [ ] Update `docs/architecture/README.md`'s durable choices to link ADR-002.
- [ ] Add a `CHANGELOG.md` `[Unreleased]` entry.
