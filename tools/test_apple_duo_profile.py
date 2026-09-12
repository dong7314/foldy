import unittest

from apple_duo_profile import blur_area, smoothstep, wipe_amount


class AppleDuoMaterialTest(unittest.TestCase):
    def test_inner_landscape_wipe(self):
        self.assertEqual(wipe_amount(True, 0), 1)
        self.assertAlmostEqual(wipe_amount(True, 1 / 3), 0.8)
        self.assertEqual(wipe_amount(True, 1), 0)

    def test_outer_portrait_wipe_is_a_midfold_pulse(self):
        self.assertEqual(wipe_amount(False, 0), 0)
        self.assertEqual(wipe_amount(False, 0.5), 0.5)
        self.assertEqual(wipe_amount(False, 1), 0)

    def test_blur_runs_in_opposite_spatial_directions(self):
        self.assertGreater(blur_area(True, 1 / 3, 0), blur_area(True, 1 / 3, 0.5))
        self.assertGreater(blur_area(False, 0.5, 1), blur_area(False, 0.5, 0.5))

    def test_landing_brightness_is_non_linear(self):
        self.assertAlmostEqual(smoothstep(0.1, 1, 0.25), 0.074074074, places=6)


if __name__ == "__main__":
    unittest.main()
