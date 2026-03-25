param(
    [string]$PackageName = "com.scouty.app",
    [string]$ActivityName = ".MainActivity",
    [int]$WaitSeconds = 8
)

$ErrorActionPreference = "Stop"

$repoRoot = Split-Path -Parent $PSScriptRoot
$syncScript = Join-Path $repoRoot "tools\sync_map_packs.ps1"

if (-not (Test-Path $syncScript)) {
    throw "Missing sync script '$syncScript'."
}

& $syncScript -PackageName $PackageName

adb logcat -c | Out-Null
adb shell am start -n "$PackageName/$ActivityName" | Out-Null
Start-Sleep -Seconds $WaitSeconds

Write-Host "Installed map files:"
adb shell run-as $PackageName ls -lh files/maps

Write-Host "`nRelevant logcat lines:"
adb logcat -d | Select-String -Pattern "ScoutyMap|ScoutyMapPacks|PMTiles|AndroidRuntime|FATAL EXCEPTION"
