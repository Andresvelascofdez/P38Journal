$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
Push-Location $projectRoot
try {
    if (-not (Get-Command java -ErrorAction SilentlyContinue)) { throw 'Se necesita JDK 17 o 21 en PATH.' }
    & .\gradlew.bat testDebugUnitTest assembleDebug assembleRelease assembleDebugAndroidTest lintDebug --console=plain --no-parallel --max-workers=1
    if ($LASTEXITCODE -ne 0) { throw 'Gradle falló. Revisar su salida.' }
    New-Item -ItemType Directory -Force dist | Out-Null
    Copy-Item app\build\outputs\apk\debug\app-debug.apk dist\p38-journal-4.0.0-debug.apk
    Copy-Item app\build\outputs\apk\release\app-release.apk dist\p38-journal-4.0.0-release.apk
    Get-FileHash dist\*.apk -Algorithm SHA256 | ForEach-Object { "$($_.Hash.ToLower())  $([IO.Path]::GetFileName($_.Path))" } | Set-Content -Encoding ascii dist\SHA256SUMS.txt
} finally { Pop-Location }
