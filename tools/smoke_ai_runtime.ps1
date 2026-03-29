param(
    [string]$PackageName = "com.scouty.app",
    [string]$ActivityName = ".MainActivity",
    [string]$Query = "cum fac focul",
    [string]$Serial,
    [string]$ModelPath,
    [switch]$DownloadIfMissing,
    [switch]$SkipBuild
)

$ErrorActionPreference = "Stop"

$repoRoot = Split-Path -Parent $PSScriptRoot
$pushScript = Join-Path $PSScriptRoot "push_model_to_device.ps1"
$reinstallScript = Join-Path $PSScriptRoot "reinstall_debug_with_assets.ps1"
$adbArgs = @()
if ($Serial) {
    $adbArgs += "-s", $Serial
}

if (!(Test-Path $pushScript)) {
    throw "Missing model push script '$pushScript'."
}
if (!(Test-Path $reinstallScript)) {
    throw "Missing reinstall script '$reinstallScript'."
}

function Invoke-Adb {
    param([string[]]$CommandArgs)

    & adb @adbArgs @CommandArgs
    if ($LASTEXITCODE -ne 0) {
        throw "adb command failed: $($CommandArgs -join ' ')"
    }
}

if (-not $SkipBuild) {
    & $reinstallScript -PackageName $PackageName -Serial $Serial -ModelPath $ModelPath -DownloadIfMissing:$DownloadIfMissing
    if ($LASTEXITCODE -ne 0) {
        throw "Debug reinstall failed."
    }
} else {
    & $pushScript -PackageName $PackageName -Serial $Serial -ModelPath $ModelPath -DownloadIfMissing:$DownloadIfMissing
    if ($LASTEXITCODE -ne 0) {
        throw "Model push failed."
    }
}

foreach ($permission in @(
    "android.permission.ACCESS_FINE_LOCATION",
    "android.permission.ACCESS_COARSE_LOCATION",
    "android.permission.CAMERA"
)) {
    & adb @adbArgs shell pm grant $PackageName $permission | Out-Null
}

Invoke-Adb -CommandArgs @("logcat", "-c")
Invoke-Adb -CommandArgs @(
    "shell",
    "am",
    "instrument",
    "-w",
    "-e",
    "class",
    "com.scouty.app.assistant.AssistantRuntimeDebugTest",
    "$PackageName.test/androidx.test.runner.AndroidJUnitRunner"
)
Invoke-Adb -CommandArgs @(
    "shell",
    "am",
    "instrument",
    "-w",
    "-e",
    "class",
    "com.scouty.app.assistant.AssistantChatRuntimeTest",
    "$PackageName.test/androidx.test.runner.AndroidJUnitRunner"
)

$pullTarget = Join-Path $repoRoot "scratch\emulator_knowledge_pack.sqlite"
$remotePullTarget = "/sdcard/Download/$PackageName-knowledge_pack.sqlite"
New-Item -ItemType Directory -Force -Path (Split-Path -Parent $pullTarget) | Out-Null

Invoke-Adb -CommandArgs @(
    "shell",
    "run-as",
    $PackageName,
    "cp",
    "/data/user/0/$PackageName/no_backup/knowledge_pack/knowledge_pack.sqlite",
    $remotePullTarget
)
Invoke-Adb -CommandArgs @("pull", $remotePullTarget, $pullTarget)
Invoke-Adb -CommandArgs @("shell", "rm", "-f", $remotePullTarget)

Write-Host ""
Write-Host "Direct SQLite verification on pulled emulator DB:"
@"
import sqlite3
path = r"$pullTarget"
conn = sqlite3.connect(path)
rows = conn.execute(
    '''
    SELECT kc.chunk_id, kc.domain, kc.language, kc.title
    FROM knowledge_chunks kc
    JOIN knowledge_chunks_fts fts ON kc.row_id = fts.rowid
    WHERE knowledge_chunks_fts MATCH ?
    ORDER BY kc.language, kc.domain, kc.title
    ''',
    ('focul* OR foc*',)
).fetchall()
for row in rows:
    print(row)
conn.close()
"@ | python -

Write-Host ""
Write-Host "Relevant logcat lines:"
& adb @adbArgs logcat -d | Select-String -Pattern "ScoutyAssistant|ScoutyModelManager|ScoutyLocalLlm|ScoutyRuntimeTest|AndroidRuntime|FATAL EXCEPTION"
