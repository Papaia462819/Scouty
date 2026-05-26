param(
    [string]$PackageName = "com.nego.scouty",
    [string]$TrailCode,
    [switch]$SkipBasePack
)

$ErrorActionPreference = "Stop"
$pushScript = Join-Path $PSScriptRoot "push_map_packs_to_device.ps1"
if (-not (Test-Path $pushScript)) {
    throw "Missing push script '$pushScript'."
}

& $pushScript -PackageName $PackageName -TrailCode $TrailCode -SkipBasePack:$SkipBasePack

Write-Host "Verifying installed map packs..."
adb shell ls -lh "/sdcard/Android/data/$PackageName/files/maps"
