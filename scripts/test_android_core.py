"""Build and run pure Rust regression binaries on one explicit Android device.

No root, Bluetooth connection or vehicle is used. Keep the output directory private.
"""
import argparse
import json
from pathlib import Path
import re
import shutil
import subprocess
import sys
import uuid

ROOT = Path(__file__).resolve().parents[1]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--sdk', type=Path, required=True)
    parser.add_argument('--serial', required=True)
    parser.add_argument('--abi', choices=['arm64-v8a', 'x86_64'], required=True)
    parser.add_argument('--adb', default=shutil.which('adb'))
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    if not args.adb:
        parser.error('Set --adb or put Android platform-tools on PATH')
    args.output.mkdir(parents=True, exist_ok=False)
    adb = [args.adb, '-s', args.serial]

    def call(command):
        return subprocess.run(command, check=True, capture_output=True, text=True, timeout=60)

    actual = call(adb + ['shell', 'getprop', 'ro.product.cpu.abi']).stdout.strip()
    if actual != args.abi:
        raise RuntimeError('Selected device ABI does not match --abi')
    built = subprocess.run([sys.executable, str(ROOT / 'scripts/build_android_rust.py'),
                            '--sdk', str(args.sdk), '--tests', '--abi', args.abi],
                           capture_output=True, text=True, timeout=600)
    (args.output / 'build.txt').write_text(built.stdout + built.stderr, encoding='utf-8')
    built.check_returncode()
    binaries = []
    expected = (ROOT / 'rust/target' / ('aarch64-linux-android' if args.abi == 'arm64-v8a' else 'x86_64-linux-android')).resolve()
    for line in built.stdout.splitlines():
        candidate = Path(line.strip())
        if candidate.is_file() and candidate.resolve().is_relative_to(expected):
            if not re.fullmatch(r'[A-Za-z0-9_-]+', candidate.name):
                raise RuntimeError('Unexpected native test executable name')
            binaries.append(candidate)
    if not binaries:
        raise RuntimeError('Cargo returned no Android test executables')
    remote = '/data/local/tmp/subarubridge-core-qa-' + uuid.uuid4().hex
    call(adb + ['shell', 'mkdir', remote])
    transferred = []
    results = []
    try:
        for binary in binaries:
            target = remote + '/' + binary.name
            transferred.append(target)
            call(adb + ['push', str(binary), target])
            call(adb + ['shell', 'chmod', '700', target])
            tested = subprocess.run(adb + ['shell', target, '--test-threads=1'], capture_output=True, text=True, timeout=120)
            (args.output / (binary.name + '.txt')).write_text(tested.stdout + tested.stderr, encoding='utf-8')
            tested.check_returncode()
            match = re.search(r'test result: ok\. (\d+) passed; 0 failed;', tested.stdout)
            if not match:
                raise RuntimeError('Native test did not report a successful result')
            results.append({'target': binary.name, 'passed': int(match.group(1))})
    finally:
        # Remove only the exact files we created, never a computed recursive tree.
        for target in transferred:
            try:
                subprocess.run(adb + ['shell', 'rm', '-f', '--', target], capture_output=True, timeout=10)
            except subprocess.TimeoutExpired:
                pass  # A disconnected phone retains only these harmless test files.
        try:
            subprocess.run(adb + ['shell', 'rmdir', remote], capture_output=True, timeout=10)
        except subprocess.TimeoutExpired:
            pass
    result = {'abi': args.abi, 'passed': sum(item['passed'] for item in results), 'targets': results}
    (args.output / 'results.json').write_text(json.dumps(result, indent=2) + '\n', encoding='utf-8')
    print(json.dumps(result))


if __name__ == '__main__':
    main()
