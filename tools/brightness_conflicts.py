"""Find fast positive A→B→A brightness reversals inside outgoing-panel transfers.

This is a SurfaceFlinger command metric, not an optical flicker measurement.
"""
import json
import re
import sys
from pathlib import Path
from analyze_luminance import analyze, timestamp, PANELS


def report(path):
    rows = []
    pattern = re.compile(r"setDisplayBrightness\((\d+)\): displayBrightness: ([-\d.]+), displayBrightnessNits: ([-\d.]+)")
    for line in Path(path).read_text(errors="replace").splitlines():
        m = pattern.search(line)
        if m and m[1] in PANELS and float(m[2]) > 0:
            rows.append((timestamp(line), PANELS[m[1]], float(m[3])))
    cases = []
    for t in analyze(path)["transfers"]:
        sequence = []
        for stamp, panel, nits in rows:
            if panel == t["outgoing"] and t["start"] <= stamp <= t["end"]:
                if not sequence or abs(nits - sequence[-1][1]) > .2:
                    sequence.append((stamp, nits))
        reversals = []
        for a, b, c in zip(sequence, sequence[1:], sequence[2:]):
            if (abs(a[1] - c[1]) <= max(.5, a[1] * .005)
                    and abs(b[1] - a[1]) > max(5, a[1] * .1)
                    and c[0] - b[0] <= .020):
                reversals.append({"nits": [round(x[1], 2) for x in (a, b, c)],
                                  "return_ms": round((c[0] - b[0]) * 1000, 3)})
        cases.append({"start": t["start"], "outgoing": t["outgoing"], "fast_positive_reversals": len(reversals),
                      "examples": reversals[:3], "max_zero_command_ms": t["max_zero_ms"]})
    return {"metric": "Positive A-B-A commands within 20 ms; not optical frames",
            "total_fast_positive_reversals": sum(x["fast_positive_reversals"] for x in cases), "transfers": cases}


if __name__ == "__main__":
    print(json.dumps(report(sys.argv[1]), indent=2))
