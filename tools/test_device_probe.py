"""Synthetic traces validate event interpretation; they are not device measurements."""
import unittest
from device_probe import transition_rows


def sample(width, angle, direction=1, age=0, panel_id=0):
    return {"displays": [{"id": panel_id, "state": 2, "logical_width": width, "logical_height": 2000}],
            "angle_deg": angle, "angle_age_ms": age, "direction": direction, "elapsed_ms": 123}


class TraceTests(unittest.TestCase):
    def test_same_logical_id_can_switch_panels(self):
        rows = transition_rows([sample(900, 0), sample(900, 80), sample(1800, 91)])
        self.assertEqual(len(rows), 1)
        self.assertEqual(rows[0]["angle_deg"], 91)

    def test_opening_and_closing_thresholds_stay_separate(self):
        rows = transition_rows([sample(900, 0), sample(1800, 91), sample(1800, 170), sample(900, 5, -1)])
        self.assertEqual([(r["angle_deg"], r["direction"]) for r in rows], [(91, 1), (5, -1)])

    def test_missing_and_stale_angles_are_not_filled_in(self):
        rows = transition_rows([sample(900, None), sample(1800, None), sample(900, 45, -1, 5000)])
        self.assertIsNone(rows[0]["angle_deg"])
        self.assertEqual(rows[1]["angle_age_ms"], 5000)

    def test_display_removed_then_added_is_preserved(self):
        rows = transition_rows([sample(900, 20), {"displays": [], "angle_deg": 21}, sample(1800, 22, panel_id=1)])
        self.assertEqual(len(rows), 2)
        self.assertEqual(rows[0]["after"], ())


if __name__ == "__main__":
    unittest.main()
