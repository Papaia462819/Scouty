# Qwen migration notes

## Step 1 - Cross-encoder reranker

Status: complete behind `RuntimeFeatureFlags.useCrossEncoderReranker` (default off).

Changed:
- Added `RuntimeFeatureFlags` in `AssistantRuntimeGraph.kt`; `useCrossEncoderReranker` defaults to `false`.
- Added `CrossEncoderReranker` backed by ONNX Runtime + Hugging Face tokenizer assets.
- Swapped the planned BGE asset for `jinaai/jina-reranker-v2-base-multilingual` int8 ONNX.
- Wired the optional reranker into `CampfireConversationEngine` after deterministic scoring and before confidence assessment.
- Batched top-3 candidate scoring with a 96-token window; the reranker evidence is blended with the deterministic score (`0.8 * deterministic + 20.0 * reranker_score`) to avoid destabilizing slot/family rules.
- Extended campfire confidence signals so reranker scores can replace deterministic top-1 strength and margin when available.
- Added diagnostics log fields: `rerank_latency_ms` and `top1_score_delta_vs_lexical`.
- Added a fixed 30-query Romanian benchmark seed in `tools/benchmarks/reranker_ro_queries.json`.
- Added manifest metadata for the expected reranker and tokenizer assets.
- Fixed campfire definition handling for Romanian/English mixed "tinder/tindar" follow-ups so existing tests pass.

Files touched:
- `app/build.gradle.kts`
- `app/src/main/java/com/scouty/app/assistant/diagnostics/AssistantDiagnostics.kt`
- `app/src/main/java/com/scouty/app/assistant/domain/AssistantRepository.kt`
- `app/src/main/java/com/scouty/app/assistant/domain/AssistantRuntimeGraph.kt`
- `app/src/main/java/com/scouty/app/assistant/domain/CampfireConversationEngine.kt`
- `app/src/main/java/com/scouty/app/assistant/domain/InterpretationPipeline.kt`
- `app/src/main/java/com/scouty/app/assistant/domain/retrieval/CrossEncoderReranker.kt`
- `app/src/main/scouty_assets/knowledge_pack_manifest.json`
- `app/src/main/scouty_assets/ml/jina-reranker-v2-base-multilingual-int8.onnx`
- `app/src/main/scouty_assets/ml/jina-reranker-v2-base-multilingual-tokenizer.json`
- `tools/benchmarks/bench_jina_reranker.py`
- `tools/benchmarks/reranker_ro_queries.json`
- `tools/benchmarks/reranker_jina_results.json`

Validation:
- `./gradlew.bat testDebugUnitTest` passes: 134 tests.
- `python tools/benchmarks/bench_jina_reranker.py --json-out tools/benchmarks/reranker_jina_results.json`
- Fixed 30-query Romanian campfire bench:
  - baseline top-1 accuracy: 70.0%
  - Jina-reranked top-1 accuracy: 73.3%
  - delta: +3.3 percentage points
  - rerank p50: 39.8 ms
  - rerank p95: 46.6 ms

Deviation:
- Jina's current official `model_int8.onnx` is 279,577,152 bytes, not ~130 MB. The tokenizer is 17,082,734 bytes. This still keeps the projected Qwen + reranker footprint inside the ~1.5 GB target.
- The p50 gate is only met with top-3 candidates and a 96-token cross-encoder window. Top-10 at 512 tokens measured roughly 1.5 s p50 on this CPU and was not acceptable for mobile.

References:
- Jina reranker source model: https://huggingface.co/jinaai/jina-reranker-v2-base-multilingual
- DJL Hugging Face tokenizer runtime: https://djl.ai/extensions/tokenizers/

## Step 2 - llama.cpp JNI runtime adapter

Status: complete behind `RuntimeFeatureFlags.useLlamaCpp` (default off).

Changed:
- Preserved `LocalLlmRuntimeAdapter` and extended generation with optional grammar, sampler settings, and prompt-cache hints.
- Added `LocalModelRuntime` metadata so sidecar manifests can select `mediapipe` or `llama_cpp`.
- Added `LlamaCppRuntimeAdapter` with JNI bridge support for GGUF loading, CPU-only generation, prompt-cache prefix reuse, and GBNF grammar pass-through.
- Added native `scouty_llama_jni` build files under `app/src/main/cpp/`.
- Vendored ARM JNI libraries for `arm64-v8a` and `armeabi-v7a`.
- Updated `ModelManager` to scan `noBackupFilesDir/models/qwen-2.5-1.5b/` and external app storage for Qwen `.gguf` bundles when the llama.cpp flag is enabled.
- Added `tools/build_llama_jni.ps1` for reproducible JNI builds from a local llama.cpp checkout.
- Added `tools/push_qwen_to_device.ps1` to push the Qwen GGUF bundle and write a `runtime: llama_cpp` sidecar manifest.
- Added `LlamaCppRuntimeDebugTest` instrumentation smoke coverage for Qwen discovery, load, and Romanian generation through the `ModelManager` state machine.

Files touched:
- `app/src/androidTest/java/com/scouty/app/assistant/LlamaCppRuntimeDebugTest.kt`
- `app/src/main/cpp/CMakeLists.txt`
- `app/src/main/cpp/scouty_llama_jni.cpp`
- `app/src/main/java/com/scouty/app/assistant/domain/AssistantRuntimeGraph.kt`
- `app/src/main/java/com/scouty/app/assistant/domain/ModelManager.kt`
- `app/src/main/java/com/scouty/app/assistant/domain/runtime/LlamaCppRuntimeAdapter.kt`
- `app/src/main/jniLibs/arm64-v8a/libscouty_llama_jni.so`
- `app/src/main/jniLibs/armeabi-v7a/libscouty_llama_jni.so`
- `docs/local-llm-runtime.md`
- `tools/build_llama_jni.ps1`
- `tools/push_qwen_to_device.ps1`

Validation:
- `./gradlew.bat testDebugUnitTest` passes.
- `./gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest` passes.
- Emulator smoke validation used a temporary, uncommitted `x86_64` JNI build because the attached target was `sdk_gphone64_x86_64`.
- `adb shell am instrument -w -e class com.scouty.app.assistant.LlamaCppRuntimeDebugTest com.scouty.app.test/androidx.test.runner.AndroidJUnitRunner` passes: Qwen Q4_K_M GGUF discovered in `no_backup`, `ModelManager` transitions to `LOADED`, and generation returns non-empty Romanian text.

Artifacts:
- llama.cpp source revision used for JNI build: `db44417b027cff147f7de85e7da22bc6a3a804fb`.
- Qwen GGUF source URL: `https://huggingface.co/Qwen/Qwen2.5-1.5B-Instruct-GGUF/resolve/main/qwen2.5-1.5b-instruct-q4_k_m.gguf`.
- Qwen GGUF SHA-256: `6A1A2EB6D15622BF3C96857206351BA97E1AF16C30D7A74EE38970E434E9407E`.
- `arm64-v8a/libscouty_llama_jni.so` SHA-256: `6A9A114A90CC2415A1B6CC653F61F0AAD791422ED4BA5D5CC3A8A2436FE7B49F`.
- `armeabi-v7a/libscouty_llama_jni.so` SHA-256: `62D700744105E7879E43E8CC92C5782BDF89492377E1F18E1E18CDDD8A18DB21`.

Deviation:
- The attached validation target was x86_64, so a temporary emulator ABI library was built, installed, tested, and removed before commit. The committed ABI set remains `arm64-v8a` and `armeabi-v7a`, per the migration brief.
- Prompt-cache support is implemented as a single in-process prefix cache in the JNI context. Step 3 will provide stable system + summary cache keys so this can produce meaningful hits.
