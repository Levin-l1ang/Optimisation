import json
import re
import shutil
import subprocess
import tempfile
import unittest
from pathlib import Path

from benchmark_2026 import paths
from benchmark_2026.io import load_instance
from benchmark_2026.line_pool import (
    canonical_hash, load_line_pool, reference_line_enumeration, validate_line_pool,
)

ROOT = Path(__file__).resolve().parents[1]


def _jdk_major() -> int:
    if not shutil.which("javac"):
        return 0
    out = subprocess.run(["javac", "-version"], capture_output=True, text=True)
    match = re.search(r"javac\s+(\d+)", out.stdout + out.stderr)
    return int(match.group(1)) if match else 0


class LinePoolTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.inst = load_instance(paths.INSTANCE)
        cls.lines, cls.provenance = load_line_pool(paths.LINE_POOL)

    def test_small_pool_has_exactly_93_lines(self):
        self.assertEqual(len(self.lines), 93)

    def test_pool_is_the_official_generator_output(self):
        self.assertIn("official", self.provenance["generator_status"])
        self.assertEqual(self.provenance["line_generation_setting"], "small")

    def test_independent_rule_check_and_oracle_agree(self):
        report = validate_line_pool(self.inst, self.lines, oracle=True)
        self.assertEqual(report["rule_problems"], 0)
        self.assertEqual((report["missing_from_pool"], report["extra_in_pool"]), (0, 0))

    def test_cost_duration_and_frequency_match_reference_formulas(self):
        line = self.lines[0]
        self.assertAlmostEqual(line.duration, 27.44)
        self.assertAlmostEqual(line.cost_per_frequency, (2 * 27.44 + 60) / 60)
        self.assertEqual(line.max_frequency, 10)
        self.assertEqual(sorted({l.max_frequency for l in self.lines}), [2, 10, 20])

    def test_backward_service_uses_reverse_connections(self):
        for l in self.lines:
            back = tuple(self.inst.connections[c].reverse_id for c in reversed(l.connections_forward))
            self.assertEqual(l.connections_backward, back)

    def test_corrupted_pool_is_rejected(self):
        with tempfile.TemporaryDirectory() as tmp:
            copy = Path(tmp) / "pool.json"
            doc = json.loads(paths.LINE_POOL.read_text())
            doc["lines"][0]["duration"] += 1
            copy.write_text(json.dumps(doc))
            with self.assertRaises(ValueError):
                load_line_pool(copy)

    def test_duplicate_or_missing_line_fails_oracle(self):
        with self.assertRaises(AssertionError):
            validate_line_pool(self.inst, self.lines[:-1], oracle=True)

    def test_ic_lines_may_exceed_60_minutes(self):
        # The "60" in LineGeneration/small.txt bounds Sprinter duration only (see
        # LinePoolGenerator.satisfiesSprinterDuration); Randstad-IC has no Sprinters.
        self.assertGreater(max(l.duration for l in self.lines), 60)

    @unittest.skipUnless(_jdk_major() >= 21, "JDK 21 required to regenerate")
    def test_regeneration_is_reproducible(self):
        with tempfile.TemporaryDirectory() as tmp:
            out = Path(tmp)
            (out / "w.txt").write_text("45\n")
            subprocess.run([str(ROOT / "scripts/run_java_export.sh"), str(out), str(out / "w.txt")],
                           check=True, capture_output=True)
            fresh = [json.loads(x) for x in (out / "lines.jsonl").read_text().splitlines()]
            frozen = json.loads(paths.LINE_POOL.read_text())["lines"]
            self.assertEqual(fresh, frozen)
            self.assertEqual((out / "routes.jsonl").read_bytes(), paths.OFFICIAL_ROUTES.read_bytes())
            self.assertEqual((out / "od_bounds.jsonl").read_bytes(), paths.OD_BOUNDS.read_bytes())


if __name__ == "__main__":
    unittest.main()
