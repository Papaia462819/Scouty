# Scouty card_generator

Authoring pipeline for Romanian-language knowledge cards that feed the offline
`knowledge_pack.sqlite` shipped with the app. Two-tier output:

- **Tier A (strict)** — sourced, conservative, life-safety domains. Drafts go
  through `strict_ingest.py`, then **mandatory human review** before merge.
- **Tier B (conversational)** — LLM-authored from general knowledge in the voice
  of an experienced Romanian hiker. Drafts go through `conversational_gen.py`,
  then spot-check (10% per batch).

## Workflow

```
sources/strict/ ────────►  strict_ingest.py  ────►  drafts/strict/
                                                       │
seeds/conversational_topics_ro.jsonl ──► conversational_gen.py ──► drafts/conversational/
                                                       │
                                              validator.py (--strict / --conversational)
                                                       │
                                              ┌────────┴────────┐
                                              │ pass            │ fail
                                              ▼                 ▼
                                       approved/{tier}/    fix or drop
                                              │
                                              ▼
              ../knowledge_pipeline/build_knowledge_pack.py
                  --draft-source ../card_generator/approved/
                                              │
                                              ▼
                          knowledge_pack.sqlite + manifest
```

The card_generator's responsibility ends at producing **approved JSON drafts**.
Embedding, FTS, manifest, and SHA-256 are owned by the canonical builder in
`tools/knowledge_pipeline/`.

## Directories

| Path | Purpose |
| --- | --- |
| `sources/strict/` | Source PDFs/HTML/text for Tier A ingest, plus `manifest.json` |
| `drafts/strict/` | Pre-review Tier A drafts (one JSON per card) |
| `drafts/conversational/` | Pre-review Tier B drafts (one JSON per card) |
| `approved/strict/` | Reviewed Tier A drafts cleared to ship |
| `approved/conversational/` | Reviewed Tier B drafts cleared to ship |
| `seeds/` | Topic prompts used by `conversational_gen.py` |
| `regression/` | Romanian regression queries used by the engine team's gate |
| `prompts/` | LLM prompt templates (shared between strict and conversational) |

## Approved-draft schema

One JSON file per card. Filename `<chunk_id>.json`. Required keys mirror the
`knowledge_chunks` schema used by `build_knowledge_pack.py`. See
`schema.py` for the canonical definition.

## Running

```powershell
# Generate Tier B drafts from the seed list
python tools/card_generator/conversational_gen.py `
  --seeds tools/card_generator/seeds/conversational_topics_ro.jsonl `
  --output tools/card_generator/drafts/conversational `
  --batch-size 10

# Validate a batch
python tools/card_generator/validator.py `
  --tier B `
  --input tools/card_generator/drafts/conversational

# After review, copy approved drafts
# (manual step — moves files from drafts/ to approved/)

# Rebuild the pack with the new approved drafts
python tools/knowledge_pipeline/sync_knowledge_pack.py --skip-fetch
```

The extended `build_knowledge_pack.py` automatically picks up
`tools/card_generator/approved/{strict,conversational}/*.json`.
