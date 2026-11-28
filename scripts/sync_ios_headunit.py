"""Package only the project's original, script-free head-unit assets for iOS."""
import argparse
from pathlib import Path
import shutil
import tempfile
from compile_car_ui import compile_ui

ROOT = Path(__file__).resolve().parents[1]
DESTINATION = ROOT / 'ios/Sources/SubaruBridgeCore/Resources/HeadUnit'


def generated(directory):
    compile_ui(ROOT / 'android/headunit-ui', directory, directory / 'home.html')
    shutil.copy2(ROOT / 'android/src/main/assets/subarubridge-lab/pattern.png', directory / 'pattern.png')
    # Generated text must be identical on Windows and macOS, including line endings.
    return {p.relative_to(directory).as_posix(): p.read_bytes() if p.suffix == '.png' else p.read_bytes().replace(b'\r\n', b'\n')
            for p in directory.rglob('*') if p.is_file()}


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--check', action='store_true')
    args = parser.parse_args()
    with tempfile.TemporaryDirectory() as temporary:
        expected = generated(Path(temporary))
        actual = {p.relative_to(DESTINATION).as_posix(): p.read_bytes() for p in DESTINATION.rglob('*') if p.is_file()}
        if args.check:
            if actual != expected:
                raise SystemExit('iOS head-unit resources differ; run python3 scripts/sync_ios_headunit.py')
            print('Original iOS head-unit assets: PASS')
        else:
            if set(actual) - set(expected):
                raise SystemExit('Unexpected files in the generated iOS resource folder; review before replacing')
            for name, data in expected.items():
                target = DESTINATION / name
                target.parent.mkdir(parents=True, exist_ok=True)
                target.write_bytes(data)
            print(f'Packaged {len(expected)} original head-unit resources')
