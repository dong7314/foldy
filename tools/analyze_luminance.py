"""Correlate outgoing-panel zero-brightness intervals with Poldy transfers.

This measures SurfaceFlinger commands, not optical black frames on the OLED.
Accepts logcat epoch or threadtime timestamps; writes JSON to stdout.
"""
import json
import re
import sys
from pathlib import Path

BRIGHTNESS = re.compile(r"setDisplayBrightness\((\d+)\): displayBrightness: ([-\d.]+)")
PANELS = {"4630947004648141459": "inner", "4630947123231501204": "outer"}


def timestamp(line):
    epoch = re.match(r"\s*(\d{10}\.\d+)\s", line)
    if epoch:
        return float(epoch[1])
    clock = re.match(r"\d\d-\d\d (\d\d):(\d\d):(\d\d\.\d+)", line)
    if clock:
        return sum(float(x) * scale for x, scale in zip(clock.groups(), (3600, 60, 1)))
    return None


def analyze(path):
    transfers = []
    pending = None
    starts = {}
    intervals = []
    values = {}
    for line in Path(path).read_text(errors="replace").splitlines():
        stamp = timestamp(line)
        if stamp is None:
            continue
        begin = re.search(r"curtain_presented:target=(inner|outer)", line)
        if begin:
            pending = {"start": stamp, "target": begin[1], "end": None}
            transfers.append(pending)
        if "native_output_released:" in line and pending is not None:
            target = "inner" if "inner=true" in line else "outer"
            if target == pending["target"]:
                pending["end"] = stamp
                pending = None
        match = BRIGHTNESS.search(line)
        if not match or match[1] not in PANELS:
            continue
        panel, value = PANELS[match[1]], float(match[2])
        if value <= 0 and values.get(panel, 0) > 0:
            starts[panel] = stamp
        elif value > 0 and panel in starts:
            intervals.append((panel, starts.pop(panel), stamp))
        values[panel] = value
    report = []
    for transfer in transfers:
        if transfer["end"] is None:
            continue
        source = "outer" if transfer["target"] == "inner" else "inner"
        gaps = [round((min(end, transfer["end"]) - max(start, transfer["start"])) * 1000, 3)
                for panel, start, end in intervals if panel == source
                and end > transfer["start"] and start < transfer["end"]]
        report.append({**transfer, "outgoing": source, "zero_intervals_ms": gaps,
                       "total_zero_ms": round(sum(gaps), 3), "max_zero_ms": max(gaps, default=0)})
    return {"metric": "SurfaceFlinger command intervals; not optical frame durations",
            "transfers": report, "unclosed_dark_panels": list(starts)}


if __name__ == "__main__":
    print(json.dumps(analyze(sys.argv[1]), indent=2))
