#!/bin/bash
set -euo pipefail
cd "$(dirname "$0")/.."
if [ "$(uname -s)" != Darwin ]; then
  echo "This build check must run on the Mac. Windows cannot validate the Apple SDK build."
  exit 2
fi
mkdir -p build
report="build/mac-validation-$(date -u +%Y%m%d-%H%M%S).log"
exec > >(tee "$report") 2>&1
echo "SubaruBridge iOS validation"
date -u
sw_vers
uname -m
xcode-select -p
xcodebuild -version
swift --version
swift scripts/render-icon.swift App/Assets.xcassets/AppIcon.appiconset/AppIcon.png
plutil -lint App/Info.plist SubaruBridge.xcodeproj/project.pbxproj
swift test --scratch-path build/package-tests
xcodebuild -project SubaruBridge.xcodeproj -scheme SubaruBridge -configuration Debug \
  -sdk iphonesimulator -destination 'generic/platform=iOS Simulator' \
  -derivedDataPath build/DerivedData CODE_SIGNING_ALLOWED=NO build
echo "Core tests and unsigned simulator build completed."
echo "This does not establish physical iPhone, Bluetooth or Subaru compatibility."
