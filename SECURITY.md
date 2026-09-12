# Security policy

Report vulnerabilities privately to the Tank Royale maintainers. Do not open a public issue containing exploit details, credentials, replay evidence, journal contents, or unpublished bot source.

## Trust boundaries

The client treats the remote bot catalog, source trees, engine pin, matchmaking projections, issue contents, and submission receipts as untrusted input. It validates schemas, repository identities, source hashes, bot identities, and the engine behavior version before ranked execution or journal changes.

## Credentials

The submission token belongs only to the submission phase. Use a fine-grained GitHub token limited to read and write Issues access for `robocode-dev/rumble-data`; never grant repository-content, branch, release, package, or Pages write permission. Supply it through `RUMBLE_CLIENT_TOKEN` at runtime and do not store it in configuration or scripts.

## Container execution

The container battle phase receives neither external network access nor a submission token. Use the read-only root filesystem, dropped capabilities, and finite resource limits provided by the launchers. Native execution is supported, but it runs reviewed bot code with the contributor's host permissions and does not provide container isolation.

## Sensitive local files

The work directory contains cached bot sources, the ranked journal, and replay evidence. Keep it out of Git, restrict access to it, and back it up if you may need evidence for a disputed result. Remove access tokens from the environment after submission.
