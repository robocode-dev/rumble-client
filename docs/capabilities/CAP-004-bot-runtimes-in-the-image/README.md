---
id: CAP-004
type: capability
status: active
links: [G-001]
goal: G-001
title: Bot runtimes in the image
---

# CAP-004 — Bot runtimes in the image

The container image holds the four runtimes that ranked bots need: Java, .NET, Python and Node.js. Their versions come from one list, `src/main/resources/runtime-versions.properties`, which the Rumble's other repositories and the native preflight also read. Each runtime is downloaded from its official source when the image is built, and each download is checked against the checksum that source publishes, so the image can hold any version the list names on the day it is published, not only what the base image's package manager ships.

The list follows the newest long-term release of Java, .NET and Node.js. Python has no long-term releases; the list follows the newest Python minor version once its first patch release, x.y.1, is out. A monthly workflow refreshes the list from each runtime's official release feed, builds the image, runs a battle on every platform and opens a pull request for a person to review. Players upgrade their own runtimes when the list moves on.

This capability is about what the image contains. How the image is published is [CAP-001](../CAP-001-published-container-image/README.md).

See `criteria.md` for acceptance criteria and `design.md` for how the runtimes are installed and refreshed.
