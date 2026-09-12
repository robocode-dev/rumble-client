# Releasing

Releases are manual: a maintainer decides when to cut one, there is no release bot. Cutting a release publishes a new container image to `ghcr.io/robocode-dev/rumble-client` (see [CAP-001](docs/capabilities/CAP-001-published-container-image/README.md)).

1. On `main`, move `CHANGELOG.md`'s `## [Unreleased]` heading to a dated version heading, e.g. `## [0.2.0] - 2026-09-13`, and start a fresh empty `## [Unreleased]` section above it.
2. Set the plain version in `gradle.properties` (drop the `-SNAPSHOT` suffix), e.g. `version=0.2.0`.
3. Commit both changes.
4. Tag the commit and push the tag: `git tag v0.2.0 && git push origin v0.2.0`. Pushing the tag triggers `.github/workflows/publish.yml`, which builds the `Dockerfile`, pushes `ghcr.io/robocode-dev/rumble-client:0.2.0` and `:latest`, then confirms the image is pullable without credentials. **On the first release**, this confirmation step is expected to fail: GitHub's API has no way to set a package's visibility, so a package `GITHUB_TOKEN` creates starts private. When it fails, go to the package's GHCR settings (Package settings → Danger Zone → Change visibility) and set it to public — a one-time step, since GitHub packages generally cannot be made private again — then re-run the failed workflow run. Every release after that should pass without intervention.
5. Bump `gradle.properties` back to the next `-SNAPSHOT` version, e.g. `version=0.3.0-SNAPSHOT`, and commit.
6. Create a GitHub Release for the tag referencing the `CHANGELOG.md` entry.

Pushing a tag is the only thing that publishes an image; ordinary commits to `main` never do.
