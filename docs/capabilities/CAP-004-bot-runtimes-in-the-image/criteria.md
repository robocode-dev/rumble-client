---
id: CAP-005
type: criteria
status: active
links: [CAP-004]
title: Acceptance criteria for the bot runtimes in the image
---

# Acceptance criteria — CAP-004 Bot runtimes in the image

```gherkin
@AC-005
Scenario: The image installs every bot runtime from its official source
  Test-type: Human
  Given the runtime list names a version of Java, .NET, Python and Node.js
  When the image is built from the repository's Dockerfile
  Then it holds the Eclipse Temurin JDK from Adoptium, the .NET SDK from Microsoft's .NET release site, CPython as Astral's python-build-standalone build, and Node.js from nodejs.org, each at the newest patch release of the version the list names
  And each download was checked against the checksum its source publishes, and none of the four runtimes was installed by the base image's package manager
  And a download whose checksum does not match, or whose checksum cannot be found, stops the build

@AC-006
Scenario: The image runs a battle on every platform
  Test-type: Human
  Given an image built from the repository's Dockerfile
  When the container verification runs its checks and one-round battles with Java, C#, Python and TypeScript sample bots
  Then every check and every battle succeeds, the image runs as a user other than root, and "rumble-client --check-runtimes" in the image reports the versions the list names

@AC-007
Scenario: The refresh moves Python to a new version only from its first patch release
  Test-type: Human
  Given the official Python release feed
  When the monthly refresh chooses the Python version for the runtime list
  Then it chooses the newest Python minor version whose x.y.1 release is published, so a minor version with only its x.y.0 release is not chosen
  And Java, .NET and Node.js are still chosen as the newest long-term release
```
