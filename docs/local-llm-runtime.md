# Local LLM Runtime

Scouty now ships with a production-shaped on-device LLM runtime path that keeps retrieval, grounding, citations, and safety authority in the existing assistant flow.

## What is wired

- Retrieval stays local and authoritative:
  - language detection
  - domain hints
  - candidate retrieval from `knowledge_pack.sqlite`
  - rerank and top chunk selection
- `SafetyPolicy` remains authoritative before and after generation.
- `LocalLlmGenerationEngine` is an optional generator stage on top of the grounded retrieval output.
- If the local model is missing or fails to load, Scouty falls back to the existing structured template generator.
- When the local model returns incomplete or malformed structured JSON, Scouty now repairs the answer from the grounded local summary plus the already selected retrieval chunks instead of dropping straight to a generic fallback.

## Runtime feature flags

`AssistantRuntimeGraph` owns runtime selection through `RuntimeFeatureFlags`.

- `useLlamaCpp = false` keeps the legacy MediaPipe/Gemma path active.
- `useLlamaCpp = true` enables Qwen GGUF discovery and loads through the llama.cpp JNI adapter.

The flag defaults to `false` while the Qwen migration is staged.

## Supported model bundles

### llama.cpp + Qwen

- Target model: Qwen 2.5 1.5B Instruct Q4_K_M GGUF.
- Runtime: `llama_cpp`.
- Supported file extension: `.gguf`.
- Expected bundle example: `qwen2.5-1.5b-instruct-q4_k_m.gguf`.
- Source URL: `https://huggingface.co/Qwen/Qwen2.5-1.5B-Instruct-GGUF/resolve/main/qwen2.5-1.5b-instruct-q4_k_m.gguf`.
- SHA-256: `6A1A2EB6D15622BF3C96857206351BA97E1AF16C30D7A74EE38970E434E9407E`.
- Size: `1117320736` bytes.
- Prompt format: call sites pass plain prompt text; `LlamaCppRuntimeAdapter` wraps Qwen prompts with ChatML:

```text
<|im_start|>system
...
<|im_end|>
<|im_start|>user
...
<|im_end|>
<|im_start|>assistant
```

Already formatted ChatML prompts are passed through unchanged for debug compatibility.

### MediaPipe + Gemma legacy path

- Target model: Gemma 3 1B instruction-tuned bundle in Google AI Edge compatible format.
- Runtime: `mediapipe`.
- Supported file extensions: `.task`, `.litertlm`.
- Expected bundle examples: `gemma-3-1b-it-int4.task`, `gemma-3-1b-it-int4.litertlm`.

## Where Scouty looks for the model

When `useLlamaCpp = true`, Scouty scans these Qwen locations:

1. Internal no-backup storage:
   - `Context.noBackupFilesDir/models/qwen-2.5-1.5b/`
2. External app storage:
   - `Context.getExternalFilesDir(null)/models/qwen-2.5-1.5b/`

When `useLlamaCpp = false`, Scouty scans these Gemma locations:

1. Internal no-backup storage:
   - `Context.noBackupFilesDir/models/gemma-3-1b/`
2. External app storage:
   - `Context.getExternalFilesDir(null)/models/gemma-3-1b/`

In practice, the validated emulator flow pushes the bundle straight into internal no-backup storage, because the app process may not reliably enumerate the external app directory on the Android emulator.

## Recommended debug install flow

When emulator storage is tight, use the reinstall helper. It rebuilds the debug APKs, uninstalls the previous app package, reinstalls with `adb install --streaming`, re-grants runtime permissions, reloads Gemma into internal storage, reloads map packs, and prints the exact on-device package size report:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File tools\reinstall_debug_with_assets.ps1
```

If the app is already installed and you only need to refresh the model bundle, use the model push helper, which writes the bundle into internal app storage and updates the sidecar manifest:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File tools\push_model_to_device.ps1
```

For the Qwen GGUF bundle:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File tools\push_qwen_to_device.ps1 -ModelPath D:\ScoutyScratch\models\qwen2.5-1.5b-instruct-q4_k_m.gguf
```

Optional end-to-end smoke path:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File tools\smoke_ai_runtime.ps1
```

Then start Scouty. `ModelManager` will detect the bundle in `no_backup`, load the selected runtime, and chat requests will attempt `LOCAL_LLM` before any structured fallback.

## Optional sidecar manifest

Scouty can read either:

- `scouty_model_manifest.json`
- `<bundle-name>.json`

Example:

```json
{
  "model_version": "gemma-3-1b-it-int4",
  "runtime": "mediapipe",
  "preferred_backend": "CPU",
  "max_tokens": 4096
}
```

Qwen example:

```json
{
  "model_version": "qwen2.5-1.5b-instruct-q4_k_m",
  "runtime": "llama_cpp",
  "preferred_backend": "CPU",
  "max_tokens": 8192
}
```

If the manifest is absent, Scouty derives the model version from the filename and defaults to:

- backend: `CPU`
- max tokens: `4096`

## Activation behavior

`ModelManager` exposes these states:

- `Missing`
- `Preparing`
- `Loaded`
- `Failed`
- `Unloaded`

Scouty loads the model eagerly when it is detected, and also re-attempts load during assistant generation if needed.

## Fallback behavior

Scouty falls back to `TemplateGenerationEngine` when:

- no local model bundle is detected
- model import/preparation fails
- runtime initialization fails
- local generation fails
- the model response does not match Scouty’s structured JSON schema

Fallback still preserves:

- structured output
- reasoning type
- knowledge pack version
- citations
- safety override

## Current limitations

- The model bundle is not stored in the repo and is not packaged in the APK.
- MediaPipe/Gemma remains available as the legacy fallback path.
- llama.cpp prompt-cache support is currently a single in-process prefix cache; Step 3 provides stable system + summary cache keys.
- Generation is text-only for this integration slice. Retrieval and truth remain in the knowledge pack and trail context.
- GPU backend is not viable on the current emulator image because the required OpenCL stack is missing; the validated runtime path is CPU.

## Validation checklist

Validated unit sequence:

```powershell
./gradlew.bat testDebugUnitTest
./gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest
```

Validated legacy MediaPipe emulator sequence:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File tools\reinstall_debug_with_assets.ps1
adb shell am instrument -w -e class com.scouty.app.assistant.AssistantRepositoryRuntimeTest com.scouty.app.test/androidx.test.runner.AndroidJUnitRunner
adb shell am instrument -w -e class com.scouty.app.assistant.AssistantChatRuntimeTest com.scouty.app.test/androidx.test.runner.AndroidJUnitRunner
adb logcat -d | Select-String -Pattern "ScoutyAssistant|ScoutyLocalLlm|AndroidRuntime|com.scouty.app"
```

Validated llama.cpp smoke sequence on the `test` AVD used a temporary x86_64 JNI build for emulator-only validation:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File tools\build_llama_jni.ps1 -Abi x86_64 -LlamaCppRoot D:\ScoutyScratch\llama.cpp -NdkRoot D:\Android\Sdk\ndk\27.2.12479018
./gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest
adb install --streaming -r app\build\outputs\apk\debug\app-debug.apk
adb install --streaming -r -t app\build\outputs\apk\androidTest\debug\app-debug-androidTest.apk
powershell -NoProfile -ExecutionPolicy Bypass -File tools\push_qwen_to_device.ps1 -ModelPath D:\ScoutyScratch\models\qwen2.5-1.5b-instruct-q4_k_m.gguf
adb shell am instrument -w -e class com.scouty.app.assistant.LlamaCppRuntimeDebugTest com.scouty.app.test/androidx.test.runner.AndroidJUnitRunner
```

The smoke test sends a plain Romanian prompt. Passing it verifies that Qwen model discovery, `ModelManager` state transition, adapter-side ChatML wrapping, and Romanian generation all work together.

Remove the temporary `app/src/main/jniLibs/x86_64/` directory before committing unless x86_64 support is explicitly part of the release.

In the app or logs, verify:

- knowledge pack version
- model version
- model state
- generation mode
- model path
- sync timestamps
