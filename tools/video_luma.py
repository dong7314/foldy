#!/usr/bin/env python3
"""Portable counterpart of video_luma.swift. Requires PyAV and Pillow.

Uses the same central 80% grid, Rec.709 luma, and <10 dark threshold.
PTS samples describe the recording, not physical OLED emission time.
"""
import argparse
import json
from pathlib import Path
import av


def analyze(source):
    rows = []
    with av.open(str(source)) as video:
        for frame in video.decode(video=0):
            image = frame.to_image()
            width, height = image.size
            pixels = image.load()
            samples = []
            for y in range(height // 10, height * 9 // 10, max(1, height // 100)):
                for x in range(width // 10, width * 9 // 10, max(1, width // 100)):
                    r, g, b = pixels[x, y]
                    samples.append(.2126 * r + .7152 * g + .0722 * b)
            rows.append({"seconds": float(frame.time), "mean": sum(samples) / len(samples),
                         "dark_fraction": sum(v < 10 for v in samples) / len(samples),
                         "max": max(samples)})
    if not rows:
        raise ValueError("No decoded frames")
    return rows


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("source", type=Path)
    parser.add_argument("output", type=Path)
    args = parser.parse_args()
    rows = analyze(args.source)
    args.output.write_text(json.dumps(rows, indent=2), encoding="utf-8")
    print(json.dumps({"frames": len(rows), "all_samples_below_10":
                      [row for row in rows if row["max"] < 10]}))
