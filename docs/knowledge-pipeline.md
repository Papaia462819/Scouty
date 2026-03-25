# Knowledge Pipeline

## Scop

Pipeline-ul din `tools/knowledge_pipeline/` produce knowledge pack-ul offline folosit de assistant:

- `app/src/main/scouty_assets/knowledge_pack.sqlite`
- `app/src/main/scouty_assets/knowledge_pack_manifest.json`

Pack-ul este versionat, auditable și pregătit pentru retrieval local pe telefon.

## Surse aprobate

Registry-ul complet este în `tools/knowledge_pipeline/sources.json`.

Familiile obligatorii de surse acoperite în repo:

- Medical / safety
  - IFRC First Aid Guidelines
  - WHO snakebite
  - CDC lightning safety
  - CDC altitude
  - CDC heat illnesses
  - EPA emergency water disinfection
- Romania / mountain safety
  - Salvamont România
  - ANM / Meteo România
- General hiking / survival
  - NPS Outdoor Emergency Plan
  - NPS hiking safety
  - NPS bear safety
  - NPS campfire guidance
- Route intelligence local
  - `app/src/main/scouty_assets/local_route_enriched_catalog.json`
  - `app/src/main/scouty_assets/local_route_geometry_index.json`

## Reguli de ingestie

Pipeline-ul actual respectă următoarele constrângeri:

- nu păstrează sursele brute ca truth pentru runtime-ul mobil
- nu traduce la runtime
- nu livrează dump-uri brute în aplicație
- exportă chunk-uri curate, rescrise și auditable
- păstrează metadata către sursa originală
- exportă variante RO și EN pentru chunk-urile curate
- importă explicit metadata de traseu din catalogul local

## Domenii acoperite

Pack-ul validează prezența acestor domenii:

- `medical_emergency`
- `mountain_safety`
- `survival_basics`
- `wildlife_romania`
- `weather_and_season`
- `route_intelligence_romania`
- `gear_and_preparation`

Build-ul eșuează dacă lipsește unul dintre ele.

## Schema minimă pe chunk

În SQLite, fiecare chunk exportat include cel puțin:

- `chunk_id`
- `domain`
- `topic`
- `language`
- `title`
- `body`
- `source_title`
- `source_url`
- `publisher`
- `source_language`
- `adapted_language`
- `publish_or_review_date`
- `source_trust`
- `safety_tags`
- `country_scope`
- `pack_version`

Pe lângă acestea, pipeline-ul mai exportă `keywords` pentru retrieval lexical.

## Surse de conținut folosite în build

### Chunk-uri curate

`tools/knowledge_pipeline/curated_chunks.json` este locul editabil pentru chunk-urile rescrise și traduse. Fiecare intrare leagă chunk-ul de un `source_id` din registry, cu topic, domeniu, safety tags și text RO/EN.

### Trasee locale

`build_knowledge_pack.py` citește `local_route_enriched_catalog.json` și generează chunk-uri `route_intelligence_romania` pentru fiecare traseu, folosind explicit:

- nume traseu
- regiune
- descriere / `local_description`
- difficulty
- duration
- distance
- ascent / descent
- `symbols`
- `from` / `to`
- `source_urls`
- provenance din Muntii Noștri / OSM, unde există

Marcajele sunt citite din `symbols`, nu deduse din descrieri libere.

## Output-uri

### `knowledge_pack.sqlite`

Conține:

- `knowledge_chunks`
- `knowledge_chunks_fts`
- `metadata`

### `knowledge_pack_manifest.json`

Conține:

- `pack_version`
- `generated_at`
- hash-ul SQLite
- număr de chunk-uri
- coverage pe domenii
- sursele folosite
- hash-uri pentru registry și chunk-uri curate
- metadata despre catalogul local de trasee

## Regenerare

Din rădăcina repo-ului:

```powershell
python tools/knowledge_pipeline/sync_knowledge_pack.py
```

Pipeline-ul complet:

1. face fetch controlat al surselor aprobate
2. construiește SQLite
3. rulează `PRAGMA integrity_check`
4. generează manifestul versionat
5. copiază output-urile în `app/src/main/scouty_assets/`

Pentru rebuild fără refetch:

```powershell
python tools/knowledge_pipeline/sync_knowledge_pack.py --skip-fetch
```

## Integritate la runtime

La pornire, `KnowledgePackManager`:

1. citește manifestul din assets
2. copiază SQLite și manifestul în storage-ul aplicației
3. verifică versiunea
4. verifică SHA-256
5. rulează `PRAGMA integrity_check`

Statusul rezultat este expus în debug UI, în `ProfileScreen`.

## Limitări actuale

- Pipeline-ul actual este determinist și integrat în repo, dar baza canonică pe PC nu este încă mutată într-un backend complet cu PostgreSQL + pgvector.
- Unele surse externe pot refuza fetch-ul automat în anumite momente. Acest lucru este vizibil în manifestul de fetch și nu este ascuns de pipeline.
- Runtime-ul mobil folosește retrieval lexical și rerank multi-factor; integrarea unui model local Google AI Edge rămâne pasul următor peste acest strat deja stabil.
