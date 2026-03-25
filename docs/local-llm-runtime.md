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
- If the local model is missing, fails to load, or returns invalid JSON, Scouty falls back to the existing structured template generator.

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

If the bundle is found only in external app storage, Scouty imports it into internal no-backup storage on first load and then initializes the runtime from there.

## Recommended debug install flow

Push the bundle into app external storage:

```powershell
adb shell mkdir -p /sdcard/Android/data/com.scouty.app/files/models/gemma-3-1b
adb push gemma-3-1b-it-int4.task /sdcard/Android/data/com.scouty.app/files/models/gemma-3-1b/
```

Then start Scouty. `ModelManager` will detect the bundle, prepare it, and attempt to load it.

## Optional sidecar manifest

Scouty can read either:

- `scouty_model_manifest.json`
- `<bundle-name>.json`

Example:

```json
{
  "model_version": "gemma-3-1b-it-int4",
  "preferred_backend": "CPU",
  "max_tokens": 32768
}
```

If the manifest is absent, Scouty derives the model version from the filename and defaults to:

- backend: `CPU`
- max tokens: `32768`

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
- Runtime validation on Android emulators is limited. Google’s AI Edge docs state that LLM Inference does not reliably support Android emulators.
- The current adapter is built around Google AI Edge MediaPipe LLM Inference, but it is isolated behind `LocalLlmRuntimeAdapter` so it can be swapped later.
- Generation is text-only for this integration slice. Retrieval and truth remain in the knowledge pack and trail context.

## Validation checklist

Use this sequence after placing a bundle on device:

```powershell
./gradlew.bat :app:assembleDebug
./gradlew.bat testDebugUnitTest
./gradlew.bat :app:installDebug
adb shell am start -n com.scouty.app/.MainActivity
adb logcat -d | Select-String -Pattern "Scouty|AndroidRuntime|com.scouty.app"
```

In the app, open Profile -> Debug & offline and verify:

- knowledge pack version
- model version
- model state
- generation mode
- model path
- sync timestamps
