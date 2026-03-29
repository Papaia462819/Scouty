# Local Gemma Runtime

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

## Supported model bundle

- Target model: Gemma 3 1B instruction-tuned bundle in Google AI Edge compatible format.
- Supported file extensions:
  - `.task`
  - `.litertlm`
- Expected bundle examples:
  - `gemma-3-1b-it-int4.task`
  - `gemma-3-1b-it-int4.litertlm`

## Where Scouty looks for the model

Scouty scans these locations, in this order:

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

Optional end-to-end smoke path:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File tools\smoke_ai_runtime.ps1
```

Then start Scouty. `ModelManager` will detect the bundle in `no_backup`, load Gemma through Google AI Edge MediaPipe, and chat requests will attempt `LOCAL_LLM` before any structured fallback.

## Optional sidecar manifest

Scouty can read either:

- `scouty_model_manifest.json`
- `<bundle-name>.json`

Example:

```json
{
  "model_version": "gemma-3-1b-it-int4",
  "preferred_backend": "CPU",
  "max_tokens": 4096
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
- The current adapter is built around Google AI Edge MediaPipe LLM Inference, but it is isolated behind `LocalLlmRuntimeAdapter` so it can be swapped later.
- Generation is text-only for this integration slice. Retrieval and truth remain in the knowledge pack and trail context.
- GPU backend is not viable on the current emulator image because the required OpenCL stack is missing; the validated runtime path is CPU.

## Validation checklist

Validated emulator sequence:

```powershell
./gradlew.bat testDebugUnitTest
powershell -NoProfile -ExecutionPolicy Bypass -File tools\reinstall_debug_with_assets.ps1
adb shell am instrument -w -e class com.scouty.app.assistant.AssistantRepositoryRuntimeTest com.scouty.app.test/androidx.test.runner.AndroidJUnitRunner
adb shell am instrument -w -e class com.scouty.app.assistant.AssistantChatRuntimeTest com.scouty.app.test/androidx.test.runner.AndroidJUnitRunner
adb logcat -d | Select-String -Pattern "ScoutyAssistant|ScoutyLocalLlm|AndroidRuntime|com.scouty.app"
```

In the app or logs, verify:

- knowledge pack version
- model version
- model state
- generation mode
- model path
- sync timestamps
