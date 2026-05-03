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
