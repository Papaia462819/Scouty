param(
    [string]$PackageName = "com.scouty.app",
    [switch]$SkipDemoPack
)

$ErrorActionPreference = "Stop"
$pushScript = Join-Path $PSScriptRoot "push_map_packs_to_device.ps1"
if (-not (Test-Path $pushScript)) {
    throw "Missing push script '$pushScript'."
}

& $pushScript -PackageName $PackageName -SkipDemoPack:$SkipDemoPack

Write-Host "Verifying installed map packs..."
adb shell run-as $PackageName ls -lh files/maps
