# Contributing to the Rumble Client

There are two different ways to contribute to the Rumble:

- To donate battle results, follow [Run ranked Rumble battles](https://robocode.dev/rumble/client-guide). You do not need to change this repository.
- To improve the client itself, open a focused pull request here.

## Develop the client

Keep a compatible Tank Royale checkout beside this repository, then run:

```shell
./gradlew --no-configuration-cache -PtankRoyaleSource=../tank-royale clean build
```

On PowerShell, quote the property argument:

```powershell
.\gradlew.bat --no-configuration-cache "-PtankRoyaleSource=../tank-royale" clean build
```

Add focused tests for behavior changes and make sure the complete build passes before requesting review.

## Protect user data and credentials

Never commit contributor tokens, `rumble-client.json`, replay evidence, cached bot sources, ranked journals, or other files from the configured work directory.

The client may create and read result issues. It must never receive credentials that can write repository contents, branches, releases, packages, Pages, facts, or projections. Keep the battle phase separate from the submission credential, and preserve the container boundary that prevents bot code from receiving external network access or tokens.

## Keep published contracts compatible

Configuration, submission-envelope, receipt, journal, and local-state formats are published contracts. Changes must be versioned and coordinated with the capability document that owns this contract in the Tank Royale repository: [CAP-016-rumble-client](https://github.com/robocode-dev/tank-royale/tree/main/docs/capabilities/CAP-016-rumble-client).

Keep diagnostic messages actionable. A rejected configuration, snapshot, battle, or submission should tell the user what failed and what they can do next.
