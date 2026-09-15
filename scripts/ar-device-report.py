#!/usr/bin/env python3
"""Read-only AR diagnostic snapshot. Never installs, launches, records video or declares AR passed."""
import argparse
import datetime
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--serial', help='ADB serial when more than one device is attached')
    parser.add_argument('--allow-emulator', action='store_true', help='Label emulator evidence explicitly; default is physical phones only')
    parser.add_argument('--output', type=Path, help='Write local JSON instead of standard output')
    args = parser.parse_args()
    sdk = Path(os.environ.get('ANDROID_HOME', str(Path.home() / 'Library/Android/sdk')))
    adb = shutil.which('adb') or str(sdk / 'platform-tools/adb')
    if not Path(adb).is_file():
        parser.error('ADB not found. Set ANDROID_HOME or add platform-tools to PATH.')

    def command(*parts):
        result = subprocess.run([adb, *parts], capture_output=True, text=True, timeout=15)
        if result.returncode:
            raise RuntimeError(result.stderr.strip() or result.stdout.strip() or 'ADB command failed')
        return result.stdout.strip()

    devices = [line.split()[0] for line in command('devices').splitlines()[1:]
               if len(line.split()) >= 2 and line.split()[1] == 'device']
    candidates = [d for d in devices if args.allow_emulator or not d.startswith('emulator-')]
    if args.serial:
        candidates = [d for d in candidates if d == args.serial]
    if len(candidates) != 1:
        parser.error('Connect and unlock one USB-debugging-authorized phone, or select --serial. Emulator testing requires --allow-emulator.')
    serial = candidates[0]

    def shell(*parts):
        return command('-s', serial, 'shell', *parts)

    def package(name):
        output = shell('dumpsys', 'package', name)
        def value(pattern):
            match = re.search(pattern, output)
            return match.group(1) if match else None
        return {'versionName': value(r'versionName=([^\s]+)'),
                'versionCode': value(r'versionCode=(\d+)')}

    emulator = shell('getprop', 'ro.kernel.qemu') == '1'
    if emulator and not args.allow_emulator:
        parser.error('Selected device is an emulator; use --allow-emulator for labelled diagnostic evidence.')
    thermal = shell('dumpsys', 'thermalservice')
    match = re.search(r'Thermal Status:\s*(\d+)', thermal)
    logs = shell('logcat', '-d', '-t', '300', '-v', 'brief', 'RoomAR:I', '*:S')
    report = {
        'capturedAtUtc': datetime.datetime.now(datetime.timezone.utc).isoformat(),
        'scope': 'Diagnostic snapshot only; not proof of placement, completion or physical competence.',
        'deviceKind': 'emulator' if emulator else 'physical',
        'model': shell('getprop', 'ro.product.model'),
        'androidRelease': shell('getprop', 'ro.build.version.release'),
        'androidApi': shell('getprop', 'ro.build.version.sdk'),
        'application': package('com.narvyn.suraksha'),
        'arServices': package('com.google.ar.core'),
        'thermalStatus': int(match.group(1)) if match else None,
        'recentRoomArLogs': logs.splitlines(),
        'logLimit': 'Recent buffered RoomAR messages may include earlier sessions; manually correlate session and APK version.',
        'privacy': 'No screenshots, camera recordings, contacts, account data or full system log were collected.'
    }
    data = json.dumps(report, ensure_ascii=False, indent=2) + '\n'
    if args.output:
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(data)
        print(args.output.resolve())
    else:
        print(data, end='')


if __name__ == '__main__':
    try:
        main()
    except (subprocess.TimeoutExpired, RuntimeError) as error:
        sys.exit(str(error))
