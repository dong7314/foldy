#!/usr/bin/env python3
"""Read-only ADB measurements for Poldy. Does not override device state or settings."""
import argparse
import datetime as dt
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys
import time

ROOT = Path(__file__).resolve().parents[1]
PACKAGE = "dev.poldy.lab"


def adb_path():
    candidate = os.environ.get("POLDY_ADB") or shutil.which("adb")
    if candidate:
        return candidate
    return str(Path.home() / "Library/Android/sdk/platform-tools/adb")


def run(args, timeout=12):
    result = subprocess.run(args, capture_output=True, text=True, timeout=timeout)
    if result.returncode:
        raise RuntimeError((result.stderr or result.stdout).strip())
    return result.stdout


def select_device(adb, requested):
    rows = []
    for line in run([adb, "devices"]).splitlines()[1:]:
        fields = line.split()
        if len(fields) >= 2:
            rows.append((fields[0], fields[1]))
    if requested:
        if (requested, "device") not in rows:
            raise RuntimeError("지정한 기기가 연결·승인되지 않았습니다.")
        return requested
    physical = [serial for serial, state in rows if state == "device" and not serial.startswith("emulator-")]
    if len(physical) != 1:
        raise RuntimeError("USB 디버깅을 승인한 실제 휴대전화 1대가 필요합니다. 여러 대라면 --serial을 지정하세요.")
    return physical[0]


def query(base, args):
    try:
        return {"ok": True, "text": run(base + ["shell"] + args)}
    except (RuntimeError, subprocess.TimeoutExpired) as exc:
        return {"ok": False, "error": str(exc)}


def inventory(base, directory):
    keys = ["ro.product.manufacturer", "ro.product.model", "ro.build.version.release",
            "ro.build.version.sdk", "ro.build.version.oneui", "ro.build.fingerprint"]
    result = {"captured_at": dt.datetime.now().astimezone().isoformat(), "properties": {}}
    for key in keys:
        result["properties"][key] = query(base, ["getprop", key])
    commands = {
        "device_state_help": ["cmd", "device_state", "help"],
        "supported_states": ["cmd", "device_state", "print-states"],
        "current_state": ["cmd", "device_state", "state"],
        "device_state_dump": ["dumpsys", "device_state"],
        "display_dump": ["dumpsys", "display"],
        "display_commands": ["cmd", "display", "help"],
        "concurrent_display_config": ["cmd", "overlay", "lookup", "android", "android:bool/config_supportsConcurrentInternalDisplays"],
    }
    for name, command in commands.items():
        result[name] = query(base, command)
    configs = ["/vendor/etc/devicestate/device_state_configuration.xml",
               "/vendor/etc/displayconfig/display_layout_configuration.xml"]
    result["oem_config"] = {p: query(base, ["cat", p]) for p in configs}
    (directory / "inventory.json").write_text(json.dumps(result, ensure_ascii=False, indent=2))
    print("기기 상태·디스플레이 구성을 저장했습니다:", directory / "inventory.json")
    print(result["supported_states"].get("text", result["supported_states"].get("error")))


def watch(base, directory, seconds):
    """Logcat is limited to our tag. Full dumpsys display stays in the local directory."""
    log_path = directory / "probe-logcat.txt"
    with log_path.open("w") as out, (directory / "system-timeline.jsonl").open("w") as timeline:
        process = subprocess.Popen(base + ["logcat", "-T", "1", "-v", "raw", "-s", "PoldyProbe:I", "PoldyFold:I", "*:S"],
                                   stdout=out, stderr=subprocess.DEVNULL)
        start = time.monotonic()
        try:
            while time.monotonic() - start < seconds:
                before = time.time_ns() // 1_000_000
                state = query(base, ["cmd", "device_state", "state"])
                display = query(base, ["dumpsys", "display"])
                timeline.write(json.dumps({"host_start_epoch_ms": before,
                    "host_end_epoch_ms": time.time_ns() // 1_000_000,
                    "device_state": state, "display": display}, ensure_ascii=False) + "\n")
                timeline.flush()
                if not state["ok"] and not display["ok"]:
                    raise RuntimeError("기기 연결이 끊겼거나 두 진단 명령이 모두 실패했습니다.")
                time.sleep(.3)
        finally:
            process.terminate()
            try:
                process.wait(timeout=3)
            except subprocess.TimeoutExpired:
                process.kill()
    print("시스템 타임라인과 Poldy 센서 로그 저장:", directory)


def pull_sessions(base, directory):
    text = run(base + ["shell", "run-as", PACKAGE, "ls", "files/sessions"])
    names = [n for n in text.splitlines() if n.endswith(".jsonl") and n[:-6].isdigit()]
    if not names:
        raise RuntimeError("측정 기록이 없습니다. Poldy Lab에서 측정을 먼저 시작하세요.")
    for name in sorted(names):
        data = run(base + ["exec-out", "run-as", PACKAGE, "cat", "files/sessions/" + name])
        (directory / name).write_text(data)
    print("기기에 저장된 측정 기록 가져옴:", len(names), "개")


def transition_rows(events):
    """Preserve direction and stale-angle evidence; never interpret ID 0 as a physical panel."""
    result = []
    previous = None
    for event in events:
        if "displays" not in event:
            continue
        geometry = tuple((d.get("id"), d.get("state"), d.get("logical_width"), d.get("logical_height"))
                         for d in event["displays"])
        if previous is not None and geometry != previous:
            result.append({"elapsed_ms": event.get("elapsed_ms"), "angle_deg": event.get("angle_deg"),
                "angle_age_ms": event.get("angle_age_ms"), "direction": event.get("direction"),
                "before": previous, "after": geometry, "event": event.get("event")})
        previous = geometry
    return result


def summarize(directory):
    reports = []
    for path in sorted(directory.glob("*.jsonl")):
        if path.name == "system-timeline.jsonl":
            continue
        events = []
        for line in path.read_text().splitlines():
            try:
                events.append(json.loads(line))
            except json.JSONDecodeError:
                pass
        reports.append({"file": path.name, "transitions": transition_rows(events)})
    report = {"interpretation": "논리 디스플레이 상태/크기 변화입니다. 물리 패널 점등은 dumpsys display 및 육안 관찰과 대조해야 합니다. angle_age_ms가 큰 값은 전환 순간의 정확한 각도를 뜻하지 않습니다.",
              "sessions": reports}
    (directory / "transitions.json").write_text(json.dumps(report, ensure_ascii=False, indent=2))
    print(json.dumps(report, ensure_ascii=False, indent=2))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("command", choices=["inventory", "watch", "pull", "summarize"])
    parser.add_argument("--serial")
    parser.add_argument("--directory", type=Path)
    parser.add_argument("--seconds", type=int, default=30)
    args = parser.parse_args()
    if not 1 <= args.seconds <= 300:
        parser.error("--seconds 범위는 1~300입니다.")
    if args.command == "summarize" and args.directory is None:
        parser.error("summarize에는 --directory가 필요합니다.")
    directory = args.directory or ROOT / "measurements" / dt.datetime.now().strftime("%Y%m%d-%H%M%S")
    if args.command == "summarize" and not directory.is_dir():
        parser.error("측정 디렉터리가 없습니다.")
    if args.command == "summarize":
        summarize(directory)
        return
    adb = adb_path()
    serial = select_device(adb, args.serial)
    directory.mkdir(parents=True, exist_ok=True)
    base = [adb, "-s", serial]
    if args.command == "inventory":
        inventory(base, directory)
    elif args.command == "watch":
        watch(base, directory, args.seconds)
    elif args.command == "pull":
        pull_sessions(base, directory)


if __name__ == "__main__":
    try:
        main()
    except (OSError, RuntimeError, subprocess.TimeoutExpired) as error:
        sys.exit(str(error))
