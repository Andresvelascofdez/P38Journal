#!/usr/bin/env sh
set -eu
cd "$(dirname "$0")/.."
command -v java >/dev/null || { echo 'JDK 17 o 21 necesario'; exit 1; }
journal_sdk="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
[ -n "$journal_sdk" ] && [ -f "$journal_sdk/platforms/android-36/android.jar" ] || { echo 'Definir ANDROID_HOME con SDK oficial Android 36'; exit 1; }
journal_sdk_escaped=$(printf '%s' "$journal_sdk" | sed 's/:/\\:/g')
printf 'sdk.dir=%s\n' "$journal_sdk_escaped" > local.properties
echo 'Entorno local configurado. Ejecutar scripts/build.sh; release requiere clave estable.'
