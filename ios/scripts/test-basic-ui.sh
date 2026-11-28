#!/bin/bash
set -euo pipefail
cd "$(dirname "$0")/.."
mkdir -p build
swift scripts/render-icon.swift App/Assets.xcassets/AppIcon.appiconset/AppIcon.png
ARCHS="$(uname -m)" bash scripts/build-rust-core.sh iphonesimulator
xcrun simctl list runtimes -j > build/runtimes.json
if [ ! -f build/unified-ui-device ]; then
  runtime=$(python3 - <<'PY'
import json
from pathlib import Path
runtimes=[r for r in json.loads(Path('build/runtimes.json').read_text())['runtimes']
          if r.get('isAvailable') and r['identifier'].startswith('com.apple.CoreSimulator.SimRuntime.iOS-')
          and len(r['version'].split('.')) <= 3 and 'iPhone' not in r['identifier']]
assert runtimes, 'No installed iOS simulator runtime'
print(max(runtimes,key=lambda r:tuple(int(x) for x in r['version'].split('.')))['identifier'])
PY
)
  xcrun simctl create 'SubaruBridge Unified QA' com.apple.CoreSimulator.SimDeviceType.iPhone-15-Plus "$runtime" > build/unified-ui-device
fi
device=$(cat build/unified-ui-device)
xcrun simctl boot "$device" || true
xcrun simctl bootstatus "$device" -b
python3 - "$device" <<'PY'
import json,subprocess,sys
devices=json.loads(subprocess.check_output(['xcrun','simctl','list','devices','-j']))
device=next(d for group in devices['devices'].values() for d in group if d['udid']==sys.argv[1])
assert device['name']=='SubaruBridge Unified QA', 'Refusing to reset an unrelated simulator app'
PY
# This is our dedicated synthetic fixture, never a physical phone or another simulator.
xcrun simctl uninstall "$device" org.subarubridge.iosresearch || true
result="build/BasicUI-$(date -u +%Y%m%d-%H%M%S).xcresult"
xcodebuild -project SubaruBridge.xcodeproj -scheme SubaruBridge -configuration Debug \
  -destination "platform=iOS Simulator,id=$device" -derivedDataPath build/DerivedData \
  -resultBundlePath "$result" -parallel-testing-enabled NO -jobs 2 ARCHS="$(uname -m)" CODE_SIGNING_ALLOWED=NO test
xcrun simctl launch --terminate-running-process "$device" org.subarubridge.iosresearch
sleep 5
xcrun simctl io "$device" screenshot build/ios-basic-home.png
echo "UI tests passed on the dedicated iPhone 15 Plus simulator. Device remains available for inspection."
