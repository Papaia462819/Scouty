# Structura repo-ului de producție

Acest folder este sursa de adevăr pentru aplicația Scouty AI și pentru infrastructura locală necesară generării knowledge pack-ului offline.

## Rădăcină

- `app/` - aplicația Android
- `docs/` - documentația repo-ului
- `tools/` - pipeline-uri și utilitare locale de build pentru date
- `gradle/` - Gradle wrapper
- `build.gradle.kts`, `settings.gradle.kts`, `gradle.properties` - configurarea proiectului
- `gradlew`, `gradlew.bat` - comenzi de build
- `local.properties.example` - șablon de configurare locală
- `.gitignore`, `.gitattributes` - configurări auxiliare

## `app/`

### `app/src/main/java/com/scouty/app`

- `MainActivity.kt` - entry point Android
- `api/` - integrarea Meteoblue și servicii externe strict necesare
- `assistant/`
  - `data/` - manager pentru knowledge pack și acces local la chunk-uri
  - `domain/` - retrieval, rerank, safety policy, generation fallback, model manager
  - `model/` - tipurile assistant-ului și modelele de runtime
  - `ui/` - ViewModel și state pentru chat
- `data/` - catalog local de trasee și profilul persistent al utilizatorului
- `ui/`
  - `MainViewModel.kt` - coordonarea stării aplicației
  - `models/` - UI models, route planning, route recommendation, gear recommendation
  - `screens/` - ecranele Compose
- `utils/` - configurarea hărții și helper-e auxiliare

### `app/src/main/res`

Resurse Android standard:

- string-uri
- teme
- XML-uri de backup / data extraction

### `app/src/main/scouty_assets`

Asset-uri folosite la runtime:

- `local_route_enriched_catalog.json`
- `local_route_geometry_index.json`
- `local_route_image_attribution_manifest.json`
- `Trasee_Varfuri.geojson`
- `Pradatori.geojson`
- `Atractii.geojson`
- `Izvoare_Adapost.geojson`
- `knowledge_pack.sqlite`
- `knowledge_pack_manifest.json`
- `glyphs/`

Acestea sunt incluse în APK deoarece `app/build.gradle.kts` configurează explicit `src/main/scouty_assets` ca sursă de assets.

### `tools/generated-map-packs`

Pack-urile PMTiles locale folosite de hartă:

- `romania-base.pmtiles`
- `bucegi-high.pmtiles`
- `manifest.json`

Acest director rămâne în repo și este folosit de scripturile locale:

- `tools/sync_map_packs.ps1` - copiază pack-urile în `files/maps` pentru aplicația instalată
- `tools/smoke_map_runtime.ps1` - rulează smoke test-ul de hartă peste pack-urile sincronizate

## `tools/knowledge_pipeline`

Pipeline-ul local pentru knowledge pack-ul offline.

Conține:

- `sources.json` - registry auditable al surselor aprobate
- `curated_chunks.json` - chunk-uri rescrise și curate, în RO și EN
- `fetch_sources.py` - fetch controlat al surselor aprobate
- `build_knowledge_pack.py` - export determinist spre SQLite și manifest
- `sync_knowledge_pack.py` - rularea pipeline-ului complet
- `version.txt` - versiunea pack-ului

Output-urile intermediare merg în `tools/knowledge_pipeline/build/`, iar cache-ul de fetch în `tools/knowledge_pipeline/cache/`. Ambele sunt ignorate în git.

## `docs/`

- `project-structure.md` - acest document
- `application-status.md` - starea funcțională a aplicației
- `knowledge-pipeline.md` - sursele aprobate și pașii de regenerare a knowledge pack-ului
