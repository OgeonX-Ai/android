#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
BACKEND_DIR="$ROOT_DIR/backend"

python -m compileall "$BACKEND_DIR"

if ! command -v gradle >/dev/null 2>&1; then
  echo "⚠️ Gradle is not installed; skipping Android unit tests." >&2
  exit 0
fi

SDK_DIR=""
if [ -f "$ROOT_DIR/local.properties" ]; then
  SDK_DIR=$(grep -E '^sdk.dir=' "$ROOT_DIR/local.properties" | head -n1 | cut -d'=' -f2-)
fi

if [ -z "$SDK_DIR" ]; then
  SDK_DIR="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
fi

if [ -z "$SDK_DIR" ] || [ ! -d "$SDK_DIR" ]; then
  echo "⚠️ Android SDK not configured; attempting bootstrap..." >&2
  if "$ROOT_DIR/scripts/bootstrap_android_sdk.sh"; then
    SDK_DIR=$(grep -E '^sdk.dir=' "$ROOT_DIR/local.properties" | head -n1 | cut -d'=' -f2-)
  else
    echo "⚠️ Bootstrap failed; skipping Android unit tests." >&2
    echo "   Set sdk.dir in local.properties or ANDROID_HOME/ANDROID_SDK_ROOT, or run scripts/bootstrap_android_sdk.sh with CMDLINE_ZIP_PATH pointing to a local zip." >&2
    exit 0
  fi
fi

gradle test --stacktrace
