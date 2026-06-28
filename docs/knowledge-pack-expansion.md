# Knowledge Pack Expansion

## Scop

Pack-ul de cunoștințe (`knowledge_pack.sqlite`) este alimentat din mai multe
surse coexistente:

- **curated_chunks** — conținut hand-rescris în `tools/knowledge_pipeline/curated_chunks.json`
- **campfire_cards** — carduri scenariu/definiție/constraint în `tools/knowledge_pipeline/campfire_cards.json`
- **route chunks** — generate automat din `local_route_enriched_catalog.json`
- **drafts** *(nou)* — carduri produse de pipeline-ul `tools/card_generator/`

Acest document descrie pipeline-ul nou de carduri și relația cu builder-ul
canonic.

## Filosofia celor două straturi

| Tier | Domenii principale | Voce | Surse |
| --- | --- | --- | --- |
| **A — strict** | medical_emergency, mountain_safety, subset wildlife/weather | Imperativă, conservatoare, fără umor. Citează 0SALVAMONT/112 când e cazul. | Salvamont, ANM, CDC, WHO, NPS, IFRC. `source_url` obligatoriu, `source_trust >= 4`. |
| **B — conversational** | campfire_basics, gear_and_preparation, survival_basics, route_intelligence_romania, trail_culture_ro, tips_and_tricks, motivation_and_morale, subset wildlife/weather | Drumeț prieten, persoana întâi plural, umor sec ocazional, exemple concrete. | LLM-authored din cunoștințe generale. `source_trust = 2`, `publisher = "Scouty Knowledge Team"`. |

Cardurile Tier B nu pot da sfaturi medicale și nu pot descuraja apelul la
ajutor — validator-ul aplică această regulă (vezi `TIER_B_FORBIDDEN_PHRASES`
în `tools/card_generator/schema.py`).

## Pipeline

```
sources/strict/ ───► strict_ingest.py ───► drafts/strict/
                                              │
seeds/conversational_topics_ro.jsonl ──► conversational_gen.py ──► drafts/conversational/
                                              │
                                     validator.py (--tier A | --tier B)
                                              │
                            review uman (Tier A: obligatoriu;
                            Tier B: spot-check 10% per batch)
                                              │
                              approved/{strict,conversational}/
                                              │
                tools/knowledge_pipeline/build_knowledge_pack.py
                  --draft-source ../card_generator/approved/  (default)
                                              │
                          knowledge_pack.sqlite + manifest
```

## Domenii și ținte de acoperire

Țintele inițiale pentru extinderea pachetului local:

| Domain | Tier | Țintă |
| --- | --- | --- |
| medical_emergency | A | 60 |
| mountain_safety | A | 50 |
| wildlife_romania (subset periculos) | A | 20 |
| weather_and_season (subset hazard) | A | 25 |
| campfire_basics | B | 60 |
| gear_and_preparation | B | 80 |
| survival_basics (non-medical) | B | 50 |
| wildlife_romania (non-periculos) | B | 30 |
| weather_and_season (general) | B | 30 |
| route_intelligence_romania (caracter) | B | 20+ |
| trail_culture_ro | B | 40 |
| tips_and_tricks | B | 60 |
| motivation_and_morale | B | 20 |

**Gate prioritar (engine team Step 5):** `campfire_basics ≥ 50`,
`medical_emergency + mountain_safety ≥ 100`. Ordinea recomandată de execuție.

## Componente

### Generatoare de drafturi

- `tools/card_generator/strict_ingest.py` — Tier A. Citește
  `sources/strict/manifest.json` + fișierele sursă (HTML/PDF/text), trimite
  ferestre de text către LLM cu prompt-ul strict din
  `prompts/strict_system_ro.txt`, scrie drafturi în `drafts/strict/`. Dacă
  sursa nu susține direct un topic, modelul răspunde
  `{"insufficient_evidence": true, ...}` și nu se scrie nimic.
- `tools/card_generator/conversational_gen.py` — Tier B. Citește
  `seeds/conversational_topics_ro.jsonl`, trimite fiecare topic la LLM cu
  prompt-ul din `prompts/conversational_system_ro.txt`, scrie drafturi în
  `drafts/conversational/`. Resumable: sare topicurile deja drafted dacă nu
  trimiți `--force`. Dacă LLM-ul detectează o margine medicală/de siguranță,
  răspunde `{"escalate": "tier_a", "reason": "..."}` și topicul se loggează
  separat (`--escalate-log`) în loc să producă un draft Tier B.

Ambele scripturi acceptă `--dry-run` care emite stub-uri deterministe fără
apel API — util pentru testarea pipeline-ului fără cheie.

### Validator

`tools/card_generator/validator.py` rulează 16 verificări:

- Schema completness (chei obligatorii, chei necunoscute = warn)
- Tier vs domeniu (Tier A doar pe domeniile A; Tier B doar pe B)
- Limba `ro` + euristică pentru diacritice
- Lungime body per tier (Tier A 80-300 cuvinte, Tier B 40-180)
- Tier A: `source_trust >= 4`, `source_url` prezent, `metadata.safety_critical = true`
- Tier B: `source_trust == 2`, publisher fix, `safety_critical = false`,
  fără fraze care descurajează apelul la ajutor
- Dedup împotriva pack-ului existent (chunk_id, title hash, body hash 160 chars)
- Filtru PII (CNP, telefoane non-emergency, email-uri) și conținut politic
- Format dată `YYYY-MM-DD`

Cod de ieșire: 0 dacă toate drafturile trec, 1 dacă există erori. Warning-urile
sunt informaționale.

### Builder extension

`tools/knowledge_pipeline/build_knowledge_pack.py` a primit:

- `--draft-source <dir>` (default: `tools/card_generator/approved/`)
- `--no-drafts` pentru a sări peste merge când e nevoie
- Funcție nouă `expand_draft_chunks()` care citește recursiv `*.json` din dir,
  validează cheile minimale și produce row-uri pentru `knowledge_chunks` +
  `knowledge_chunks_fts`
- Câmpuri noi în manifest: `draft_chunk_count`, `draft_chunk_sha256`,
  `draft_domain_counts`

Domenii noi (`campfire_basics`, `trail_culture_ro`, `tips_and_tricks`,
`motivation_and_morale`) nu sunt incluse în `REQUIRED_DOMAINS` — build-ul nu
eșuează dacă lipsesc, dar drafturile aprobate care le folosesc sunt acceptate.

## Schema draft

Fiecare draft aprobat este un fișier JSON cu cheile din
`tools/card_generator/schema.py`:

```jsonc
{
  "chunk_id": "cg_<domain>_<topic_slug>_<sha12>",
  "card_id": "<same as chunk_id by default>",
  "domain": "campfire_basics",
  "topic": "fire_wet_wood",
  "language": "ro",
  "title": "Aprins focul când lemnele sunt ude",
  "body": "...40-180 cuvinte (Tier B) sau 80-300 (Tier A)...",
  "lead": "rezumat scurt sub 160 chars",
  "keywords": "lemn ud foc ploaie mesteacan",
  "card_family": "SCENARIO" | "DEFINITION" | "CONSTRAINT",
  "priority": 0..100,
  "tier": "A" | "B",
  "source_title": "...",
  "source_url": "https://...",
  "publisher": "...",
  "source_language": "ro" | "en",
  "adapted_language": "ro",
  "publish_or_review_date": "YYYY-MM-DD",
  "source_trust": 1..5,
  "safety_tags": ["..."],
  "country_scope": "ro" | "global",
  "metadata_json": {
    "safety_critical": true | false,
    "tone": "strict" | "conversational",
    "lead": "...",
    "evidence_confidence": "high" | "medium" | "low",  // Tier A
    "source_id": "..."  // Tier A
  }
}
```

Numele de fișier: `<chunk_id>.json` în
`approved/{strict,conversational}/`.

## Workflow review

- **Tier A:** review uman obligatoriu pe toate drafturile înainte de mutare în
  `approved/strict/`. Reviewer-ul verifică fidelitatea față de sursă, tonul
  imperativ, prezența mențiunii 0SALVAMONT/112 unde e cazul.
- **Tier B:** spot-check 10% per batch. Dacă spot-check-ul găsește mai mult de
  20% probleme, se respinge tot batch-ul și se rulează din nou cu prompt
  ajustat sau temperature mai mică.

Drafturile aprobate se mută din `drafts/{tier}/` în `approved/{tier}/`. Cele
respinse rămân în `drafts/{tier}/` pentru reluare sau se șterg.

## Carduri borderline (HALT condition)

Topicurile care sunt aparent Tier B dar au o margine medicală/de siguranță
trebuie semnalate înainte de generare. Exemple:

- "ar trebui să beau apă din izvor de munte?" — risc *E. coli*, trebuie Tier A
- "cum tratez o entorsă pe traseu?" — Tier A
- "ce mănânc dacă rămân fără energie?" — Tier B (energetic), DAR nu trebuie
  să atingă hipoglicemie clinică (Tier A)

LLM-ul Tier B este instruit să răspundă cu
`{"escalate": "tier_a", "reason": "..."}` la astfel de topicuri. Fișierul
`--escalate-log` produs de `conversational_gen.py` adună aceste cazuri pentru
ca echipa să le promoveze manual la Tier A.

## Deviații de la brief

Următoarele sunt diferențe față de brief-ul inițial de extindere,
intenționate pentru a integra cu pipeline-ul existent:

| Brief | Implementare actuală |
| --- | --- |
| `tools/card_generator/build_pack.py` | Eliminat. Builder canonic
  `tools/knowledge_pipeline/build_knowledge_pack.py` extins cu
  `--draft-source`. |
| `tools/card_generator/embedder.py` | Eliminat. Embedding-ul aparține
  builder-ului canonic. (Drafturile actuale nu primesc embedding-uri în
  `card_embeddings`; sunt retrieved exclusiv prin FTS, ca majoritatea
  curated_chunks.) |
| `multilingual-e5-small` | `sentence-transformers/all-MiniLM-L6-v2` (384-dim,
  identic ca dimensiune; păstrat pentru compatibilitate cu rândurile existente
  `card_embeddings`). |
| `tools/card_generator/seeds/conversational_topics_ro.txt` | `.jsonl` în loc
  de `.txt` — fiecare linie e un obiect cu domain, topic, topic_slug, opțional
  card_family/priority/safety_tags. |

## Status batch curent (2026-05)

Batch demo end-to-end:

| Domain | Tier | Cards merged |
| --- | --- | --- |
| medical_emergency | A | 5 |
| mountain_safety | A | 3 |
| weather_and_season (hazard) | A | 1 |
| wildlife_romania (periculos) | A | 1 |
| campfire_basics | B | 2 |
| gear_and_preparation | B | 1 |
| survival_basics | B | 1 |
| weather_and_season (general) | B | 1 |
| wildlife_romania (non-periculos) | B | 1 |
| route_intelligence_romania | B | 1 |
| trail_culture_ro | B | 1 |
| tips_and_tricks | B | 1 |
| motivation_and_morale | B | 1 |
| **Total** | | **20** |

Pack: 1748 → 1768 chunks. Toate domeniile required încă acoperite, plus 4
domenii noi acceptate.

Surse Tier A folosite în acest batch: `salvamont_recomandari`,
`salvamont_turist_montan`, `salvamont_martor_accident`, `who_snakebite_facts`,
`cdc_heat_illnesses`, `cdc_lightning_safety`, `anm_avalanche_scale`,
`nps_bear_safety`. Toate aveau deja entry-uri valide în
`tools/knowledge_pipeline/sources.json`.

Topicuri flagged ca borderline-safety (de discutat înainte de a continua):
*(niciunul în acest batch demo)*.

## Pași următori

1. Rulare prima rundă reală Tier B: `python tools/card_generator/conversational_gen.py
   --seeds seeds/conversational_topics_ro.jsonl --batch-size 20`. Cu cele 390
   de topice seed se obține un draft pe campfire_basics, gear_and_preparation,
   etc.
2. Spot-check 10% pe rezultatul de mai sus.
3. Promovarea drafturilor aprobate în `approved/conversational/`.
4. Rulare Tier A peste sursele Salvamont/ANM (deja fetched în `tools/knowledge_pipeline/cache/normalized/`):
   copiere/symlink în `tools/card_generator/sources/strict/`, completare
   `manifest.json`, rulare `python tools/card_generator/strict_ingest.py`.
5. Review uman pe Tier A.
6. `python tools/knowledge_pipeline/sync_knowledge_pack.py --skip-fetch`
   pentru build final cu toate drafturile aprobate.
