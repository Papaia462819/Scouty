# Structura pachetului publicat

Acest folder conține doar partea publicabilă a proiectului Scouty.

## Rădăcină

- `app/` - aplicația Android
- `docs/` - documentația proiectului publicat
- `gradle/` - Gradle wrapper
- `build.gradle.kts`, `settings.gradle.kts`, `gradle.properties` - configurarea proiectului
- `gradlew`, `gradlew.bat` - comenzi de build
- `local.properties.example` - șablon de configurare locală
- `.gitignore`, `.gitattributes` - fișiere utile pentru repo

## `app/`

### `app/src/main/java/com/scouty/app`

- `MainActivity.kt` - entry point Android
- `api/` - integrarea Meteoblue
- `data/` - loadere pentru asset-urile de traseu incluse în aplicație
- `ui/` - ViewModel, navigație și ecrane Compose
- `utils/` - configurarea hărții, helper-e de lifecycle și logică auxiliară

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

Acestea sunt incluse în APK deoarece `app/build.gradle.kts` configurează explicit `src/main/scouty_assets` ca sursă de assets.

## `docs/`

- `project-structure.md` - acest document
- `application-status.md` - cum funcționează aplicația în versiunea actuală

## Ce a fost exclus intenționat

Acest pachet nu include:

- scripturile Python de pipeline
- date brute și fișiere de lucru locale
- cache-uri
- loguri și capturi de debugging

Scopul folderului este să poată fi publicat direct ca repo al aplicației, fără dependențe locale inutile.
