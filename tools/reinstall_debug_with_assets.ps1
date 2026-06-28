param(
    [string]$PackageName = "com.nego.scouty",
    [string]$TestPackageName = "com.nego.scouty.test",
    [string]$Serial,
    [switch]$SkipBuild,
    [switch]$SkipMaps
)

$ErrorActionPreference = "Stop"

$repoRoot = Split-Path -Parent $PSScriptRoot
$appApk = Join-Path $repoRoot "app\build\outputs\apk\debug\app-debug.apk"
$testApk = Join-Path $repoRoot "app\build\outputs\apk\androidTest\debug\app-debug-androidTest.apk"
$pushMapsScript = Join-Path $PSScriptRoot "push_map_packs_to_device.ps1"

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
    param(
        [string[]]$CommandArgs,
        [switch]$CaptureOutput,
        [switch]$IgnoreExitCode
    )

    $adb = Get-AdbCommand
    $fullArgs = @()
    if ($Serial) {
        $fullArgs += "-s", $Serial
    }
    $fullArgs += $CommandArgs

    Write-Host "> adb $($fullArgs -join ' ')"
    if ($CaptureOutput) {
        $output = & $adb @fullArgs
        if ($LASTEXITCODE -ne 0 -and -not $IgnoreExitCode) {
            throw "adb command failed: $($fullArgs -join ' ')"
        }
        return $output
    }

    & $adb @fullArgs
    if ($LASTEXITCODE -ne 0 -and -not $IgnoreExitCode) {
        throw "adb command failed: $($fullArgs -join ' ')"
    }
}

function Invoke-AdbQuiet {
    param([string[]]$CommandArgs)

    Invoke-Adb -CommandArgs $CommandArgs -IgnoreExitCode | Out-Null
}

function Install-ApkStreamed {
    param(
        [string]$ApkPath,
        [switch]$AllowTestPackage
    )

    if (!(Test-Path $ApkPath -PathType Leaf)) {
        throw "Missing APK '$ApkPath'."
    }

    $adb = Get-AdbCommand
    $args = @()
    if ($Serial) {
        $args += "-s", $Serial
    }
    $args += @("install", "--streaming", "-r")
    if ($AllowTestPackage) {
        $args += "-t"
    }
    $args += $ApkPath

    Write-Host "> adb $($args -join ' ')"
    & $adb @args
    if ($LASTEXITCODE -ne 0) {
        throw "Streamed install failed for '$ApkPath'."
    }
}

if (-not $SkipBuild) {
    & "$repoRoot\gradlew.bat" :app:assembleDebug :app:assembleDebugAndroidTest
    if ($LASTEXITCODE -ne 0) {
        throw "Gradle assemble failed."
    }
}

if (!(Test-Path $pushMapsScript)) {
    throw "Missing map push script '$pushMapsScript'."
}

Invoke-Adb -CommandArgs @("get-state")
Invoke-AdbQuiet -CommandArgs @("shell", "rm", "-f", "/data/local/tmp/app-debug.apk")
Invoke-AdbQuiet -CommandArgs @("shell", "pm", "trim-caches", "1G")
Invoke-AdbQuiet -CommandArgs @("uninstall", $TestPackageName)
Invoke-AdbQuiet -CommandArgs @("uninstall", $PackageName)

Install-ApkStreamed -ApkPath $appApk
Install-ApkStreamed -ApkPath $testApk -AllowTestPackage

foreach ($permission in @(
    "android.permission.ACCESS_FINE_LOCATION",
    "android.permission.ACCESS_COARSE_LOCATION",
    "android.permission.CAMERA"
)) {
    Invoke-AdbQuiet -CommandArgs @("shell", "pm", "grant", $PackageName, $permission)
}

if (-not $SkipMaps) {
    & $pushMapsScript -PackageName $PackageName -Serial $Serial
    if ($LASTEXITCODE -ne 0) {
        throw "Map push failed."
    }
}

$storageStats = Invoke-Adb -CommandArgs @("shell", "pm", "get-package-storage-stats", $PackageName) -CaptureOutput
$apkPathLine = Invoke-Adb -CommandArgs @("shell", "pm", "path", $PackageName) -CaptureOutput |
    Select-Object -First 1
$apkDevicePath = ($apkPathLine -replace "^package:", "").Trim()
$apkStat = Invoke-Adb -CommandArgs @("shell", "ls", "-l", $apkDevicePath) -CaptureOutput
$mapsStat = Invoke-Adb -CommandArgs @("shell", "run-as", $PackageName, "ls", "-ln", "files/maps") -CaptureOutput -IgnoreExitCode

Write-Host ""
Write-Host "Installed package size report:"
$storageStats | ForEach-Object { Write-Host "  $_" }
Write-Host ""
Write-Host "Installed base APK:"
$apkStat | ForEach-Object { Write-Host "  $_" }

if ($mapsStat) {
    Write-Host ""
    Write-Host "Installed map files:"
    $mapsStat | ForEach-Object { Write-Host "  $_" }
}
