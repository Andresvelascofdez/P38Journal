#!/usr/bin/env sh
set -eu
cd "$(dirname "$0")/.."
sh ./gradlew testDebugUnitTest lintDebug assembleDebug assembleRelease --console=plain
mkdir -p dist
cp app/build/outputs/apk/debug/app-debug.apk dist/p38-journal-2.0.0-debug.apk
cp app/build/outputs/apk/release/app-release.apk dist/p38-journal-2.0.0-release.apk
(cd dist && sha256sum ./*.apk > SHA256SUMS.txt)
