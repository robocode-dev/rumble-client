#!/usr/bin/env python3
"""Unit tests for the runtime metadata updater."""

from __future__ import annotations

import importlib.util
import tempfile
import unittest
from pathlib import Path


MODULE_PATH = Path(__file__).with_name("update_runtime_versions.py")
SPEC = importlib.util.spec_from_file_location("update_runtime_versions", MODULE_PATH)
assert SPEC and SPEC.loader
MODULE = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(MODULE)


class RuntimeUpdaterTest(unittest.TestCase):
    def setUp(self) -> None:
        self.feeds = {
            "java": {"available_lts_releases": [17, 21, 25]},
            "dotnet": {
                "releases-index": [
                    {"channel-version": "9.0", "release-type": "sts", "support-phase": "active"},
                    {"channel-version": "10.0", "release-type": "lts", "support-phase": "active"},
                    {"channel-version": "11.0", "release-type": "lts", "support-phase": "active"},
                    {"channel-version": "8.0", "release-type": "lts", "support-phase": "eol"},
                ]
            },
            "python": [
                {"name": "Python 3.14.7", "is_published": True, "pre_release": False},
                {"name": "Python 3.15.0a1", "is_published": True, "pre_release": True},
                {"name": "Python 3.13.15", "is_published": True, "pre_release": False},
            ],
            "node": [
                {"version": "v24.21.0", "lts": "Krypton"},
                {"version": "v26.8.2", "lts": False},
            ],
        }

    def test_selects_latest_allowed_lanes(self) -> None:
        self.assertEqual(
            {
                "java": "25",
                "dotnet": "11",
                "python": "3.14",
                "node": "24",
                "nodeInstaller": "24.21.0",
            },
            MODULE.latest_versions(self.feeds),
        )

    def test_updates_properties_and_readme(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            properties = root / "runtime-versions.properties"
            properties.write_text("java=17\ndotnet=8\npython=3.12\nnode=22\nnodeInstaller=22.18.0\n", encoding="utf-8")
            readme = root / "README.md"
            readme.write_text(
                "before\n<!-- runtime-versions:start -->\nold\n<!-- runtime-versions:end -->\nafter\n",
                encoding="utf-8",
            )

            versions = MODULE.latest_versions(self.feeds)
            self.assertTrue(MODULE.update_properties(properties, versions))
            self.assertTrue(MODULE.update_readme(readme, versions))
            self.assertFalse(MODULE.update_properties(properties, versions))
            self.assertFalse(MODULE.update_readme(readme, versions))
            self.assertIn("Java 25, .NET 11, Python 3.14, and Node.js 24", readme.read_text(encoding="utf-8"))

    def test_rejects_malformed_feed(self) -> None:
        malformed = dict(self.feeds)
        malformed["node"] = [{"version": "not-a-version", "lts": "Krypton"}]
        with self.assertRaises(MODULE.RuntimeFeedError):
            MODULE.latest_versions(malformed)


if __name__ == "__main__":
    unittest.main()
