"""Check actual GPU boundary pixels from solid inputs, not a CPU copy of the shader."""
from pathlib import Path
from PIL import Image, ImageDraw
import json
import math
import sys


def analyze(directory):
    cases = json.loads((directory / "edge-cases.json").read_text())
    profiles = []
    for case in cases:
        frame = Image.open(directory / case["file"]).convert("RGB")
        inner, angle = case["inner"], case["angle"]
        if (inner and angle == 180) or (not inner and angle == 0):
            assert frame.getextrema() == ((255, 255),) * 3, "Visible endpoint must stay exactly sharp"
        if inner:
            stationary = frame.crop((int(frame.width * .7), 0, frame.width, frame.height))
            assert stationary.getextrema() == ((255, 255),) * 3, "Stationary face must not acquire a vignette"
        if angle not in (90, 110):
            continue
        q = case["quad"]
        dx, dy = q[2] - q[0], q[3] - q[1]
        length = math.hypot(dx, dy)
        nx, ny = -dy / length, dx / length
        x, y = (q[0] + q[2]) / 2, (q[1] + q[3]) / 2
        readings = []
        # The 0.13 source feather is blurred with the content, so colour intentionally
        # extends outside the geometric edge. Scan from the dark wedge through that halo.
        for distance in range(-math.ceil(case["softness"]), math.ceil(case["softness"] * 2)):
            pixel = frame.getpixel((round(x + nx * distance), round(y + ny * distance)))
            readings.append({"distance_pixels": distance, "mean_rgb": sum(pixel) / 3})
        values = [r["mean_rgb"] for r in readings]
        biggest_step = max(abs(b - a) for a, b in zip(values, values[1:]))
        assert values[0] < 12, "The wedge itself must remain dark"
        assert values[-1] - values[0] > 40, "The scan must reach actual content"
        assert len(set(round(v) for v in values)) > 16, "Boundary must contain a genuine gradual transition"
        assert biggest_step < 18, "A sharp brightness jump remains in the GPU output"
        profiles.append({"inner": inner, "angle": angle, "largest_one_pixel_step": biggest_step,
                         "distinct_brightness_levels": len(set(round(v) for v in values)), "samples": readings})
    report = {"passed": True, "profiles": profiles, "visible_endpoints_exact": True,
              "inner_stationary_face_exact": True,
              "scope": "Generated solid pixels on the phone GPU; not physical-motion timing or a claim of reference identity."}
    (directory / "boundary-check.json").write_text(json.dumps(report, indent=2))
    sheet = Image.new("RGB", (1440, 1280), "#101920")
    labels = ImageDraw.Draw(sheet)
    for column, panel in enumerate(("inner", "outer")):
        frame = Image.open(directory / f"{panel}-edge-colour.png").convert("RGB")
        frame.thumbnail((690, 1160))
        sheet.paste(frame, (column * 720 + 15, 42))
        labels.text((column * 720 + 15, 16), f"{panel} / softened projected boundary", fill="white")
    sheet.save(directory / "soft-boundary.jpg", quality=96)
    return report


if __name__ == "__main__":
    result = analyze(Path(sys.argv[1]))
    print(json.dumps({"passed": result["passed"], "profiles": [
        {k: v for k, v in p.items() if k != "samples"} for p in result["profiles"]]}, indent=2))
