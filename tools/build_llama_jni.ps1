param(
    [string]$LlamaCppRoot = "D:\ScoutyScratch\llama.cpp",
    [string]$AndroidSdkRoot = "D:\Android\Sdk",
    [string]$NdkVersion = "27.2.12479018",
    [string]$BuildRoot = "D:\ScoutyScratch\scouty_llama_jni_build",
    [string[]]$Abi = @("arm64-v8a", "armeabi-v7a")
)

$ErrorActionPreference = "Stop"

$repoRoot = Split-Path -Parent $PSScriptRoot
$sourceDir = Join-Path $repoRoot "app\src\main\cpp"
$outputRoot = Join-Path $repoRoot "app\src\main\jniLibs"
$ndkRoot = Join-Path $AndroidSdkRoot "ndk\$NdkVersion"
$toolchain = Join-Path $ndkRoot "build\cmake\android.toolchain.cmake"

if (!(Test-Path $LlamaCppRoot -PathType Container)) {
    throw "llama.cpp checkout not found at '$LlamaCppRoot'. Clone ggml-org/llama.cpp first."
}
if (!(Test-Path $toolchain -PathType Leaf)) {
    throw "Android NDK toolchain not found at '$toolchain'."
}

foreach ($abiName in $Abi) {
    $buildDir = Join-Path $BuildRoot $abiName
    $abiOutputDir = Join-Path $outputRoot $abiName

    New-Item -ItemType Directory -Force -Path $buildDir | Out-Null
    New-Item -ItemType Directory -Force -Path $abiOutputDir | Out-Null

    $configureArgs = @(
        "-S", $sourceDir,
        "-B", $buildDir,
        "-G", "Ninja",
        "-DCMAKE_TOOLCHAIN_FILE=$toolchain",
        "-DANDROID_ABI=$abiName",
        "-DANDROID_PLATFORM=android-28",
        "-DANDROID_STL=c++_static",
        "-DCMAKE_BUILD_TYPE=Release",
        "-DANDROID_SUPPORT_FLEXIBLE_PAGE_SIZES=ON",
        "-DLLAMA_CPP_ROOT=$LlamaCppRoot",
        "-DLLAMA_BUILD_EXAMPLES=OFF",
        "-DLLAMA_BUILD_TESTS=OFF",
        "-DLLAMA_CURL=OFF",
        "-DGGML_OPENMP=OFF",
        "-DGGML_NATIVE=OFF"
    )

    & cmake @configureArgs

    if ($LASTEXITCODE -ne 0) {
        throw "CMake configure failed for $abiName."
    }

    & cmake --build $buildDir --target scouty_llama_jni --config Release
    if ($LASTEXITCODE -ne 0) {
        throw "CMake build failed for $abiName."
    }

    $library = Get-ChildItem -Path $buildDir -Recurse -Filter libscouty_llama_jni.so -File |
        Sort-Object LastWriteTime -Descending |
        Select-Object -First 1
    if (!$library) {
        throw "Built library not found for $abiName."
    }

    Copy-Item -LiteralPath $library.FullName -Destination (Join-Path $abiOutputDir "libscouty_llama_jni.so") -Force
    Write-Host "Copied $($library.FullName) -> $abiOutputDir"
}
