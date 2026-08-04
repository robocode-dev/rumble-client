# Security policy

Report a vulnerability privately to the Tank Royale maintainers rather than opening a public issue. Do not include credentials, replay evidence, journal contents, or unpublished bot sources in the report.

The client treats all remote catalog, projection, and submission data as untrusted input. Tokens are supplied only to the submission phase and must have no repository-content write permission. The Docker battle phase receives neither external network access nor a submission token. Native execution is supported but runs reviewed bot code with the contributor's host permissions and does not provide Docker isolation.
