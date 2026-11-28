#!/bin/bash
set -euo pipefail
cd "$(dirname "$0")/.."
platform=${1:-host}
toolchain=${SUBARUBRIDGE_RUST_TOOLCHAIN:-1.90.0}
export CARGO_TARGET_DIR="${CARGO_TARGET_DIR:-$PWD/build/rust-target}"
if [ -n "${CARGO_HOME:-}" ]; then export PATH="$CARGO_HOME/bin:$PATH"; else export PATH="$HOME/.cargo/bin:$PATH"; fi
destination="$PWD/build/rust-core/$platform"
mkdir -p "$destination"
case "$platform" in
  host)
    cargo +"$toolchain" build --manifest-path ../rust/Cargo.toml --locked --release
    cp "$CARGO_TARGET_DIR/release/libsubarubridge_core.a" "$destination/libsubarubridge_core.a"
    ;;
  iphoneos|iphonesimulator)
    objects=()
    for arch in ${ARCHS:-arm64}; do
      case "$platform:$arch" in
        iphoneos:arm64) target=aarch64-apple-ios ;;
        iphonesimulator:arm64) target=aarch64-apple-ios-sim ;;
        iphonesimulator:x86_64) target=x86_64-apple-ios ;;
        *) echo "Unsupported Apple target $platform:$arch" >&2; exit 2 ;;
      esac
      rustup target add --toolchain "$toolchain" "$target"
      cargo +"$toolchain" build --manifest-path ../rust/Cargo.toml --locked --release --target "$target"
      objects+=("$CARGO_TARGET_DIR/$target/release/libsubarubridge_core.a")
    done
    if [ "${#objects[@]}" -eq 1 ]; then cp "${objects[0]}" "$destination/libsubarubridge_core.a"
    else xcrun lipo -create "${objects[@]}" -output "$destination/libsubarubridge_core.a"; fi
    ;;
  *) echo "Expected host, iphoneos or iphonesimulator" >&2; exit 2 ;;
esac
echo "Original Rust core ready for $platform"
