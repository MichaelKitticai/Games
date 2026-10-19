#!/usr/bin/env bash
# Builds x264 as a static library for each Android ABI the app ships.
# Output: app/src/main/cpp/x264/<abi>/{include,lib}. Run before Gradle (CI does this, cached).
#
# Needs ANDROID_NDK_HOME (or ANDROID_HOME with the NDK version below installed).
set -euo pipefail

X264_COMMIT=b35605ace3ddf7c1a5d67a2eb553f034aef41d55 # stable branch
NDK_VERSION=27.0.12077973
API=29

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT="$ROOT/app/src/main/cpp/x264"
# Outside the project: x264 can't build from a path with spaces.
SRC="${X264_SRC_DIR:-$HOME/.cache/status-compressor/x264-src}"
NDK="${ANDROID_NDK_HOME:-${ANDROID_HOME:-$HOME/Library/Android/sdk}/ndk/$NDK_VERSION}"

case "$(uname -s)" in
  Darwin) HOST=darwin-x86_64 ;; # The NDK ships x86_64 binaries that run on Apple Silicon too.
  *) HOST=linux-x86_64 ;;
esac
TC="$NDK/toolchains/llvm/prebuilt/$HOST/bin"

if [ -f "$OUT/.commit" ] && [ "$(cat "$OUT/.commit")" = "$X264_COMMIT" ]; then
  echo "x264 $X264_COMMIT already built"
  exit 0
fi

if [ ! -x "$TC/aarch64-linux-android$API-clang" ]; then
  echo "NDK $NDK_VERSION not found at $NDK" >&2
  exit 1
fi

if [ ! -d "$SRC/.git" ]; then
  git clone --quiet https://code.videolan.org/videolan/x264.git "$SRC"
fi
git -C "$SRC" fetch --quiet origin "$X264_COMMIT"
git -C "$SRC" checkout --quiet "$X264_COMMIT"

# x264's configure and make can't cope with spaces in paths, so stage installs in a temp dir.
STAGE="$(mktemp -d)"
trap 'rm -rf "$STAGE"' EXIT

build() {
  local abi=$1 host=$2 cc=$3
  shift 3
  echo "Building x264 for $abi"
  (
    cd "$SRC"
    make distclean >/dev/null 2>&1 || true
    CC="$TC/$cc" ./configure \
      --host="$host" \
      --cross-prefix="$TC/llvm-" \
      --sysroot="$NDK/toolchains/llvm/prebuilt/$HOST/sysroot" \
      --prefix="$STAGE/$abi" \
      --enable-static --enable-pic --disable-cli --disable-opencl \
      --bit-depth=8 --chroma-format=420 \
      "$@" >/dev/null
    make -j"$(getconf _NPROCESSORS_ONLN)" >/dev/null
    make install-lib-static >/dev/null
  )
  mkdir -p "$OUT"
  cp -R "$STAGE/$abi" "$OUT/$abi"
}

rm -rf "$OUT"
build arm64-v8a aarch64-linux-android "aarch64-linux-android$API-clang"
# 32-bit ARM assembly doesn't build with the NDK's clang; plain C is fine for the few old phones.
build armeabi-v7a arm-linux-androideabi "armv7a-linux-androideabi$API-clang" --disable-asm
echo "$X264_COMMIT" > "$OUT/.commit"
echo "x264 built into $OUT"
