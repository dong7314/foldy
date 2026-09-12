#!/usr/bin/env python3
"""Decode every frame of a local reference video and create reproducible metrics/contact sheets."""

from __future__ import annotations

import argparse
import json
from pathlib import Path

import av
import numpy as np
from PIL import Image, ImageDraw


def gradient_energy(gray: np.ndarray) -> float:
    dx = np.abs(np.diff(gray, axis=1)).mean()
    dy = np.abs(np.diff(gray, axis=0)).mean()
    return float(dx + dy)


def region_metrics(rgb: np.ndarray) -> dict:
    gray = (rgb[..., 0] * .2126 + rgb[..., 1] * .7152 + rgb[..., 2] * .0722).astype(np.float32)
    height, width = gray.shape
    regions = {
        "all": gray,
        "left": gray[:, :width // 2],
        "right": gray[:, width // 2:],
        "center": gray[height // 5:height * 4 // 5, width // 5:width * 4 // 5],
    }
    return {name: {"meanLuma": round(float(values.mean()), 5),
                   "darkFraction": round(float((values < 10).mean()), 7),
                   "gradientEnergy": round(gradient_energy(values), 5)}
            for name, values in regions.items()}


def contact_sheet(frames: list[tuple[int, float, Image.Image]], target: Path, columns: int = 5) -> None:
    thumb_w, thumb_h, label_h = 302, 258, 26
    rows = (len(frames) + columns - 1) // columns
    sheet = Image.new("RGB", (columns * thumb_w, rows * (thumb_h + label_h)), "#101418")
    draw = ImageDraw.Draw(sheet)
    for slot, (index, seconds, frame) in enumerate(frames):
        image = frame.copy(); image.thumbnail((thumb_w, thumb_h))
        x = (slot % columns) * thumb_w + (thumb_w - image.width) // 2
        y = (slot // columns) * (thumb_h + label_h)
        sheet.paste(image, (x, y))
        draw.text((slot % columns * thumb_w + 6, y + thumb_h + 4),
                  f"f{index:03d}  {seconds:.3f}s", fill="white")
    sheet.save(target)


def analyze(source: Path, output: Path, contact_step: int) -> dict:
    output.mkdir(parents=True, exist_ok=True)
    rows, contacts = [], []
    previous = None
    with av.open(str(source)) as container:
        stream = container.streams.video[0]
        metadata = {"source": str(source), "width": stream.width, "height": stream.height,
                    "codec": stream.codec_context.name,
                    "averageRate": float(stream.average_rate), "timeBase": str(stream.time_base)}
        for index, decoded in enumerate(container.decode(stream)):
            image = decoded.to_image().convert("RGB")
            rgb = np.asarray(image, dtype=np.float32)
            row = {"frame": index, "seconds": round(float(decoded.time), 6), **region_metrics(rgb)}
            row["meanAbsoluteChange"] = None if previous is None else round(float(np.abs(rgb - previous).mean()), 5)
            rows.append(row); previous = rgb
            if index % contact_step == 0:
                contacts.append((index, float(decoded.time), image))
    if not rows:
        raise ValueError("No decoded video frames")
    metadata.update({"frameCount": len(rows), "durationSeconds": rows[-1]["seconds"],
                     "contactStepFrames": contact_step})
    (output / "frame-metrics.json").write_text(json.dumps({"metadata": metadata, "frames": rows}, indent=2), encoding="utf-8")
    for start in range(0, len(contacts), 30):
        contact_sheet(contacts[start:start + 30], output / f"contact-{start // 30 + 1:02d}.jpg")
    return metadata


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("source", type=Path)
    parser.add_argument("output", type=Path)
    parser.add_argument("--contact-step", type=int, default=5)
    args = parser.parse_args()
    print(json.dumps(analyze(args.source, args.output, args.contact_step), indent=2))


if __name__ == "__main__":
    main()
