#!/bin/bash
set -euo pipefail
cd "$(dirname "$0")/.."
mode=${1:-signed}
if [ "$mode" != signed ] && [ "$mode" != unsigned ]; then echo 'Choose signed or unsigned'; exit 2; fi
mkdir -p build/artifacts
swift scripts/render-icon.swift App/Assets.xcassets/AppIcon.appiconset/AppIcon.png
ARCHS=arm64 bash scripts/build-rust-core.sh iphoneos
if [ "$mode" = signed ]; then
  : "${DEVELOPMENT_TEAM:?Set the development team from your own Xcode account}"
  archive="$PWD/build/DeviceArchive-$(date -u +%Y%m%d-%H%M%S).xcarchive"
  xcodebuild -project SubaruBridge.xcodeproj -scheme SubaruBridge -configuration Release \
    -destination 'generic/platform=iOS' -archivePath "$archive" -jobs 2 \
    -allowProvisioningUpdates DEVELOPMENT_TEAM="$DEVELOPMENT_TEAM" CODE_SIGN_STYLE=Automatic archive
  export DEVELOPMENT_TEAM
  python3 - <<'PY'
import os,plistlib
from pathlib import Path
options={'method':'debugging','signingStyle':'automatic',
    'teamID':os.environ['DEVELOPMENT_TEAM'],'manageAppVersionAndBuildNumber':False}
Path('build/ExportOptions.plist').write_bytes(plistlib.dumps(options))
PY
  xcodebuild -exportArchive -archivePath "$archive" -exportOptionsPlist build/ExportOptions.plist \
    -exportPath build/artifacts/signed -allowProvisioningUpdates
  echo 'Export completed. Verify the provisioning profile includes the intended iPhone.'
else
  xcodebuild -project SubaruBridge.xcodeproj -scheme SubaruBridge -configuration Release \
    -destination 'generic/platform=iOS' -sdk iphoneos -derivedDataPath build/UnsignedDevice -jobs 2 \
    CODE_SIGNING_ALLOWED=NO CODE_SIGNING_REQUIRED=NO build
  app="$PWD/build/UnsignedDevice/Build/Products/Release-iphoneos/SubaruBridge.app"
  version=$(/usr/libexec/PlistBuddy -c 'Print :CFBundleShortVersionString' "$app/Info.plist")
  xcrun vtool -show-build "$app/SubaruBridge" | grep -q 'platform IOS'
  stage=$(mktemp -d "$PWD/build/ipa-stage.XXXXXX")
  mkdir "$stage/Payload"
  ditto "$app" "$stage/Payload/SubaruBridge.app"
  output="$PWD/build/artifacts/SubaruBridge-$version-requires-signing.ipa"
  (cd "$stage" && /usr/bin/zip -qry "$output" Payload)
  shasum -a 256 "$output"
  echo 'This is a device IPA requiring signing/provisioning. It is not directly installable.'
fi
