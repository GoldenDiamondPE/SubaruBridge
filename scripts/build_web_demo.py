"""Keep the public browser demo tied to the original head-unit UI source."""
import argparse
import json
from pathlib import Path
from compile_car_ui import validate

ROOT = Path(__file__).resolve().parents[1]


def document():
    source = ROOT / 'android/headunit-ui'
    config = json.loads((source / 'dashboard.json').read_text(encoding='utf-8'))
    validate(config, source)
    return {
        'schemaVersion': 1,
        'source': 'android/headunit-ui',
        'config': config,
        'css': (source / 'dashboard.css').read_text(encoding='utf-8'),
        'templates': {p.stem: p.read_text(encoding='utf-8') for p in sorted((source / 'templates').glob('*.html'))},
        'icons': {p.stem: p.read_text(encoding='utf-8') for p in sorted((source / 'icons').glob('*.svg'))},
    }


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--check', action='store_true')
    args = parser.parse_args()
    target = ROOT / 'docs/assets/demo-data.json'
    expected = json.dumps(document(), ensure_ascii=False, indent=2) + '\n'
    if args.check:
        if not target.exists() or target.read_text(encoding='utf-8') != expected:
            raise SystemExit('Demo UI assets differ. Run python3 scripts/build_web_demo.py and review the result.')
        print('Demo source synchronization: PASS')
    else:
        target.write_text(expected, encoding='utf-8', newline='\n')
        print('Updated docs/assets/demo-data.json from original head-unit source')
