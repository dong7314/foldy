"""Inspect actual phone-GPU pixels for IMU response, coverage, reversals and early diffusion."""
from pathlib import Path
from PIL import Image, ImageChops, ImageStat, ImageFilter, ImageDraw
import json
import sys


def mean_difference(a, b):
    return sum(ImageStat.Stat(ImageChops.difference(a, b)).mean) / 3


def analyze(directory):
    results = []
    sheet = Image.new("RGB", (1440, 1150), "#101920")
    labels = ImageDraw.Draw(sheet)
    for row, panel in enumerate(("inner", "outer")):
        def read(suffix):
            return Image.open(directory / f"{panel}-motion-{suffix}.png").convert("RGB")
        neutral = read("neutral")
        endpoint = read("angle-180" if panel == "inner" else "angle-0")
        assert ImageChops.difference(neutral, read("returned")).getbbox() is None, "Reversed pose changed its pixels"
        assert ImageChops.difference(endpoint, read("endpoint-tilted")).getbbox() is None, "Tilt moved a visible endpoint"
        gyro = mean_difference(read("gyro--1"), read("gyro-1"))
        gravity = mean_difference(neutral, read("gravity"))
        assert gyro > .05 and gravity > .05, "IMU input has no visible GPU response"
        feather = []
        for tilt in (-1, 0, 1):
            solid = Image.open(directory / f"{panel}-motion-solid-{tilt}.png").convert("RGBA")
            assert solid.getchannel("A").getextrema() == (255, 255), "A transparent edge was exposed"
            # Scan the feather where content is lit. The inner free edge at x=.08 is
            # intentionally near black (only 4..10/255), so it cannot contain 20 levels.
            x = round(solid.width * (.25 if panel == "inner" else .82))
            values = [sum(solid.getpixel((x, y))[:3]) / 3 for y in range(solid.height // 3)]
            jump = max(abs(a - b) for a, b in zip(values, values[1:]))
            levels = len(set(round(v) for v in values))
            assert jump <= 4, "Top feather still has a hard pixel step"
            assert levels >= 20, "Shading does not form a broad feather"
            feather.append({"tilt": tilt, "max_one_pixel_step": jump, "brightness_levels": levels})
        # Neutral IMU keeps geometry fixed, so these differences come from the material.
        w, h = endpoint.size
        box = (int(w * (.05 if panel == "inner" else .7)), int(h * .2),
               int(w * (.3 if panel == "inner" else .94)), int(h * .8))
        sharpness = []
        for departure in (0, 1, 2, 6, 12):
            angle = 180 - departure if panel == "inner" else departure
            frame = read(f"angle-{angle}").crop(box).convert("L")
            edge = frame.filter(ImageFilter.FIND_EDGES).crop((1, 1, frame.width-1, frame.height-1))
            sharpness.append({"departure_degrees": departure, "edge_mean": ImageStat.Stat(edge).mean[0]})
        assert sharpness[2]["edge_mean"] < sharpness[0]["edge_mean"], "No diffusion at 2 degrees departure"
        last = read("angle-179" if panel == "inner" else "angle-1")
        last_step = mean_difference(last, endpoint)
        assert 0 < last_step < 3, "Last degree introduces an abrupt material change"
        results.append({"panel": panel, "gyro_difference": gyro, "gravity_difference": gravity,
                        "reversal_identical": True, "endpoint_unchanged_by_tilt": True,
                        "opaque_coverage": True, "feather": feather, "early_diffusion": sharpness,
                        "last_degree_mean_rgb_difference": last_step})
        for column, (suffix, label) in enumerate((("gyro--1", "tilt -"), ("neutral", "neutral"), ("gyro-1", "tilt +"))):
            frame = read(suffix);frame.thumbnail((450, 505))
            x, y = 15 + column * 480, 30 + row * 565
            sheet.paste(frame, (x, y));labels.text((x, y-18), f"{panel} / {label}", fill="white")
    report = {"passed": True, "panels": results,
              "scope": "Generated pixels on the Android GPU; not optical flicker, app layout, physical sensor latency, or exact reference identity."}
    (directory / "motion-check.json").write_text(json.dumps(report, indent=2))
    sheet.save(directory / "motion-comparison.jpg", quality=95)
    return report


if __name__ == "__main__":
    print(json.dumps(analyze(Path(sys.argv[1])), indent=2))
