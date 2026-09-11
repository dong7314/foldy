"""Compare explicit GPU benchmark output; never treat this as a device-frame-rate test."""
from pathlib import Path
from PIL import Image, ImageChops, ImageStat, ImageDraw
import json
import statistics
import sys


def analyze(directory):
    runs = json.loads((directory / "timing.json").read_text())
    report = {"timing": [], "quality": []}
    for inner in (False, True):
        for reduced in (False, True):
            values = sorted(x for run in runs
                            if run["inner"] == inner and run["reduced"] == reduced
                            for x in run["milliseconds"])
            report["timing"].append({
                "panel": "inner" if inner else "outer", "optimized": reduced,
                "n": len(values), "median_ms": statistics.median(values),
                "p95_ms": values[int(len(values) * .95)], "max_ms": max(values),
            })
    for panel in ("inner", "outer"):
        for angle in (0, 45, 90, 135, 180):
            a = Image.open(directory / f"{panel}-native-{angle}.png").convert("RGB")
            b = Image.open(directory / f"{panel}-reduced-{angle}.png").convert("RGB")
            difference = ImageChops.difference(a, b)
            width, height = a.size
            sharp = difference.crop((int(width * .75), int(height * .3), width, int(height * .7)))
            report["quality"].append({
                "panel": panel, "angle": angle,
                "mean_rgb_difference": sum(ImageStat.Stat(difference).mean) / 3,
                "max_channel_difference": max(v[1] for v in difference.getextrema()),
                "identical": difference.getbbox() is None,
                "inner_stationary_middle_identical": sharp.getbbox() is None if panel == "inner" else None,
            })
            if panel == "inner":
                native_sharp = b.crop((int(width * .75), int(height * .3), width, int(height * .7)))
                endpoint = Image.open(directory / "inner-reduced-180.png").convert("RGB").crop(
                    (int(width * .75), int(height * .3), width, int(height * .7)))
                report["quality"][-1]["optimized_stationary_unchanged_across_angles"] = ImageChops.difference(native_sharp, endpoint).getbbox() is None
                report["quality"][-1]["stationary_reference_max_difference"] = max(v[1] for v in sharp.getextrema())
    report["scope"] = "Generated native-size pixels, isolated GPU render and fence; not whole-app FPS or physical hinge accuracy."
    (directory / "report.json").write_text(json.dumps(report, indent=2))
    # Unenhanced side-by-side output, with one representative angle per panel.
    sheet = Image.new("RGB", (1280, 1540), "#101920")
    labels = ImageDraw.Draw(sheet)
    labels.text((20, 10), "ORIGINAL", fill="white")
    labels.text((660, 10), "OPTIMIZED", fill="white")
    for row, (panel, angle) in enumerate((("inner", 135), ("outer", 45))):
        for column, mode in enumerate(("native", "reduced")):
            image = Image.open(directory / f"{panel}-{mode}-{angle}.png").convert("RGB")
            image.thumbnail((620, 940))
            x = 20 + column * 640
            y = 40 if row == 0 else 560
            sheet.paste(image, (x, y))
            labels.text((x, y - 16), f"{panel} {angle} degrees", fill="white")
    sheet.save(directory / "comparison.jpg", quality=96)
    return report


if __name__ == "__main__":
    print(json.dumps(analyze(Path(sys.argv[1])), indent=2))
