#!/usr/bin/env bash
set -euo pipefail
PROJECT_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
if [[ -z "${JAVA_HOME:-}" && -d "/Applications/Android Studio.app/Contents/jbr/Contents/Home" ]]; then
  export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
fi
if [[ -z "${ANDROID_HOME:-}" && -d "$HOME/Library/Android/sdk" ]]; then
  export ANDROID_HOME="$HOME/Library/Android/sdk"
fi
cd "$PROJECT_ROOT/apps/android"
./gradlew testDebugUnitTest assembleDebug "$@"
mkdir -p "$PROJECT_ROOT/artifacts"
cp app/build/outputs/apk/debug/app-debug.apk "$PROJECT_ROOT/artifacts/suraksha-saathi-0.3.1-debug.apk"
shasum -a 256 "$PROJECT_ROOT/artifacts/suraksha-saathi-0.3.1-debug.apk"
