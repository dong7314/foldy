import unittest
from state_trial import validate_trial, physical_displays


class StateTrialTest(unittest.TestCase):
    empty = 'mOverrideState=Optional.empty\nOverride Request active: false'

    def test_requires_supported_state_and_bounded_duration(self):
        validate_trial('0,1,2,3,4,5', self.empty, 5, 15)
        for state, seconds in [(9, 15), (5, 0), (5, 21)]:
            with self.assertRaises(RuntimeError):
                validate_trial('0,1,2,3,4,5', self.empty, state, seconds)

    def test_refuses_existing_or_unknown_override(self):
        for dump in ['', 'mOverrideState=Optional[DeviceState{identifier=4}]',
                     'mOverrideState=Optional.empty\nOverride Request active: true']:
            with self.assertRaises(RuntimeError):
                validate_trial('0,1,2,3,4,5', dump, 5, 15)

    def test_extracts_physical_not_logical_display_state(self):
        dump = '''DisplayDeviceInfo{"built-in": uniqueId="local:123", 2448 x 1848, other stuff, state ON, committedState OFF, flags}
        mDisplayInfo=DisplayInfo{"logical", state ON}'''
        self.assertEqual(physical_displays(dump), [{'unique_id': 'local:123', 'width': 2448,
                         'height': 1848, 'state': 'ON', 'committed_state': 'OFF'}])


if __name__ == '__main__':
    unittest.main()
