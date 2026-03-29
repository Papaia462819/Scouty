param(
    [string]$PackageName = "com.scouty.app",
    [string]$ModelPath,
    [string]$Serial,
    [ValidateSet("CPU", "GPU")]
    [string]$PreferredBackend = "CPU",
    [int]$MaxTokens = 4096,
    [int]$MinimumSizeMb = 100,
    [switch]$DownloadIfMissing,
    [string]$DownloadUrl = "https://huggingface.co/ANISH-j/models-for-echo-application/resolve/main/gemma3-1b-it-int4.task?download=true",
    [string]$DownloadDir = (Join-Path (Split-Path -Parent $PSScriptRoot) "scratch\models")
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

function Format-ProcessArguments {
    param([string[]]$Arguments)

    $quotedArguments = $Arguments | ForEach-Object {
        if ($_ -match '[\s"]') {
            '"' + ($_ -replace '"', '\"') + '"'
        } else {
            $_
        }
    }

    return [string]::Join(" ", $quotedArguments)
}

function Invoke-Adb {
    param(
        [string[]]$CommandArgs,
        [switch]$CaptureOutput
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
        if ($LASTEXITCODE -ne 0) {
            throw "adb command failed: $($fullArgs -join ' ')"
        }
        return $output
    }

    & $adb @fullArgs
    if ($LASTEXITCODE -ne 0) {
        throw "adb command failed: $($fullArgs -join ' ')"
    }
}

function Invoke-AdbBinaryUpload {
    param(
        [string]$LocalPath,
        [string]$RemotePath
    )

    $adb = Get-AdbCommand
    $fullArgs = @()
    if ($Serial) {
        $fullArgs += "-s", $Serial
    }
    $fullArgs += @(
        "exec-in",
        "run-as",
        $PackageName,
        "sh",
        "-c",
        "cat > '$RemotePath'"
    )

    Write-Host "> adb $($fullArgs -join ' ') < $LocalPath"

    $psi = New-Object System.Diagnostics.ProcessStartInfo
    $psi.FileName = $adb
    $psi.UseShellExecute = $false
    $psi.RedirectStandardInput = $true
    $psi.RedirectStandardOutput = $true
    $psi.RedirectStandardError = $true

    if ($psi.ArgumentList -ne $null) {
        foreach ($arg in $fullArgs) {
            [void]$psi.ArgumentList.Add($arg)
        }
    } else {
        $psi.Arguments = Format-ProcessArguments -Arguments $fullArgs
    }

    $process = New-Object System.Diagnostics.Process
    $process.StartInfo = $psi

    try {
        [void]$process.Start()
        $inputStream = [System.IO.File]::OpenRead($LocalPath)
        try {
            $inputStream.CopyTo($process.StandardInput.BaseStream)
            $process.StandardInput.BaseStream.Flush()
        } finally {
            $inputStream.Dispose()
            $process.StandardInput.Close()
        }

        $stdout = $process.StandardOutput.ReadToEnd()
        $stderr = $process.StandardError.ReadToEnd()
        $process.WaitForExit()

        if ($process.ExitCode -ne 0) {
            throw "Binary upload failed for '$RemotePath'. stdout=$stdout stderr=$stderr"
        }

        if ($stdout.Trim()) {
            Write-Host $stdout.Trim()
        }
        if ($stderr.Trim()) {
            Write-Host $stderr.Trim()
        }
    } finally {
        $process.Dispose()
    }
}

function Get-CandidateFiles {
    $roots = @(
        $ModelPath,
        (Join-Path (Split-Path -Parent $PSScriptRoot) "models"),
        (Join-Path (Split-Path -Parent $PSScriptRoot) "scratch\models"),
        (Join-Path $env:USERPROFILE "Downloads"),
        (Join-Path $env:USERPROFILE "Desktop")
    ) | Where-Object { $_ } | Select-Object -Unique

    foreach ($root in $roots) {
        if (Test-Path $root -PathType Leaf) {
            Get-Item $root
            continue
        }
        if (!(Test-Path $root -PathType Container)) {
            continue
        }
        Get-ChildItem -Path $root -Recurse -Include *.task,*.litertlm -File -ErrorAction SilentlyContinue
    }
}

function Resolve-ModelPath {
    $minimumBytes = $MinimumSizeMb * 1MB
    $candidate = Get-CandidateFiles |
        Where-Object {
            $_.Length -ge $minimumBytes -and
            $_.Name.ToLowerInvariant().Contains("gemma") -and
            $_.Name.ToLowerInvariant().Contains("1b")
        } |
        Sort-Object LastWriteTime -Descending |
        Select-Object -First 1

    if ($candidate) {
        return $candidate.FullName
    }

    if (-not $DownloadIfMissing) {
        throw "No usable Gemma bundle was found locally. Pass -ModelPath or use -DownloadIfMissing."
    }

    New-Item -ItemType Directory -Force -Path $DownloadDir | Out-Null
    $downloadTarget = Join-Path $DownloadDir "gemma3-1b-it-int4.task"
    Write-Host "Downloading model bundle to $downloadTarget"
    & curl.exe -L -C - $DownloadUrl -o $downloadTarget
    if ($LASTEXITCODE -ne 0) {
        throw "Model download failed."
    }
    return $downloadTarget
}

function Get-ModelVersion {
    param([string]$ResolvedModelPath)

    $baseName = [System.IO.Path]::GetFileNameWithoutExtension($ResolvedModelPath)
    if ($baseName -like "gemma3-*") {
        return $baseName -replace "^gemma3-", "gemma-3-"
    }
    return $baseName
}

$resolvedModelPath = Resolve-ModelPath
$modelFile = Get-Item $resolvedModelPath
$modelVersion = Get-ModelVersion -ResolvedModelPath $resolvedModelPath

$internalDir = "/data/user/0/$PackageName/no_backup/models/gemma-3-1b"
$internalModelPath = "$internalDir/$($modelFile.Name)"
$externalDir = "/sdcard/Android/data/$PackageName/files/models/gemma-3-1b"

$manifestJson = @"
{
  "model_version": "$modelVersion",
  "preferred_backend": "$PreferredBackend",
  "max_tokens": $MaxTokens
}
"@
$tempManifest = Join-Path ([System.IO.Path]::GetTempPath()) "scouty_model_manifest.json"
Set-Content -Path $tempManifest -Value $manifestJson -Encoding ascii

Invoke-Adb -CommandArgs @("get-state")
$packageListing = Invoke-Adb -CommandArgs @("shell", "pm", "list", "packages", $PackageName) -CaptureOutput
if (($packageListing -join "`n") -notmatch "package:$([regex]::Escape($PackageName))") {
    throw "Package '$PackageName' is not installed on the target device."
}

Write-Host "Removing stale external bundle copy to free shared emulator storage."
Invoke-Adb -CommandArgs @("shell", "rm", "-rf", $externalDir)

Invoke-Adb -CommandArgs @("shell", "run-as", $PackageName, "mkdir", "-p", $internalDir)
Invoke-Adb -CommandArgs @("shell", "run-as", $PackageName, "rm", "-f", $internalModelPath)
Invoke-Adb -CommandArgs @("shell", "run-as", $PackageName, "rm", "-f", "$internalDir/scouty_model_manifest.json")

Invoke-AdbBinaryUpload -LocalPath $resolvedModelPath -RemotePath $internalModelPath
Invoke-AdbBinaryUpload -LocalPath $tempManifest -RemotePath "$internalDir/scouty_model_manifest.json"

Invoke-Adb -CommandArgs @("shell", "run-as", $PackageName, "ls", "-lh", $internalDir)

Write-Host ""
Write-Host "Model pushed successfully."
Write-Host "  Local file : $resolvedModelPath"
Write-Host "  Remote dir : $internalDir"
Write-Host "  Version    : $modelVersion"
Write-Host "  Backend    : $PreferredBackend"
Write-Host "  Max tokens : $MaxTokens"
