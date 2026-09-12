# Releasing

Releases are manual: a maintainer decides when to cut one, there is no release bot. Cutting a release publishes a new container image to `ghcr.io/robocode-dev/rumble-client` (see [CAP-001](docs/capabilities/CAP-001-published-container-image/README.md)).

1. On `main`, move `CHANGELOG.md`'s `## [Unreleased]` heading to a dated version heading, e.g. `## [0.2.0] - 2026-09-13`, and start a fresh empty `## [Unreleased]` section above it.
2. Set the plain version in `gradle.properties` (drop the `-SNAPSHOT` suffix), e.g. `version=0.2.0`.
3. Commit both changes.
4. Tag the commit and push the tag: `git tag v0.2.0 && git push origin v0.2.0`. Pushing the tag triggers `.github/workflows/publish.yml`, which builds the `Dockerfile`, pushes `ghcr.io/robocode-dev/rumble-client:0.2.0` and `:latest`, sets the GHCR package to public, then confirms the image is pullable without credentials. If the workflow fails on that last confirmation step, the package's visibility couldn't be set automatically — set it to public by hand in the package's GHCR settings, then re-run the workflow.
5. Bump `gradle.properties` back to the next `-SNAPSHOT` version, e.g. `version=0.3.0-SNAPSHOT`, and commit.
6. Create a GitHub Release for the tag referencing the `CHANGELOG.md` entry.

Pushing a tag is the only thing that publishes an image; ordinary commits to `main` never do.
