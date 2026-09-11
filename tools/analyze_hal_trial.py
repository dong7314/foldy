"""Summarize Poldy's filtered trial log; excludes phone screen contents."""
import json
import re
import statistics
import sys
from pathlib import Path


def analyze(path):
    samples, endpoints, departures, errors, reveals = [], [], [], [], []
    for line in path.read_text().splitlines():
        match = re.search(r"sample:angle=([\d.]+), sensor_ns=(\d+), age_ms=([\d.]+), visual=([\d.]+), moving=(true|false), source=(\w+)", line)
        if match:
            samples.append(dict(wall=line[:18], angle=float(match[1]), sensor_ns=int(match[2]),
                                age_ms=float(match[3]), visual=float(match[4]), moving=match[5] == "true", source=match[6]))
        match = re.search(r"endpoint_correction:target=(\d+), measured=([\d.]+), visual=([\d.]+), sample_age_ms=([-\d.]+)", line)
        if match and float(match[4]) >= 0:
            endpoints.append(dict(wall=line[:18], target=float(match[1]), measured=float(match[2]),
                                  visual=float(match[3]), sample_age_ms=float(match[4])))
        if "measured_departure:" in line:
            departures.append(line)
        if "reveal_finished:" in line:
            reveals.append(line)
        if " E " in line:
            errors.append(line)
    age = sorted(sample["age_ms"] for sample in samples)
    gaps = [(b["sensor_ns"] - a["sensor_ns"]) / 1e6 for a, b in zip(samples, samples[1:]) if a["angle"] != b["angle"]]
    summary = dict(samples=len(samples), unique_angles=len({s["angle"] for s in samples}),
                   angle_range=[min(s["angle"] for s in samples), max(s["angle"] for s in samples)] if samples else None,
                   age_ms_median=statistics.median(age) if age else None,
                   age_ms_p95=age[int(.95 * (len(age) - 1))] if age else None,
                   age_ms_max=max(age) if age else None,
                   changed_sample_gap_ms_median=statistics.median(gaps) if gaps else None,
                   endpoint_measured_distance_median=statistics.median(abs(e["target"] - e["measured"]) for e in endpoints) if endpoints else None,
                   endpoint_measured_distance_max=max(abs(e["target"] - e["measured"]) for e in endpoints) if endpoints else None,
                   endpoint_visual_distance_median=statistics.median(abs(e["target"] - e["visual"]) for e in endpoints) if endpoints else None,
                   endpoint_visual_distance_max=max(abs(e["target"] - e["visual"]) for e in endpoints) if endpoints else None,
                   endpoints=endpoints, departures=departures, reveals=reveals, errors=errors)
    path.with_name("samples.json").write_text(json.dumps(samples, indent=2) + "\n")
    path.with_name("summary.json").write_text(json.dumps(summary, indent=2) + "\n")
    print(json.dumps(summary, indent=2))


if __name__ == "__main__":
    analyze(Path(sys.argv[1]))
