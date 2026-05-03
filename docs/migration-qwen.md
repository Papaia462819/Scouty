# Qwen migration notes

## Step 1 - Cross-encoder reranker

Status: code scaffolded and feature-flagged, not validated as complete.

Changed:
- Added `RuntimeFeatureFlags` in `AssistantRuntimeGraph.kt`; `useCrossEncoderReranker` defaults to `false`.
- Added `CrossEncoderReranker` backed by ONNX Runtime + Hugging Face tokenizer assets.
- Wired the optional reranker into `CampfireConversationEngine` after deterministic scoring and before confidence assessment.
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
- `tools/benchmarks/reranker_ro_queries.json`

Validation:
- `./gradlew.bat testDebugUnitTest` passes: 134 tests.
- Reranker benchmark not run. The model assets are not present under `app/src/main/scouty_assets/ml/`.

Deviation:
- The available int8 ONNX artifact for `bge-reranker-v2-m3` is 569,705,899 bytes, plus a 17,082,900 byte tokenizer. This conflicts with the prompt's "~120 MB" assumption and puts the later Qwen + reranker footprint above the stated ~1.5 GB target.
- Because the asset is absent and the size/latency gate is unresolved, Step 1 is not "done" by the requested p50/top-1 accuracy criteria. Step 2 is blocked until the reranker asset choice is corrected or explicitly accepted.

References:
- BGE reranker source model: https://huggingface.co/BAAI/bge-reranker-v2-m3
- ONNX int8 artifact inspected: https://huggingface.co/tss-deposium/bge-reranker-v2-m3-onnx-int8
- DJL Hugging Face tokenizer runtime: https://djl.ai/extensions/tokenizers/
