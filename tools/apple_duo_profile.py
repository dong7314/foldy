#!/usr/bin/env python3
"""Extract the official iPhone Duo Slider animation at its authored 30 fps."""

from __future__ import annotations

import argparse
import json
import math
import struct
from pathlib import Path


COMPONENTS = {"SCALAR": 1, "VEC2": 2, "VEC3": 3, "VEC4": 4, "MAT4": 16}
FORMATS = {5126: ("f", 4), 5125: ("I", 4), 5123: ("H", 2), 5121: ("B", 1)}


def read_accessor(gltf: dict, blob: bytes, index: int) -> list[tuple[float, ...]]:
    accessor = gltf["accessors"][index]
    view = gltf["bufferViews"][accessor["bufferView"]]
    count = COMPONENTS[accessor["type"]]
    fmt, size = FORMATS[accessor["componentType"]]
    offset = view.get("byteOffset", 0) + accessor.get("byteOffset", 0)
    stride = view.get("byteStride", count * size)
    return [struct.unpack_from("<" + fmt * count, blob, offset + row * stride)
            for row in range(accessor["count"])]


def quaternion_distance(a: tuple[float, ...], b: tuple[float, ...]) -> float:
    # q and -q describe the same orientation.
    dot = min(1.0, abs(sum(x * y for x, y in zip(a, b))))
    return 2.0 * math.degrees(math.acos(dot))


def rounded(value: float) -> float:
    return round(value, 6)


def clamp(value: float, low: float = 0.0, high: float = 1.0) -> float:
    return max(low, min(high, value))


def smoothstep(low: float, high: float, value: float) -> float:
    position = clamp((value - low) / (high - low))
    return position * position * (3 - 2 * position)


def wipe_amount(inner: bool, progress: float) -> float:
    if inner:
        return clamp(1.2 * (1 - progress))
    return 0.5 * clamp(1 - 2 * abs(progress - 0.5))


def blur_area(inner: bool, progress: float, x: float) -> float:
    distance = 1 - x if inner else x
    low, high = (0.45, 1.0) if inner else (0.0, 0.9)
    source = clamp((distance - low) / (high - low) * wipe_amount(inner, progress) * 2.5)
    return source / 0.75


def extract(gltf_path: Path) -> dict:
    gltf = json.loads(gltf_path.read_text(encoding="utf-8"))
    blob = (gltf_path.parent / gltf["buffers"][0]["uri"]).read_bytes()
    animation = next(item for item in gltf["animations"] if item.get("name") == "Slider")
    tracks: dict[int, list[tuple[float, ...]]] = {}
    times = None
    for channel in animation["channels"]:
        if channel["target"]["path"] != "rotation":
            continue
        sampler = animation["samplers"][channel["sampler"]]
        current_times = read_accessor(gltf, blob, sampler["input"])
        if times is None:
            times = current_times
        elif current_times != times:
            raise ValueError("Slider tracks do not share one authored timeline")
        tracks[channel["target"]["node"]] = read_accessor(gltf, blob, sampler["output"])
    if times is None or len(times) != 61:
        raise ValueError(f"Expected 61 Slider samples, found {0 if times is None else len(times)}")

    deform_nodes = list(range(10, 33))
    moving_face_node = 33
    hinge_guide_node = 35
    raw_energy = []
    for frame in range(len(times)):
        deltas = [quaternion_distance(tracks[node][frame], tracks[node][-1])
                  for node in deform_nodes]
        raw_energy.append(math.sqrt(sum(value * value for value in deltas) / len(deltas)))
    open_energy, closed_energy = raw_energy[-1], raw_energy[0]

    frames = []
    for frame, sample in enumerate(times):
        progress = sample[0] / times[-1][0]
        crease = (raw_energy[frame] - open_energy) / (closed_energy - open_energy)
        if progress <= 1 / 3:
            brightness = 0.15 + 0.10 * progress * 3
        else:
            brightness = 0.25 + 0.75 * (progress - 1 / 3) * 1.5
        inner_wipe = wipe_amount(True, progress)
        outer_wipe = wipe_amount(False, progress)
        frames.append({
            "frame": frame,
            "seconds": rounded(sample[0]),
            "progress": rounded(progress),
            "movingFaceDegrees": rounded(quaternion_distance(
                tracks[moving_face_node][0], tracks[moving_face_node][frame])),
            "hingeGuideDegrees": rounded(quaternion_distance(
                tracks[hinge_guide_node][0], tracks[hinge_guide_node][frame])),
            "creaseRmsDegrees": rounded(raw_energy[frame]),
            "creaseNormalized": rounded(crease),
            "releaseNormalized": rounded(1 - crease),
            "innerScreenBrightness": rounded(brightness),
            "innerEmissiveMultiplier": rounded(smoothstep(0.1, 1.0, brightness)),
            "innerWipeAmount": rounded(inner_wipe),
            "outerWipeAmount": rounded(outer_wipe),
            "innerBlurArea": [rounded(blur_area(True, progress, x))
                              for x in (0, 0.25, 0.5, 0.75, 1)],
            "outerBlurArea": [rounded(blur_area(False, progress, x))
                              for x in (0, 0.25, 0.5, 0.75, 1)],
            "boneOpenDeltaDegrees": [rounded(quaternion_distance(
                tracks[node][frame], tracks[node][-1])) for node in deform_nodes],
        })

    return {
        "source": "Apple iPhone Duo product gallery Slider animation",
        "sourcePage": "https://www.apple.com/kr/iphone-duo/",
        "animation": "Slider",
        "durationSeconds": 2,
        "authoredFps": 30,
        "frameCount": len(frames),
        "deformBoneNodes": deform_nodes,
        "stateParameters": {
            "hingeTarget": {"closed": 0, "landing": 0.3333, "open": 1},
            "springBounce": 0.15,
            "springDuration": 0.75,
            "magnetClosedRadius": 0.08,
            "magnetClosedStrength": 30,
            "magnetOpenRadius": 0.08,
            "magnetOpenStrength": 20,
            "innerScreenBrightness": {"closed": 0.15, "landing": 0.25, "open": 1},
            "innerWipe": {"factor": -1.2, "offset": 1.2,
                          "blurBounds": [0.45, 1], "shadeBounds": [0.5, 1]},
            "outerPortraitWipe": {"peak": 0.5, "peakProgress": 0.5,
                                  "blurBounds": [0, 0.9], "shadeBounds": [0, 1]},
            "blurPass": {"mipLodMaximum": 8, "passes": 2,
                         "amountScale": 2.5, "areaRemapMaximum": 0.75},
            "fadeThroughBlackSeconds": 0.38,
            "fadeThroughBlackHoldSeconds": 0,
        },
        "frames": frames,
    }


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("gltf", type=Path)
    parser.add_argument("output", type=Path)
    args = parser.parse_args()
    profile = extract(args.gltf)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(profile, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"wrote {args.output} ({profile['frameCount']} frames)")
    print("authored 23-bone crease release table:")
    values = [frame["releaseNormalized"] for frame in profile["frames"]]
    for start in range(0, len(values), 8):
        print("    " + ", ".join(f"{value:.6f}f" for value in values[start:start + 8]) + ",")


if __name__ == "__main__":
    main()
