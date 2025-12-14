#!/usr/bin/env bash
set -euo pipefail

# Simple helper to install a minimal Android SDK for local unit tests.
# It downloads the commandline tools, installs platform-tools, and the
# Android 34 platform/build-tools. The install directory defaults to
# $HOME/android-sdk unless ANDROID_SDK_ROOT is set.

SDK_ROOT=${ANDROID_SDK_ROOT:-"$HOME/android-sdk"}
CMDLINE_VERSION=11076708
CMDLINE_ZIP="commandlinetools-linux-${CMDLINE_VERSION}_latest.zip"
CMDLINE_URL="https://dl.google.com/android/repository/${CMDLINE_ZIP}"
# GitHub mirror to fall back when direct downloads are blocked (e.g., 403 proxy)
CMDLINE_MIRROR_URL="https://github.com/TrevorMare/AndroidSDKCommandLineTools/raw/main/${CMDLINE_ZIP}"
ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"

mkdir -p "$SDK_ROOT"
cd "$SDK_ROOT"

if [ ! -f "$CMDLINE_ZIP" ]; then
  echo "Downloading Android commandline tools..." >&2
  if ! curl -L "$CMDLINE_URL" -o "$CMDLINE_ZIP"; then
    echo "Primary download failed; trying GitHub mirror..." >&2
    curl -L "$CMDLINE_MIRROR_URL" -o "$CMDLINE_ZIP" || true
  fi
else
  echo "Reusing existing $CMDLINE_ZIP" >&2
fi

if [ ! -f "$CMDLINE_ZIP" ]; then
  echo "❌ Unable to download commandline tools. Manually download ${CMDLINE_ZIP} and place it in $SDK_ROOT then re-run." >&2
  exit 1
fi

if [ ! -d "cmdline-tools" ]; then
  mkdir -p cmdline-tools/latest
  unzip -qo "$CMDLINE_ZIP" -d cmdline-tools/latest
fi

export ANDROID_SDK_ROOT="$SDK_ROOT"
export ANDROID_HOME="$SDK_ROOT"
export PATH="$SDK_ROOT/cmdline-tools/latest/bin:$SDK_ROOT/platform-tools:$PATH"

echo "Accepting licenses and installing platform-tools, platform 34, build-tools 34.0.0..." >&2
yes | sdkmanager --licenses >/dev/null
sdkmanager "platform-tools" "platforms;android-34" "build-tools;34.0.0" >/dev/null

if [ -d "$ROOT_DIR" ]; then
  printf 'sdk.dir=%s\n' "$SDK_ROOT" > "$ROOT_DIR/local.properties"
fi

echo "SDK installed at $SDK_ROOT"
