#!/usr/bin/env python3
"""Fetch / verify the pinned 2026 Line Planning Benchmark subset.

The benchmark is pinned to one commit.  The subset needed by this project
(Randstad-IC instance files, Java sources of the official generators, license)
is copied into ``data/external/line-planning-benchmark`` and checked against
``data/external/benchmark_manifest.json`` (SHA-256 of every file).

Usage::

    python scripts/fetch_benchmark.py            # fetch if missing, then verify
    python scripts/fetch_benchmark.py --verify   # verify only (no network)
"""

from __future__ import annotations

import argparse
import hashlib
import json
import shutil
import subprocess
import sys
import tempfile
from pathlib import Path

REPO_URL = "https://github.com/rn-van-lieshout/line-planning-benchmark"
COMMIT = "5d0aae68588701285c42ba47e593fbeb600cbc5c"
INCLUDE = ["LICENSE", "README.md", "settings.properties", "instances/README.md",
           "instances/Randstad-IC", "src"]

ROOT = Path(__file__).resolve().parents[1]
DEST = ROOT / "data" / "external" / "line-planning-benchmark"
MANIFEST = ROOT / "data" / "external" / "benchmark_manifest.json"


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1 << 20), b""):
            digest.update(chunk)
    return digest.hexdigest()


def collect(base: Path) -> dict:
    return {
        str(path.relative_to(base)): sha256(path)
        for path in sorted(base.rglob("*"))
        if path.is_file()
    }


def write_manifest() -> None:
    manifest = {"repository": REPO_URL, "commit": COMMIT, "license": "MIT",
                "files": collect(DEST)}
    MANIFEST.write_text(json.dumps(manifest, indent=1, sort_keys=True) + "\n")


def verify() -> list:
    manifest = json.loads(MANIFEST.read_text())
    if manifest["commit"] != COMMIT:
        return [f"manifest commit {manifest['commit']} != pinned {COMMIT}"]
    problems = []
    for name, expected in manifest["files"].items():
        path = DEST / name
        if not path.is_file():
            problems.append(f"missing: {name}")
        elif sha256(path) != expected:
            problems.append(f"hash mismatch: {name}")
    return problems


def fetch() -> None:
    with tempfile.TemporaryDirectory() as tmp:
        work = Path(tmp)
        def git(*args):
            subprocess.run(["git", *args], cwd=work, check=True)
        git("init", "-q")
        git("remote", "add", "origin", REPO_URL)
        git("fetch", "-q", "--depth", "1", "origin", COMMIT)
        git("checkout", "-q", "FETCH_HEAD", "--", *INCLUDE)
        head = subprocess.run(["git", "rev-parse", "FETCH_HEAD"], cwd=work,
                              check=True, capture_output=True, text=True).stdout.strip()
        if head != COMMIT:
            raise SystemExit(f"fetched {head}, expected {COMMIT}")
        if DEST.exists():
            shutil.rmtree(DEST)
        DEST.mkdir(parents=True)
        for item in INCLUDE:
            source = work / item
            target = DEST / item
            target.parent.mkdir(parents=True, exist_ok=True)
            if source.is_dir():
                shutil.copytree(source, target)
            else:
                shutil.copy2(source, target)


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--verify", action="store_true", help="verify only")
    parser.add_argument("--write-manifest", action="store_true",
                        help="(maintainers) regenerate the manifest from DEST")
    args = parser.parse_args()
    if args.write_manifest:
        write_manifest()
        print(f"wrote {MANIFEST}")
        return 0
    if not args.verify and (not DEST.exists() or verify()):
        print(f"fetching {REPO_URL}@{COMMIT[:12]} ...")
        fetch()
    problems = verify()
    if problems:
        print("\n".join(problems[:20]))
        return 1
    print(f"benchmark OK: {len(json.loads(MANIFEST.read_text())['files'])} files, commit {COMMIT}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
