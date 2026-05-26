param(
    [string]$PackageName = "com.nego.scouty",
    [string]$PacksDir = (Join-Path $PSScriptRoot "generated-map-packs"),
    [string]$TrailCode,
    [string]$Serial,
    [switch]$SkipBasePack
)

$ErrorActionPreference = "Stop"

function Get-AdbCommand {
    $adb = Get-Command adb -ErrorAction SilentlyContinue
    if ($adb) {
        return $adb.Source
    }

    if ($env:ANDROID_HOME) {
        $candidate = Join-Path $env:ANDROID_HOME "platform-tools\adb.exe"
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

function Get-SafePathSegment {
    param([string]$Value)
    $safe = ($Value.ToLowerInvariant() -replace "[^a-z0-9._-]+", "_").Trim("_")
    if (!$safe) {
        return "route"
    }
    return $safe
}

function Push-Pack {
    param(
        [string]$LocalPath,
        [string]$RemotePath
    )

    if (!(Test-Path $LocalPath)) {
        throw "Missing local pack: $LocalPath"
    }
    $remoteDir = Split-Path -Parent $RemotePath
    Invoke-Adb -CommandArgs @("shell", "mkdir", "-p", $remoteDir)
    Invoke-Adb -CommandArgs @("push", $LocalPath, $RemotePath)
}

$deviceMapsDir = "/sdcard/Android/data/$PackageName/files/maps"

Invoke-Adb -CommandArgs @("get-state")
Invoke-Adb -CommandArgs @("shell", "pm", "list", "packages", $PackageName)

if (-not $SkipBasePack) {
    $basePack = Join-Path $PacksDir "romania-high-detail.pmtiles"
    Push-Pack -LocalPath $basePack -RemotePath "$deviceMapsDir/romania-high-detail.pmtiles"
}

if ($TrailCode) {
    $safeTrailCode = Get-SafePathSegment -Value $TrailCode
    $trailPack = Join-Path $PacksDir "trails\$safeTrailCode\offline.pmtiles"
    Push-Pack -LocalPath $trailPack -RemotePath "$deviceMapsDir/trails/$safeTrailCode/offline.pmtiles"
}

Write-Host "Map packs pushed to app-specific external storage for $PackageName."
