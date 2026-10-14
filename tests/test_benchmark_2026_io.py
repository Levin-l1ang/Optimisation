import json
import subprocess
import unittest
from pathlib import Path

from benchmark_2026 import BENCHMARK_COMMIT, paths
from benchmark_2026.io import BenchmarkFormatError, load_instance, validate_instance

ROOT = Path(__file__).resolve().parents[1]


class BenchmarkIOTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.inst = load_instance(paths.INSTANCE)

    def test_pinned_files_match_manifest(self):
        out = subprocess.run(["python3", str(ROOT / "scripts/fetch_benchmark.py"), "--verify"],
                             capture_output=True, text=True)
        self.assertEqual(out.returncode, 0, out.stdout + out.stderr)
        manifest = json.loads((ROOT / "data/external/benchmark_manifest.json").read_text())
        self.assertEqual(manifest["commit"], BENCHMARK_COMMIT)

    def test_primary_instance_dimensions(self):
        self.assertEqual(len(self.inst.connections), 80)
        self.assertEqual(len(self.inst.od_pairs), 416)
        self.assertEqual(len(self.inst.active_stations), 29)   # not the 110 metadata rows
        self.assertEqual(len(self.inst.stations), 110)
        self.assertAlmostEqual(sum(o.volume for o in self.inst.od_pairs), 58397.78, places=2)

    def test_parameters_are_the_primary_configuration(self):
        p = self.inst.parameters
        self.assertEqual((p.line_detour_ratio, p.line_max_duration), (1.1, 60.0))
        self.assertEqual((p.route_max_detour, p.transfer_penalty, p.fixed_cost, p.budget), (15, 15, 1.0, 60.0))
        self.assertTrue(p.has_ics and not p.has_sprs)

    def test_reverse_connections_are_symmetric(self):
        for c in self.inst.connections.values():
            r = self.inst.connections[c.reverse_id]
            self.assertEqual(r.reverse_id, c.connection_id)
            self.assertEqual((r.from_station, r.to_station), (c.to_station, c.from_station))

    def test_validation_detects_broken_reference(self):
        import dataclasses
        bad = dict(self.inst.connections)
        c = bad[0]
        bad[0] = dataclasses.replace(c, reverse_id=999)
        with self.assertRaises(BenchmarkFormatError):
            validate_instance(dataclasses.replace(self.inst, connections=bad))

    def test_all_od_stations_are_active(self):
        active = set(self.inst.active_stations)
        for od in self.inst.od_pairs:
            self.assertIn(od.origin, active)
            self.assertIn(od.destination, active)


if __name__ == "__main__":
    unittest.main()
