# Changelog

All notable changes to this project are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Changed

- The `docker/rumble.sh` and `docker/rumble.ps1` launchers now default to the published `ghcr.io/robocode-dev/rumble-client:latest` image instead of the local-only `rumble-client:dev`, so you can pull and run without building or tagging an image. To use a locally built image, pass its name as the launcher's image argument.
## [0.1.0] - 2026-09-13

### Changed

- Updated the pinned Tank Royale Battle Runner and container source to 1.3.1.

### Added

- Published container image: tagging a release now builds and pushes `ghcr.io/robocode-dev/rumble-client` so contributors can `docker pull` it instead of building it themselves. See `RELEASING.md` for the release steps.
