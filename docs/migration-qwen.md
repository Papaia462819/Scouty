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
- Fixed 30-query Romanian campfire bench, now treated as preliminary smoke evidence rather than a statistical shipping gate:
  - baseline top-1 accuracy: 70.0%
  - Jina-reranked top-1 accuracy: 73.3%
  - delta: +3.3 percentage points
  - paired bootstrap 95% CI for delta: [0.0, 10.0] percentage points
  - statistically powered: false
  - rerank p50: about 40-42 ms on the local CPU runs
  - rerank p95: about 47-55 ms on the local CPU runs

Deviation:
- Jina's current official `model_int8.onnx` is 279,577,152 bytes, not ~130 MB. The tokenizer is 17,082,734 bytes. This still keeps the projected Qwen + reranker footprint inside the ~1.5 GB target.
- The p50 gate is only met with top-3 candidates and a 96-token cross-encoder window. Top-10 at 512 tokens measured roughly 1.5 s p50 on this CPU and was not acceptable for mobile.
- The 30-query reranker bench is statistically thin. The benchmark harness now reports paired bootstrap confidence intervals and supports `--require-statistical-gate`, but a real ship/no-ship decision still needs at least 100 labelled Romanian queries with CI lower bound above 0.

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

## Step 3 - conversation memory store

Status: complete behind `RuntimeFeatureFlags.useConversationMemory` (default off). `RuntimeFeatureFlags.useLlmSummarizer` remains default off; compaction is deterministic.

Changed:
- Added `ConversationStore` backed by `noBackupFilesDir/conversations.sqlite` with the required `conversations` and `turns` tables.
- Added the required store API: `appendTurn`, `loadRecent`, `loadSummary`, and `updateSummary`.
- Added repository-only helpers for conversation creation, full-turn loading, pruning, and reset.
- Added `ConversationContextAssembler` to build a Romanian prompt context from static persona rules, summary, recent raw turns, structured `AssistantConversationState`, device/trail context, and the current query.
- Added prompt-cache hints keyed by SHA-256 of the stable system + summary prefix.
- Added `SummaryCompactor`; it triggers after more than 12 turns when summary + history exceed budget, writes deterministic Romanian bullets, and keeps the last 4 raw turns.
- Wired `AssistantRepository` to append user turns before retrieval and assistant turns after response generation when memory is enabled.
- Threaded conversation history into both `LocalLlmGenerationEngine` and the campfire `GroundedWordingEngine` so prompt-cache hints and prior turns are available to local generation.
- Added `AssistantViewModel.resetConversation()` without changing layout.
- Added diagnostics fields: `history_tokens_sent`, `summary_compaction_count`, `prefix_key_stability_rate`, and `recent_turn_count`.

Files touched:
- `app/src/androidTest/java/com/scouty/app/assistant/ConversationStoreInstrumentedTest.kt`
- `app/src/main/java/com/scouty/app/assistant/data/ConversationStore.kt`
- `app/src/main/java/com/scouty/app/assistant/diagnostics/AssistantDiagnostics.kt`
- `app/src/main/java/com/scouty/app/assistant/domain/AssistantRepository.kt`
- `app/src/main/java/com/scouty/app/assistant/domain/AssistantRuntimeGraph.kt`
- `app/src/main/java/com/scouty/app/assistant/domain/InterpretationPipeline.kt`
- `app/src/main/java/com/scouty/app/assistant/domain/LocalLlmGenerationEngine.kt`
- `app/src/main/java/com/scouty/app/assistant/domain/memory/ConversationContextAssembler.kt`
- `app/src/main/java/com/scouty/app/assistant/domain/memory/SummaryCompactor.kt`
- `app/src/main/java/com/scouty/app/assistant/ui/AssistantViewModel.kt`
- `docs/migration-qwen.md`

Validation:
- `./gradlew.bat testDebugUnitTest` passes.
- `./gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest` passes.
- Emulator persistence/context validation used a temporary, uncommitted `x86_64` JNI build because the attached target was `sdk_gphone64_x86_64`.
- `adb shell am instrument -w -e class com.scouty.app.assistant.ConversationStoreInstrumentedTest com.scouty.app.test/androidx.test.runner.AndroidJUnitRunner` passes: 2 tests.
- Instrumentation coverage verifies:
  - turns and summary survive `ConversationStore` recreation;
  - deterministic compaction prunes to the last 4 raw turns;
  - the compacted summary and assembled context keep the earlier "lemne ude" topic available for a later recall query.

Deviation:
- I did not mark this as a full qualitative 20-turn Qwen conversation bench. Step 3 now preserves and injects the required memory context; subjective answer quality depends on Step 4 Qwen defaulting and Step 5 expression-layer routing.
- The schema remains the requested two-table shape. Structured state is not persisted as a third table or JSON column; it is injected from the existing `AssistantConversationState` and reinforced into deterministic summaries during compaction.

## Step 4 - Qwen 2.5 1.5B-Instruct default for llama.cpp

Status: complete for the gated llama.cpp path. When `RuntimeFeatureFlags.useLlamaCpp = true`, `ModelManager` prefers Qwen `.gguf` bundles from `models/qwen-2.5-1.5b/`.

Changed:
- Added adapter-side Qwen ChatML formatting in `LlamaCppRuntimeAdapter`.
- Kept call sites prompt-template agnostic: normal generation paths pass plain prompt text, and the llama.cpp adapter wraps Qwen prompts.
- Preserved debug compatibility for already formatted ChatML prompts by passing them through unchanged.
- Transformed prompt-cache prefixes through the same ChatML wrapper so Step 3 system + summary cache hints still match the real llama.cpp token stream.
- Updated `LlamaCppRuntimeDebugTest` to send a plain Romanian prompt instead of hand-written ChatML.
- Updated `docs/local-llm-runtime.md` with the Qwen URL, SHA-256, size, and ChatML behavior.

Files touched:
- `app/src/androidTest/java/com/scouty/app/assistant/LlamaCppRuntimeDebugTest.kt`
- `app/src/main/java/com/scouty/app/assistant/domain/runtime/LlamaCppRuntimeAdapter.kt`
- `docs/local-llm-runtime.md`
- `docs/migration-qwen.md`

Validation:
- `./gradlew.bat testDebugUnitTest` passes.
- `./gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest` passes.
- Emulator Qwen smoke validation used a temporary, uncommitted `x86_64` JNI build because the attached target was `sdk_gphone64_x86_64`.
- `adb shell am instrument -w -e class com.scouty.app.assistant.LlamaCppRuntimeDebugTest com.scouty.app.test/androidx.test.runner.AndroidJUnitRunner` passes.
- Smoke prompt: `Cum aprind un foc cu lemne ude?`
- Result gate: Qwen Q4_K_M loads through `ModelManager`, transitions to `LOADED`, and returns non-empty Romanian fire-starting text from a plain prompt that the adapter wraps with ChatML.

Artifacts:
- Qwen GGUF source URL: `https://huggingface.co/Qwen/Qwen2.5-1.5B-Instruct-GGUF/resolve/main/qwen2.5-1.5b-instruct-q4_k_m.gguf`.
- Qwen GGUF SHA-256: `6A1A2EB6D15622BF3C96857206351BA97E1AF16C30D7A74EE38970E434E9407E`.
- Qwen GGUF size: `1117320736` bytes.

Deviation:
- The requested 20-prompt Qwen-vs-Gemma fluency bench was not a true side-by-side run because the local emulator has the Qwen GGUF installed but no Gemma MediaPipe bundle. Qwen generation was smoke-validated; Gemma comparison remains a bench task once a Gemma bundle is present on the same target.
- Grammar-constrained JSON validity is supported by Step 2 GBNF pass-through, but the 20-prompt grammar bench belongs with Step 6 tool-calling grammar coverage.

## Step 5 gate check - expression-layer inversion

Status: blocked by knowledge-pack coverage. No Step 5 implementation was started.

Gate command:

```powershell
@'
import sqlite3
conn = sqlite3.connect('app/src/main/scouty_assets/knowledge_pack.sqlite')
cur = conn.cursor()
for label, where in {
    'campfire_basics': "domain='campfire_basics'",
    'medical_emergency + mountain_safety': "domain in ('medical_emergency','mountain_safety')",
    'wildlife_romania': "domain='wildlife_romania'",
    'weather_and_season': "domain='weather_and_season'",
}.items():
    cur.execute(f"select count(*) from knowledge_chunks where {where}")
    print(label, cur.fetchone()[0])
conn.close()
'@ | python -
```

Observed coverage:
- `campfire_basics`: 0 cards. The current campfire content is `field_know_how/topic=campfire` with 30 cards, still below the requested 50-card campfire gate and under a different domain name.
- `medical_emergency + mountain_safety`: 10 cards, below the requested 100 cards.
- `wildlife_romania`: 4 cards, below the requested 80-card alternative.
- `weather_and_season`: 4 cards, below the requested 80-card alternative.

Decision:
- Halted before Step 5 as required by the implementation brief.
- Did not run the 60-query human-judged retrieval appropriateness gate because the domain-count gate already fails decisively.
- Content generation/card authoring is out of scope for this migration pass and belongs to the separate content workstream.

## Step 5 - expression-layer inversion (Tier B only)

Status: implemented behind `RuntimeFeatureFlags.useCardParaphraseExpression = false`. Tier B conversational cards can now be routed through a local Qwen paraphrase layer when the flag is explicitly enabled. Tier A/strict cards still short-circuit to the existing response path.

Pre-flight:
- Commit `2740110`: fixed Romanian diacritics in the Qwen/runtime memory prompts, renamed misleading cache-hit telemetry to `prefix_key_stability_rate`, removed stale summary text, and landed the low-risk runtime/store cleanup from the review notes.

Changed:
- Extended `tools/knowledge_pipeline/build_knowledge_pack.py` so `--draft-source` can be repeated. The build now ingests both `tools/card_generator/approved/conversational` and `tools/card_generator/approved/strict`.
- Normalized draft `tier` into `metadata_json` without adding a new SQLite column.
- Generated `card_embeddings` rows for every `knowledge_chunks` row that did not already have the legacy campfire embedding, eliminating partial embedding coverage for Tier B domains.
- Added `domain/expression/CardParaphraseEngine.kt`.
  - It paraphrases only when the feature flag is on, the retrieved card has `metadata_json.tier == "B"`, and retrieval confidence is `HIGH` or `MEDIUM`.
  - It builds a Romanian prompt from conversation context, device context, the verbatim card body, and the current query.
  - It calls the local runtime with `temperature=0.4`, `top_p=0.9`, `max_tokens=180`, passing through the conversation prompt-cache hint.
  - It rejects blank/oversized output and falls back if numeric/named/lead key-fact coverage is below 70%.
- Wired the expression layer into `AssistantRepository` before the template/grounded wording paths.
  - Standard retrieval uses the current top-1 retrieved card.
  - The legacy campfire lane gets a flag-gated Tier B lookup against `campfire_basics` so the new approved conversational cards can be tested without changing flag-off behavior.
- Added diagnostics fields in `AssistantDiagnostics`: `expression_invocation_count`, `expression_fallback_count`, `expression_token_latency_ms`, and `expression_skipped_tier_a_count`.
- Added the 60-query Tier B bench files:
  - `tools/benchmarks/expression_ro_queries.json`
  - `tools/benchmarks/bench_expression_layer.py`
  - `tools/benchmarks/expression_ro_results.json`

Files touched:
- `app/src/main/java/com/scouty/app/assistant/diagnostics/AssistantDiagnostics.kt`
- `app/src/main/java/com/scouty/app/assistant/domain/AssistantRepository.kt`
- `app/src/main/java/com/scouty/app/assistant/domain/AssistantRuntimeGraph.kt`
- `app/src/main/java/com/scouty/app/assistant/domain/CampfireConversationEngine.kt`
- `app/src/main/java/com/scouty/app/assistant/domain/expression/CardParaphraseEngine.kt`
- `app/src/test/java/com/scouty/app/assistant/domain/AssistantRepositoryIntegrationTest.kt`
- `app/src/test/java/com/scouty/app/assistant/domain/expression/CardParaphraseEngineTest.kt`
- `app/src/main/scouty_assets/knowledge_pack.sqlite`
- `app/src/main/scouty_assets/knowledge_pack_manifest.json`
- `tools/knowledge_pipeline/build_knowledge_pack.py`
- `tools/benchmarks/bench_expression_layer.py`
- `tools/benchmarks/expression_ro_queries.json`
- `tools/benchmarks/expression_ro_results.json`

Knowledge-pack build:

```powershell
python tools\knowledge_pipeline\build_knowledge_pack.py `
  --draft-source tools\card_generator\approved\conversational `
  --draft-source tools\card_generator\approved\strict
```

Observed build output:
- Approved drafts merged: 398 cards across 11 domains.
- Tier B conversational cards: 388.
- Tier A strict cards: 10.
- `campfire_basics=61`, `gear_and_preparation=77`, `tips_and_tricks=58`, `survival_basics=49`.
- Total `knowledge_chunks`: 2146.
- Total `card_embeddings`: 2146.
- Embedding orphans: 0.
- `metadata_json.tier/tone`: `B/conversational=388`, `A/strict=10`.
- Manifest/database SHA-256: `dd17ca435f1972ac84cf9a084253f70f3e233978915bdcc6f86f65d24705feb7`.

Validation:
- `./gradlew.bat testDebugUnitTest` passes.
- `CardParaphraseEngineTest` covers Tier A skip, LOW-confidence skip, sanitization rejection, faithfulness rejection, successful pass-through, and key-fact extraction.
- `AssistantRepositoryIntegrationTest.tierBExpressionFlag_usesParaphraseBeforeTemplate` verifies that the flag-on path returns a Tier B paraphrase before the template path.
- `python tools\benchmarks\bench_expression_layer.py --json-out tools\benchmarks\expression_ro_results.json` ran on the rebuilt pack.

Bench result:
- Query count: 60.
- Retrieval top-1 appropriateness: 100% on the title/lead-seeded Tier B bench.
- Diacritic correctness on simulated ideal answers: 100%.
- Faithfulness pass rate on simulated ideal answers: 43.3%.
- Jina reranker host-side p50/p95: 1048.9 ms / 1130.0 ms in Python ONNXRuntime. This is not comparable to the Android Step 1 JNI/runtime latency gate.
- Qwen expression latency p50/p95: not measured in this host bench.

Deviation:
- I used `ModelManager.generate(...)` directly from `CardParaphraseEngine` rather than `LocalLlmGenerationEngine`. `LocalLlmGenerationEngine` is still the legacy structured-JSON generator, so routing a free-form paraphrase through it would reintroduce the JSON meta-task Step 5 is explicitly removing.
- The 60-query bench is generated from approved card titles/leads, not an independent human-judged regression set. It is useful as a deterministic smoke gate, but it is optimistic for retrieval appropriateness.
- The host bench validates retrieval and the deterministic faithfulness checker only. It does not validate Qwen output quality or p95 expression latency through llama.cpp on a device.
- The simulated `ideal_answer` strings are intentionally short leads, so the 70% card-body key-fact checker rejects many of them. That is a checker/content mismatch, not evidence that Qwen passed or failed.
- Tier A paraphrasing remains blocked until strict coverage reaches the original gate. Future work should land as "Step 5b - Tier A paraphrasing" after strict ingest adds the missing medical/mountain-safety cards.

Decision:
- Do not flip `useCardParaphraseExpression` default yet.
- The code path is ready for opt-in device validation with Qwen, but the Step 5 ship gate is not fully met until Qwen expression p95 and real generated faithfulness are measured on the dev target.

## Step 6 - grammar-constrained tool calling

Status: implemented behind `RuntimeFeatureFlags.useGrammarToolCalling = false`. The legacy interpreter remains available behind `useLegacyInterpreter = true`; when grammar tool-calling is enabled, it gets first chance on ambiguous/low-confidence requests.

Changed:
- Added `domain/tools/AssistantTools.kt`.
  - Defines the closed tool catalog: `lookup_card`, `set_gear_packed`, `check_capability`, `ask_clarification`, `recall_previous`, and `respond_directly`.
  - Adds `GrammarToolCallPlanner`, which calls the local model with a GBNF grammar and `temperature=0`.
  - Adds `ToolCallParser`, which accepts only known tool names, known domains, known metrics, and known campfire slot keys/values.
- Added `domain/tools/tool_call.gbnf` and mirrored the same grammar in `ToolCallGrammar.Text` for runtime use.
- Added `domain/tools/ToolDispatcher.kt`.
  - `lookup_card` reruns deterministic retrieval with a domain hint and optional slot filters, then returns chunks to the normal answer/paraphrase path.
  - `ask_clarification` creates a Romanian clarification response and updates `AssistantConversationState.openQuestion`.
  - `recall_previous` searches the assembled conversation history/summary and returns a deterministic Romanian recall response.
  - `set_gear_packed` emits `AssistantAction.ToggleGearPacked`.
  - `check_capability` answers duration/elevation/weather from active trail context.
  - `respond_directly` lets the existing answer path continue.
- Wired `AssistantRepository` so tool-calling triggers only when:
  - retrieval confidence score is `< 0.55`, or
  - there is an unresolved `openQuestion`.
- Removed the regex-based partial JSON repair path from `LocalLlmGenerationEngine`. Invalid structured JSON now falls back to the template engine; ambiguous interpretation belongs to Step 6 tool-calling.
- Added diagnostics via `AssistantDiagnostics.logToolCalling`: `tool_call_invocation_count`, `tool_name`, `tool_call_latency_ms`, status, and error.
- Added a 50-query Romanian tool-call schema corpus and parser bench:
  - `tools/benchmarks/tool_call_ro_queries.json`
  - `tools/benchmarks/bench_tool_call_parser.py`
  - `tools/benchmarks/tool_call_ro_results.json`

Files touched:
- `app/src/main/java/com/scouty/app/assistant/diagnostics/AssistantDiagnostics.kt`
- `app/src/main/java/com/scouty/app/assistant/domain/AssistantRepository.kt`
- `app/src/main/java/com/scouty/app/assistant/domain/AssistantRuntimeGraph.kt`
- `app/src/main/java/com/scouty/app/assistant/domain/LocalLlmGenerationEngine.kt`
- `app/src/main/java/com/scouty/app/assistant/domain/tools/AssistantTools.kt`
- `app/src/main/java/com/scouty/app/assistant/domain/tools/ToolDispatcher.kt`
- `app/src/main/java/com/scouty/app/assistant/domain/tools/tool_call.gbnf`
- `app/src/test/java/com/scouty/app/assistant/domain/AssistantRepositoryIntegrationTest.kt`
- `app/src/test/java/com/scouty/app/assistant/domain/tools/AssistantToolsTest.kt`
- `tools/benchmarks/bench_tool_call_parser.py`
- `tools/benchmarks/tool_call_ro_queries.json`
- `tools/benchmarks/tool_call_ro_results.json`

Validation:
- `./gradlew.bat testDebugUnitTest` passes.
- `./gradlew.bat :app:assembleDebug` passes.
- `AssistantToolsTest` verifies all tool JSON shapes, rejects unknown tools/invalid slot values, and checks that the grammar lists the known campfire slot keys.
- `AssistantRepositoryIntegrationTest.grammarToolCalling_lowConfidenceCanAskClarification` verifies that a low-confidence query can route through the grammar planner to `ask_clarification` and persist `openQuestion`.
- `python tools\benchmarks\bench_tool_call_parser.py --json-out tools\benchmarks\tool_call_ro_results.json` passes:
  - query count: 50
  - valid JSON/catalog count: 50
  - valid rate: 100%

Deviation:
- The 50-query Step 6 bench validates the schema/catalog/parser corpus, not actual Qwen generation on device. A real Qwen grammar run still needs to be executed on the Android target with `useLlamaCpp=true` and `useGrammarToolCalling=true`.
- `tool_call.gbnf` is duplicated as `ToolCallGrammar.Text` because Android source packaging does not give this module a simple runtime classpath loader for a raw `.gbnf` file in `java/`. The file remains present for review and parity.
- The legacy interpreter is not deleted; it is bypassed when grammar tool-calling succeeds and remains as a gated fallback during migration.

Conversational readiness:
- The app now has the intended conversational skeleton: deterministic retrieval/rerank, memory, Qwen expression layer for Tier B, and grammar-constrained tool routing for ambiguous turns.
- It is not yet safe to call the full conversational model "production-ready" because the two Qwen-dependent gates remain unmeasured on device: Step 5 real paraphrase faithfulness/latency and Step 6 real 50-query GBNF validity.
- Recommended flag state remains default off for `useCardParaphraseExpression` and `useGrammarToolCalling` until those device gates pass.
