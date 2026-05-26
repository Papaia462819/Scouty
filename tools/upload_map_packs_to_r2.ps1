param(
    [string]$SourceDir = (Join-Path $PSScriptRoot "generated-map-packs"),
    [string]$Bucket = $env:R2_BUCKET,
    [string]$AccountId = $env:CLOUDFLARE_ACCOUNT_ID,
    [string]$EndpointUrl = $env:R2_ENDPOINT_URL,
    [string]$Profile = $env:AWS_PROFILE,
    [switch]$DryRun
)

$ErrorActionPreference = "Stop"

function Resolve-R2Endpoint {
    if ($EndpointUrl) {
        return $EndpointUrl
    }
    if (!$AccountId) {
        throw "Set CLOUDFLARE_ACCOUNT_ID or pass -EndpointUrl."
    }
    return "https://$AccountId.r2.cloudflarestorage.com"
}

function Invoke-AwsS3 {
    param([string[]]$CommandArgs)

    $aws = Get-Command aws -ErrorAction SilentlyContinue
    if (!$aws) {
        throw "AWS CLI was not found on PATH."
    }

    $fullArgs = @("s3") + $CommandArgs + @("--endpoint-url", (Resolve-R2Endpoint))
    if ($Profile) {
        $fullArgs += @("--profile", $Profile)
    }
    if ($DryRun) {
        $fullArgs += "--dryrun"
    }

    Write-Host "> aws $($fullArgs -join ' ')"
    & $aws.Source @fullArgs
    if ($LASTEXITCODE -ne 0) {
        throw "aws command failed: $($fullArgs -join ' ')"
    }
}

if (!$Bucket) {
    throw "Set R2_BUCKET or pass -Bucket."
}

$basePack = Join-Path $SourceDir "romania-high-detail.pmtiles"
$trailsDir = Join-Path $SourceDir "trails"

if (!(Test-Path $basePack)) {
    throw "Missing base pack: $basePack"
}
if (!(Test-Path $trailsDir)) {
    throw "Missing trails directory: $trailsDir"
}

Invoke-AwsS3 -CommandArgs @(
    "cp",
    $basePack,
    "s3://$Bucket/base/romania-high-detail.pmtiles",
    "--content-type",
    "application/octet-stream",
    "--cache-control",
    "public,max-age=31536000,immutable"
)

Invoke-AwsS3 -CommandArgs @(
    "sync",
    $trailsDir,
    "s3://$Bucket/trails",
    "--exclude",
    "*",
    "--include",
    "*.pmtiles",
    "--content-type",
    "application/octet-stream",
    "--cache-control",
    "public,max-age=31536000,immutable"
)

Invoke-AwsS3 -CommandArgs @(
    "sync",
    $trailsDir,
    "s3://$Bucket/trails",
    "--exclude",
    "*",
    "--include",
    "*.json",
    "--content-type",
    "application/json",
    "--cache-control",
    "public,max-age=300"
)

Write-Host "Map packs uploaded to R2 bucket '$Bucket'."
