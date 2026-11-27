#!/bin/bash
set -euo pipefail
cd "$(dirname "$0")/.."
mkdir -p build
xcrun simctl list devices available -j > build/simulators.json
device=$(python3 - <<'PY'
import json
from pathlib import Path
data=json.loads(Path('build/simulators.json').read_text())
for runtime,devices in data['devices'].items():
    if 'iOS-' not in runtime:
        continue
    for device in devices:
        if device.get('isAvailable') and device['name'].startswith('iPhone'):
            print(device['udid']);raise SystemExit(0)
raise SystemExit('No available iPhone simulator runtime')
PY
)
xcrun simctl boot "$device" || true
xcrun simctl bootstatus "$device" -b
xcrun simctl install "$device" build/DerivedData/Build/Products/Debug-iphonesimulator/SubaruBridge.app
xcrun simctl launch --terminate-running-process "$device" org.subarubridge.iosresearch
sleep 8
xcrun simctl spawn "$device" launchctl list > build/app-processes.txt
python3 - <<'PY'
from pathlib import Path
rows=[row.split() for row in Path('build/app-processes.txt').read_text().splitlines() if 'org.subarubridge.iosresearch' in row]
assert any(row[0].isdigit() and int(row[0])>0 for row in rows), 'App did not remain running after launch'
print('PASS: iOS simulator app remained running after cold launch')
PY
xcrun simctl io "$device" screenshot build/ios-home.png
xcrun simctl terminate "$device" org.subarubridge.iosresearch
xcrun simctl shutdown "$device"
