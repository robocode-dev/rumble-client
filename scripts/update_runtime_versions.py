#!/usr/bin/env python3
"""Refresh the Rumble client runtime policy from official release metadata."""

from __future__ import annotations

import argparse
import json
import re
import urllib.request
from pathlib import Path
from typing import Any, Callable, Mapping

SOURCE_URLS = {
    "java": "https://api.adoptium.net/v3/info/available_releases",
    "dotnet": "https://dotnetcli.blob.core.windows.net/dotnet/release-metadata/releases-index.json",
    "python": "https://www.python.org/api/v2/downloads/release/?limit=5000",
    "node": "https://nodejs.org/dist/index.json",
}
REQUIRED_PROPERTIES = ("java", "dotnet", "python", "node", "nodeInstaller")
README_START = "<!-- runtime-versions:start -->"
README_END = "<!-- runtime-versions:end -->"
PROPERTY_PATTERN = re.compile(r"^(java|dotnet|python|node|nodeInstaller)=(.*)$", re.MULTILINE)
PYTHON_RELEASE_PATTERN = re.compile(r"^Python (3)\.(\d+)\.(\d+)$")
NODE_VERSION_PATTERN = re.compile(r"^v(\d+)\.(\d+)\.(\d+)$")


class RuntimeFeedError(RuntimeError):
    """Raised when an official release feed is unavailable or malformed."""


def fetch_json(url: str) -> Any:
    """Fetch and decode one official JSON release feed."""
    request = urllib.request.Request(url, headers={"User-Agent": "tank-royale-runtime-refresh"})
    try:
        with urllib.request.urlopen(request, timeout=30) as response:
            return json.load(response)
    except (OSError, json.JSONDecodeError) as error:
        raise RuntimeFeedError(f"Could not read runtime metadata from {url}: {error}") from error


def latest_versions(feeds: Mapping[str, Any]) -> dict[str, str]:
    """Select the newest versions allowed by the LTS-first runtime policy."""
    try:
        java_lts = max(int(version) for version in feeds["java"]["available_lts_releases"])
        dotnet_lts = max(
            (
                entry["channel-version"]
                for entry in feeds["dotnet"]["releases-index"]
                if entry.get("release-type") == "lts"
                and entry.get("support-phase") not in {"eol", "end-of-life"}
            ),
            key=lambda version: tuple(int(part) for part in version.split(".")),
        )
        dotnet_major = dotnet_lts.split(".", maxsplit=1)[0]

        python_releases = []
        for release in feeds["python"]:
            match = PYTHON_RELEASE_PATTERN.fullmatch(release["name"])
            if match and release.get("is_published") and not release.get("pre_release"):
                python_releases.append(tuple(int(part) for part in match.groups()))
        python_version = max(python_releases)

        node_releases = []
        for release in feeds["node"]:
            match = NODE_VERSION_PATTERN.fullmatch(release["version"])
            if match and release.get("lts"):
                node_releases.append(tuple(int(part) for part in match.groups()))
        node_version = max(node_releases)
    except (KeyError, TypeError, ValueError) as error:
        raise RuntimeFeedError(f"Official runtime metadata has an unexpected shape: {error}") from error

    return {
        "java": str(java_lts),
        "dotnet": dotnet_major,
        "python": f"{python_version[0]}.{python_version[1]}",
        "node": str(node_version[0]),
        "nodeInstaller": ".".join(str(part) for part in node_version),
    }


def read_properties(path: Path) -> dict[str, str]:
    """Read the runtime properties required by the image and preflight checker."""
    properties: dict[str, str] = {}
    for line in path.read_text(encoding="utf-8").splitlines():
        if not line or line.startswith("#") or "=" not in line:
            continue
        key, value = line.split("=", maxsplit=1)
        properties[key] = value
    missing = [key for key in REQUIRED_PROPERTIES if key not in properties]
    if missing:
        raise RuntimeFeedError(f"Runtime policy is missing properties: {', '.join(missing)}")
    return properties


def update_properties(path: Path, versions: Mapping[str, str]) -> bool:
    """Update the runtime properties file and report whether it changed."""
    content = path.read_text(encoding="utf-8")
    current = read_properties(path)
    if any(key not in versions for key in REQUIRED_PROPERTIES):
        raise RuntimeFeedError("Selected runtime metadata did not produce every required property")
    updated = PROPERTY_PATTERN.sub(lambda match: f"{match.group(1)}={versions[match.group(1)]}", content)
    if updated == content:
        return False
    path.write_text(updated, encoding="utf-8")
    changed = [
        f"{key}: {current[key]} -> {versions[key]}"
        for key in REQUIRED_PROPERTIES
        if current[key] != versions[key]
    ]
    print("Runtime policy changes:")
    print("\n".join(f"- {change}" for change in changed))
    return True


def update_readme(path: Path, versions: Mapping[str, str]) -> bool:
    """Update the generated runtime summary in the Rumble README."""
    content = path.read_text(encoding="utf-8")
    block = (
        f"{README_START}\n"
        f"The container and native preflight currently target Java {versions['java']}, .NET {versions['dotnet']}, "
        f"Python {versions['python']}, and Node.js {versions['node']} (Node.js installer {versions['nodeInstaller']}). "
        "This block is refreshed by the scheduled runtime update workflow.\n"
        f"{README_END}"
    )
    pattern = re.compile(re.escape(README_START) + r".*?" + re.escape(README_END), re.DOTALL)
    updated, count = pattern.subn(block, content, count=1)
    if count != 1:
        raise RuntimeFeedError(f"README must contain exactly one generated runtime block: {path}")
    if updated == content:
        return False
    path.write_text(updated, encoding="utf-8")
    return True


def load_feeds(fetcher: Callable[[str], Any] = fetch_json) -> dict[str, Any]:
    """Fetch all release feeds used by the runtime policy."""
    return {runtime: fetcher(url) for runtime, url in SOURCE_URLS.items()}


def main() -> int:
    """Run the runtime refresh."""
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--versions-file", type=Path, default=Path("src/main/resources/runtime-versions.properties"))
    parser.add_argument("--readme", type=Path, default=Path("README.md"))
    args = parser.parse_args()

    feeds = load_feeds()
    versions = latest_versions(feeds)
    print("Official runtime metadata:")
    print("\n".join(f"- {runtime}: {SOURCE_URLS[runtime]}" for runtime in SOURCE_URLS))
    changed = update_properties(args.versions_file, versions)
    changed = update_readme(args.readme, versions) or changed
    if not changed:
        print("Runtime policy is already current.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
