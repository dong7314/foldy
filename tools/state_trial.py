#!/usr/bin/env python3
"""Temporarily request a verified device state, then reset it on the phone itself."""
import argparse
import json
from pathlib import Path
import re
import subprocess
import sys
import time
from device_probe import adb_path, run, select_device

# The timer and cleanup execute on Android, so normal USB disconnection does not
# rely on a later Mac command to reset. We also reset again in the host finally.
DEVICE_SCRIPT = '''trap 'cmd device_state state reset' EXIT
trap 'exit 130' HUP INT TERM
cmd device_state state "$1" || exit 1
sleep "$2"
'''


def physical_displays(dump):
    result = []
    for line in dump.splitlines():
        if 'DisplayDeviceInfo{' not in line:
            continue
        unique = re.search(r'uniqueId="([^"]+)"', line)
        size = re.search(r'", (\d+) x (\d+)', line)
        state = re.search(r', state (\w+), committedState (\w+)', line)
        if unique and size and state:
            result.append({'unique_id': unique[1], 'width': int(size[1]), 'height': int(size[2]),
                           'state': state[1], 'committed_state': state[2]})
    return result


def validate_trial(supported, dump, state, seconds):
    identifiers = {int(i) for i in supported.strip().split(',') if i.strip().isdigit()}
    if state not in identifiers:
        raise RuntimeError('기기에서 확인된 지원 상태 ID가 아닙니다.')
    if not 1 <= seconds <= 20:
        raise RuntimeError('임시 실험은 1~20초로 제한합니다.')
    if 'mOverrideState=Optional.empty' not in dump or 'Override Request active: false' not in dump:
        raise RuntimeError('기존 상태 요청이 있거나 요청 유무를 판별하지 못했습니다. 덮어쓰지 않습니다.')


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--serial')
    p.add_argument('--state', type=int, required=True)
    p.add_argument('--seconds', type=int, default=15)
    p.add_argument('--directory', type=Path, required=True)
    a = p.parse_args()
    adb = adb_path()
    base = [adb, '-s', select_device(adb, a.serial)]
    dump = run(base + ['shell', 'dumpsys', 'device_state'])
    states = run(base + ['shell', 'cmd', 'device_state', 'print-states-simple'])
    validate_trial(states, dump, a.state, a.seconds)
    a.directory.mkdir(parents=True, exist_ok=True)
    (a.directory / 'state-before.txt').write_text(dump)
    process = None
    try:
        with (a.directory / 'request.txt').open('w') as output:
            process = subprocess.Popen(base + ['shell', 'sh', '-s', '--', str(a.state), str(a.seconds)],
                                       stdin=subprocess.PIPE, stdout=output, stderr=subprocess.STDOUT,
                                       text=True)
            process.stdin.write(DEVICE_SCRIPT)
            process.stdin.close()
            print(f'상태 {a.state}를 {a.seconds}초간 요청합니다. 기기에서 자동 원복합니다.', flush=True)
            start = time.monotonic()
            with (a.directory / 'trial.jsonl').open('w') as timeline:
                while process.poll() is None and time.monotonic() - start < a.seconds + 5:
                    state = run(base + ['shell', 'cmd', 'device_state', 'state'])
                    display = run(base + ['shell', 'dumpsys', 'display'])
                    row = {'host_epoch_ms': time.time_ns() // 1_000_000, 'state': state,
                           'physical_displays': physical_displays(display)}
                    timeline.write(json.dumps(row, ensure_ascii=False) + '\n')
                    timeline.flush()
                    time.sleep(.4)
            process.wait(timeout=3)
    finally:
        try:
            run(base + ['shell', 'cmd', 'device_state', 'state', 'reset'])
            after = run(base + ['shell', 'dumpsys', 'device_state'])
            (a.directory / 'state-after.txt').write_text(after)
            if 'mOverrideState=Optional.empty' not in after:
                raise RuntimeError('상태 요청 해제가 확인되지 않았습니다. 즉시 상태를 확인하세요.')
            print('상태 요청 해제 확인. 실제 접힘 상태에 따른 기본 동작으로 돌아왔습니다.')
        finally:
            if process is not None and process.poll() is None:
                process.terminate()
                process.wait(timeout=3)


if __name__ == '__main__':
    try:
        main()
    except (OSError, RuntimeError, subprocess.TimeoutExpired) as exc:
        sys.exit(str(exc))
