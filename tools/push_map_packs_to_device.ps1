param(
    [string]$PackageName = "com.scouty.app",
    [string]$PacksDir = (Join-Path $PSScriptRoot "generated-map-packs"),
    [string]$Serial,
    [switch]$SkipDemoPack
)

$ErrorActionPreference = "Stop"

function Get-AdbCommand {
    $adb = Get-Command adb -ErrorAction SilentlyContinue
    if ($adb) {
        return $adb.Source
    }

    if ($env:ANDROID_HOME) {
        $candidate = Join-Path $env:ANDROID_HOME "platform-tools\\adb.exe"
        if (Test-Path $candidate) {
            return $candidate
        }
    }

    throw "adb was not found on PATH and ANDROID_HOME is not set."
}

function Invoke-Adb {
    param([string[]]$CommandArgs)

    $adb = Get-AdbCommand
    if (!$CommandArgs -or $CommandArgs.Count -eq 0) {
        throw "Invoke-Adb called without arguments."
    }
    $fullArgs = @()
    if ($Serial) {
        $fullArgs += "-s", $Serial
    }
    $fullArgs += $CommandArgs

    Write-Host "> adb $($fullArgs -join ' ')"
    & $adb @fullArgs
    if ($LASTEXITCODE -ne 0) {
        throw "adb command failed: $($fullArgs -join ' ')"
    }
}

function Push-Pack {
    param(
        [string]$LocalPath,
        [string]$PackFileName
    )

    $tempRemote = "/data/local/tmp/$PackFileName"
    Invoke-Adb -CommandArgs @("push", $LocalPath, $tempRemote)
    Invoke-Adb -CommandArgs @("shell", "run-as", $PackageName, "mkdir", "-p", "files/maps")
    Invoke-Adb -CommandArgs @("shell", "run-as", $PackageName, "cp", $tempRemote, "files/maps/$PackFileName")
    Invoke-Adb -CommandArgs @("shell", "rm", "-f", $tempRemote)
}

$basePack = Join-Path $PacksDir "romania-base.pmtiles"
$demoPack = Join-Path $PacksDir "bucegi-high.pmtiles"

if (!(Test-Path $basePack)) {
    throw "Missing required pack: $basePack"
}

Invoke-Adb -CommandArgs @("get-state")
Invoke-Adb -CommandArgs @("shell", "pm", "list", "packages", $PackageName)
Push-Pack -LocalPath $basePack -PackFileName "romania-base.pmtiles"

if (-not $SkipDemoPack -and (Test-Path $demoPack)) {
    Push-Pack -LocalPath $demoPack -PackFileName "bucegi-high.pmtiles"
} elseif (-not $SkipDemoPack) {
    Write-Warning "Optional pack not found: $demoPack"
}

Write-Host "Map packs pushed to app-specific storage for $PackageName."
