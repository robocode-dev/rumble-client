---
id: CH-001-tasks
type: tasks
status: open
links: [CH-001]
title: Tasks for CH-001
---

# Tasks — CH-001

- [x] Add goal G-001 and capability CAP-001 (README, criteria.md with @AC-001/@AC-002/@AC-003, design.md)
- [x] Add ADR-001 recording the GHCR + tag-trigger + manual-versioning decision
- [ ] Add `CHANGELOG.md` (Keep a Changelog format, `## [Unreleased]` section) — serves AC-003
- [ ] Add `RELEASING.md` describing the manual release steps (changelog, version bump, tag)
- [ ] Add `.github/workflows/publish.yml`, triggered on `push: tags: ['v*.*.*']` — serves AC-001, AC-002
- [ ] Update `README.md` container section: drop "non-published" language, add `docker pull` instructions
- [ ] Update `docs/architecture/README.md` to name GHCR as the image distribution point
