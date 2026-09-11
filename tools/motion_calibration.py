"""Fit an approximate primary-gyro scale from a Poldy motion probe JSONL."""
import argparse
import json
import math
import statistics
from pathlib import Path


def calibrate(path):
    rows = [json.loads(line) for line in Path(path).read_text().splitlines()]
    hinge = [row for row in rows if row['event'] == 'hinge']
    gyro = [row for row in rows if row['event'] == 'motion' and row['type'] == 4]
    cycles = []
    for index, start in enumerate(hinge):
        if start['angle_deg'] != 90 or index == 0 or index + 1 >= len(hinge):
            continue
        end = hinge[index + 1]
        # Approximate margin: the public notification can follow initial movement.
        samples = [row for row in gyro if start['elapsed_ms'] - 700 <= row['elapsed_ms'] <= end['elapsed_ms']]
        degrees = math.degrees(sum((b['y'] + a['y']) / 2 * (b['timestamp_ns'] - a['timestamp_ns']) / 1e9
                                   for a, b in zip(samples, samples[1:])))
        cycles.append({'from': hinge[index-1]['angle_deg'], 'to': end['angle_deg'],
                       'gyro_y_degrees': degrees, 'usable': 20 < abs(degrees) < 180})
    opening = [180 / c['gyro_y_degrees'] for c in cycles if c['usable'] and c['to'] == 180 and c['gyro_y_degrees'] > 0]
    closing = [-180 / c['gyro_y_degrees'] for c in cycles if c['usable'] and c['to'] == 0 and c['gyro_y_degrees'] < 0]
    if len(opening) < 2 or len(closing) < 2:
        raise ValueError('Insufficient consistent motion; do not invent a calibrated angle.')
    return {'source': 'single_primary_gyro_y', 'experimental': True,
            'opening_gain': round(statistics.median(opening), 4),
            'closing_gain': round(statistics.median(closing), 4), 'cycles': cycles,
            'limitation': 'Whole-device yaw cannot be separated from hinge rotation; hand placement changes the scale. Public endpoints reset accumulated error.'}


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('recording'); parser.add_argument('output')
    args = parser.parse_args()
    Path(args.output).write_text(json.dumps(calibrate(args.recording), indent=2) + '\n')
