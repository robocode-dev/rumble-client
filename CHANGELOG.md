# Changelog

All notable changes to this project are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Changed

- `submit` no longer posts result issues through the GitHub API and no longer needs a token. It writes each batch of results to a plain text file under `.rumble-client/submissions` (split by size as well as by the limit of sixty results) and prints the result form address; you paste the file into the form, press Create, and run `submit` again later to collect the receipt. Outside contributors could not create a repository-scoped token and the API dropped the submission label, so their issues were never processed.

## [0.2.0] - 2026-09-15

### Fixed

- Team battles (twinduel) failed to boot with "Team member directory not found": bot cache preparation now publishes the sibling-named alias directory Tank Royale's booter expects for each team member.
- Bumped the pinned Tank Royale commit, which also fixes team member scripts failing to launch from a directory name containing a space.

## [0.1.0] - 2026-09-13

### Changed

- Updated the pinned Tank Royale Battle Runner and container source to 1.3.1.
- The `docker/rumble.sh` and `docker/rumble.ps1` launchers now default to the published `ghcr.io/robocode-dev/rumble-client:latest` image instead of the local-only `rumble-client:dev`, so you can pull and run without building or tagging an image. To use a locally built image, pass its name as the launcher's image argument.

### Added

- Published container image: tagging a release now builds and pushes `ghcr.io/robocode-dev/rumble-client` so contributors can `docker pull` it instead of building it themselves. See `RELEASING.md` for the release steps.
