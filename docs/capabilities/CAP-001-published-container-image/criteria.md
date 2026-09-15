---
id: CAP-002
type: criteria
status: active
links: [CAP-001]
title: Acceptance criteria for the published container image
---

# Acceptance criteria — CAP-001 Published container image

```gherkin
@AC-001
Scenario: Tagging a release publishes a pullable versioned image
  Test-type: Human
  Given a maintainer has updated CHANGELOG.md and gradle.properties for version X.Y.Z on main
  When they create and push the git tag "vX.Y.Z"
  Then the publish workflow builds the repository's Dockerfile and pushes the image to ghcr.io/robocode-dev/rumble-client tagged "X.Y.Z" and "latest"
  And a contributor can run "docker pull ghcr.io/robocode-dev/rumble-client:X.Y.Z" and use it in place of a locally built image

@AC-002
Scenario: Ordinary commits to main never publish an image
  Test-type: Human
  Given no version tag has been pushed
  When a commit lands on main
  Then no image is pushed to ghcr.io/robocode-dev/rumble-client

@AC-003
Scenario: Every published version has a changelog entry
  Test-type: Human
  Given a version tag "vX.Y.Z" has been pushed
  When a contributor reads CHANGELOG.md
  Then it contains a "## [X.Y.Z]" section describing what changed since the previous version

@AC-004
Scenario: The launchers run the published image unless told otherwise
  Test-type: Human
  Given a contributor has not built or tagged any image locally
  When they run "docker/rumble.sh runtimes" or "docker/rumble.ps1 runtimes" without an image argument
  Then the launcher runs ghcr.io/robocode-dev/rumble-client:latest, pulling it if it is not present
  And passing an explicit image argument, such as a locally built "rumble-client:dev", runs that image instead
```
