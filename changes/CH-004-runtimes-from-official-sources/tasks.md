---
id: CH-004-tasks
type: tasks
status: open
links: [CH-004]
title: Install every bot runtime from its official source and let Python wait for its first patch release
---

- [ ] Settle the open questions with the maintainer.
- [ ] Write the capability for the image's bot runtimes, with its criteria and design.
- [ ] Install Java, .NET and Python in the Dockerfile from their official sources, checked against their published checksums, and build the Python Bot API wheel with the Python the list names.
- [ ] Let the refresh move Python to a new minor version only from its x.y.1 release, with unit tests.
- [ ] Update the README, the architecture overview, the published-image capability and the changelog.
- [ ] Build the image locally and run the container verification and the unit tests; validate the corpus.
- [ ] Hand the maintainer the runs to judge the criteria by.
