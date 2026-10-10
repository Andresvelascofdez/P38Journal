$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
if (-not (Get-Command java -ErrorAction SilentlyContinue)) { throw 'JDK 17 o 21 necesario. No se ha modificado la configuración global.' }
$journalSdk = $env:ANDROID_HOME
if (-not $journalSdk) { $journalSdk = Join-Path $env:LOCALAPPDATA 'Android\Sdk' }
if (-not (Test-Path -LiteralPath (Join-Path $journalSdk 'platforms\android-36\android.jar'))) { throw "Instalar plataforma Android 36 en el SDK elegido: $journalSdk. Usar SDK Manager oficial." }
$sdkValue = $journalSdk.Replace('\','/').Replace(':','\:')
[IO.File]::WriteAllText((Join-Path $projectRoot 'local.properties'), "sdk.dir=$sdkValue`n", [Text.UTF8Encoding]::new($false))
Write-Output 'Entorno local configurado. Ejecutar scripts/build.ps1; release requiere la clave privada estable.'
