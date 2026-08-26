# CONTEXT LICENȚĂ — Scouty 1.0.0

> **STATUS 2026-07-02:** document istoric/de lucru, nu sursa de adevar pentru starea curenta a aplicatiei. Pentru prezentarea actualizata foloseste `SCOUTY_PREZENTARE_TEHNICA.md`. Sectiunile de mai jos pot contine metadate si concluzii depasite despre `applicationId`, Firebase, module mock, Qwen/local LLM sau starea testelor.

> **Notă de adaptare a template-ului.** Promptul original a fost scris pentru un proiect numit „AutoNaut Ai" (asistență mecanică auto + OBD-II). Proiectul real din acest repo este **Scouty 1.0.0** — asistent outdoor offline pentru drumeții în Carpați. Conținutul relevant pentru varianta curentă este: knowledge pack SQLite pre-built, căutare locală FTS, corecție deterministă a întrebării, Jina reranker v2 pentru reordonare semantică, YOLO11n/ONNX pentru detecție urme animale și Gemini pentru formularea online când există conexiune. Offline, chatul nu descarcă și nu rulează un model generativ local.

> **Surse extracție.** Codul Android principal: `C:\Scouty\scouty_app\app\` (sub git, branch `main`, commit `7ae896c`). Dataset YOLO și artefacte antrenare: `D:\ScoutyDatasets\scouty_tracks_dataset_generated\`. Pipeline Python: `C:\Scouty\scouty_app\tools\`. Toate căile din raport sunt **relative la `C:\Scouty\scouty_app\`** dacă nu se specifică altfel.

---

## 0. METADATE PROIECT [VERIFICAT]

| Atribut | Valoare | Sursa |
|---|---|---|
| **Nume aplicație** | `Scouty` | `app/src/main/res/values/strings.xml:3` (`<string name="app_name">Scouty</string>`); `AndroidManifest.xml:15` (`android:label="@string/app_name"`) |
| **applicationId** | `com.scouty.app` | `app/build.gradle.kts:35` |
| **namespace** | `com.scouty.app` | `app/build.gradle.kts:31` |
| **versionCode** | `1` | `app/build.gradle.kts:38` |
| **versionName** | `1.0.0` | `app/build.gradle.kts:39` |
| **compileSdk** | `35` (Android 15) | `app/build.gradle.kts:32` |
| **targetSdk** | `35` | `app/build.gradle.kts:37` |
| **minSdk** | `28` (Android 9 Pie) | `app/build.gradle.kts:36` |
| **Java source / target** | `VERSION_17` | `app/build.gradle.kts:57-58` |
| **Kotlin jvmTarget** | `17` | `app/build.gradle.kts:62` |
| **Kotlin version** | `2.0.21` | `build.gradle.kts:3-7` |
| **AGP** | `8.13.2` | `build.gradle.kts:2` |
| **KSP** | `2.0.21-1.0.27` | `build.gradle.kts:5` |
| **Compose plugin** | `2.0.21` | `build.gradle.kts:4` |
| **kotlinx.serialization plugin** | `2.0.21` | `build.gradle.kts:6` |
| **NDK version** | n/a | Nu există runtime nativ pentru chatul offline curent |
| **APK debug existent** | `app/build/intermediates/apk/debug/app-debug.apk` | (build local, dimensiunea în secțiunea 11) |

### 0.1. Limbaje și volum cod (LOC)

| Limbaj | Locație | Nr. fișiere | LOC |
|---|---|---:|---:|
| **Kotlin (main)** | `app/src/main/java/com/scouty/app/` | **105** | **31 427** |
| **Kotlin (test)** | `app/src/test/java/` | **26** | **4 760** *(diferența 36 187 − 31 427)* |
| **Kotlin (androidTest)** | `app/src/androidTest/java/` | **6** | (incluse în diferență) |
| **C++ (native JNI)** | n/a | **0** | 0 |
| **CMake** | n/a | **0** | 0 |
| **Python (tools/)** | `tools/` și `tools/knowledge_pipeline/` și `tools/benchmarks/` și `tools/card_generator/` | ~ **14** scripturi | ≈ 4 500 (estimat) |
| **PowerShell (tooling debug/date)** | `tools/*.ps1` | mai multe | n/a |
| **XML resurse** | `app/src/main/res/` | 4 | 136 |
| **Procent dominanță** | Kotlin ≈ 85 %, Python ≈ 12 %, C++ ≈ 2 %, alte ≈ 1 % | — | (estimat din LOC) |

> Numărul total LOC Kotlin (main + test + androidTest) = **36 187** (din `wc -l`).

### 0.2. Structură directoare la nivel 2

```
scouty_app/
├── app/
│   ├── build/                      (artefacte; ignorat de git)
│   ├── build.gradle.kts            (config app modul)
│   ├── proguard-rules.pro          (gol — R8 dezactivat)
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml
│       │   ├── assets/             (gol — vezi sourceSets remap la scouty_assets)
│       │   ├── java/com/scouty/app (toate sursele Kotlin — vezi 2.1)
│       │   ├── res/                (values/, values-ro/, xml/, font_certs.xml)
│       │   └── scouty_assets/      (folderul folosit de fapt — vezi sourceSets în build.gradle)
│       │       ├── Atractii.geojson           (4.59 MB)
│       │       ├── Izvoare_Adapost.geojson    (1.44 MB)
│       │       ├── Pradatori.geojson          (360 KB)
│       │       ├── Trasee_Varfuri.geojson     (62.6 MB)
│       │       ├── glyphs/                    (PMTiles glyph PBFs)
│       │       ├── knowledge_pack.sqlite      (13.51 MB) [Knowledge pack SQLite]
│       │       ├── knowledge_pack_manifest.json (19.5 KB)
│       │       ├── local_route_enriched_catalog.json (4.96 MB)
│       │       ├── local_route_geometry_index.json (1.43 MB)
│       │       ├── local_route_image_attribution_manifest.json (1.03 MB)
│       │       └── ml/
│       │           ├── jina-reranker-v2-base-multilingual-int8.onnx       (266.6 MB, Git LFS)
│       │           ├── jina-reranker-v2-base-multilingual-tokenizer.json  (16.3 MB)
│       │           ├── track_model_v1.onnx                                (10.12 MB)
│       │           ├── track_model_metadata.json                          (1.4 KB)
│       │           ├── track_model_calibration.json                       (289 B)
│       │           └── track_species.json                                 (1.9 KB)
│       ├── test/java/com/scouty/app/      (26 fișiere teste unitare JUnit4)
│       └── androidTest/java/com/scouty/app/ (6 fișiere teste instrumentate)
├── build.gradle.kts                (root — doar declarații plugin)
├── docs/                           (planning, application-status etc.)
├── gradle/wrapper/
├── gradle.properties
├── gradlew + gradlew.bat
├── local.properties (gitignored)
├── local.properties.example
├── scratch/
├── settings.gradle.kts
└── tools/                          (pipeline Python + benchmarks)
    ├── benchmarks/
    │   ├── bench_expression_layer.py
    │   ├── bench_jina_reranker.py
    │   └── bench_tool_call_parser.py
    ├── bin/                        (pmtiles.exe, go-pmtiles_windows zip)
    ├── build_bucegi_demo_bbox.py
    ├── build_offline_map_packs.py
    ├── card_generator/             (pipeline cards conversaționale — sute de JSON-uri)
    ├── knowledge_pipeline/
    │   ├── build_knowledge_pack.py
    │   ├── common.py
    │   ├── fetch_sources.py
    │   └── sync_knowledge_pack.py
    └── .tmp/                       (cache temporare PMTiles)
```

### 0.3. Listă completă fișiere `.kt` în `app/src/main/java/com/scouty/app/` (105 fișiere)

```
MainActivity.kt
api/MeteoblueModels.kt
api/MeteoblueService.kt
assistant/data/ConversationStore.kt
assistant/data/DeviceContextProvider.kt
assistant/data/KnowledgePackManager.kt
assistant/diagnostics/AssistantDiagnostics.kt
assistant/domain/AssistantRepository.kt                   (2 902 LOC — orchestrator central)
assistant/domain/AssistantRuntimeGraph.kt
assistant/domain/CampfireConversationEngine.kt           (1 778 LOC)
assistant/domain/InterpretationPipeline.kt                (948 LOC)
assistant/domain/LocalLlmGenerationEngine.kt             (wrapper legacy/fallback; chat offline trece allowLocalModel=false)
assistant/domain/ModelManager.kt                         (status/discovery pentru bundle MediaPipe legacy; chatul offline nu folosește model generativ local)
assistant/domain/TrailContextEngine.kt                    (1 443 LOC)
assistant/domain/expression/CardParaphraseEngine.kt
assistant/domain/memory/ConversationContextAssembler.kt
assistant/domain/memory/SummaryCompactor.kt
assistant/domain/retrieval/CrossEncoderReranker.kt       (Jina reranker ONNX)
assistant/domain/tools/AssistantTools.kt
assistant/domain/tools/ToolDispatcher.kt
assistant/model/AssistantModels.kt
assistant/model/AssistantRuntimeModels.kt
assistant/ui/AssistantFollowUpPrompts.kt
assistant/ui/AssistantViewModel.kt
data/RouteEnrichmentCatalog.kt
data/RouteGeometryIndex.kt
data/UserTrailProfileStore.kt
profile/LocalAccountRepository.kt
profile/ProfileAssessmentEngine.kt
profile/ProfileModels.kt
profile/ProfileProgressionEngine.kt
profile/ProfileViewModel.kt
tracks/data/TrackModelAssets.kt                          (Asset bootstrap + SHA-256 verificare)
tracks/data/TrackSpeciesCatalog.kt
tracks/domain/TrackConfidencePolicy.kt                   (bands: PROBABIL/POSIBIL/INCERT)
tracks/domain/TrackDetector.kt                            (YOLO11 inferență ONNX Runtime)
tracks/domain/TrackIdentificationUseCase.kt
tracks/domain/TrackModels.kt
tracks/domain/TrackPostprocessor.kt                       (NMS + letterbox -> source coords)
tracks/domain/TrackPreprocessor.kt                        (letterbox 640×640 + RGB float [0,1])
tracks/ui/TrackCameraScreen.kt                            (1 283 LOC — CameraX capture)
ui/MainViewModel.kt                                       (1 672 LOC — GPS, baterie, Meteoblue)
ui/ScoutyApp.kt                                           (root Compose + BottomBar)
ui/components/CommonComponents.kt
ui/components/RouteRemoteImage.kt
ui/components/ScoutyBottomBar.kt
ui/components/ScoutyComponents.kt
ui/components/TrailListItem.kt
ui/models/GearRecommendationEngine.kt                     (22 reguli rule-based)
ui/models/RouteRecommendationEngine.kt
ui/models/TrailPlanningModels.kt
ui/models/UIModels.kt
ui/screens/AuthFlowScreen.kt                              (1 773 LOC — Login/Register/Onboarding)
ui/screens/ChatScreen.kt                                  (chat UI cu citations + safety chips)
ui/screens/GearScreen.kt                                  (635 LOC)
ui/screens/HomeScreen.kt                                  (741 LOC)
ui/screens/MapScreen.kt                                   (4 005 LOC — MapLibre + PMTiles)
ui/screens/OnboardingStepScaffold.kt
ui/screens/ProfileScreen.kt                               (1 486 LOC)
ui/screens/SosScreen.kt
ui/theme/Color.kt
ui/theme/Shape.kt
ui/theme/Theme.kt
ui/theme/Type.kt
utils/MapConnectivityManager.kt
utils/MapDataConfig.kt
utils/MapLifecycleManager.kt
utils/MapPackRegistry.kt
utils/MapStyleConfig.kt                                   (1 013 LOC — definire stil MapLibre)
utils/SolarCalculator.kt                                  (NOAA sunset offline)
utils/TrailDifficultyCalculator.kt
utils/TrailEntity.kt                                      (declarat dar nefolosit — vezi 13)
```

(restul fișierelor mici de UI/components/theme nu sunt enumerate aici — sunt cele de mai sus)

### 0.4. Listă fișiere `.py`

```
tools/build_bucegi_demo_bbox.py
tools/build_offline_map_packs.py
tools/benchmarks/bench_expression_layer.py
tools/benchmarks/bench_jina_reranker.py
tools/benchmarks/bench_tool_call_parser.py
tools/card_generator/common.py
tools/card_generator/conversational_gen.py
tools/card_generator/schema.py
tools/card_generator/strict_ingest.py
tools/card_generator/validator.py
tools/knowledge_pipeline/build_knowledge_pack.py
tools/knowledge_pipeline/common.py
tools/knowledge_pipeline/fetch_sources.py
tools/knowledge_pipeline/sync_knowledge_pack.py
```

> **Nu există script Python de antrenare YOLO11 în repo.** Antrenarea a fost rulată extern cu CLI-ul Ultralytics. Argumentele sunt persistate în `D:\ScoutyDatasets\scouty_tracks_dataset_generated\runs\v1_animalclue_1280_yolo11n_tuned_20260502\args.yaml` (vezi 4.1).

### 0.5. Fișiere XML de layout în `res/layout/` — **NICIUNUL**

Scouty este 100 % Jetpack Compose, single-Activity. Nu există layout-uri XML. Restul XML din `res/`:

```
res/values/font_certs.xml         (certificate fonturi Google Fonts pentru downloadable fonts)
res/values/strings.xml            (86 linii, EN + RO mix)
res/values/themes.xml             (Theme.Scouty Material3)
res/values-ro/strings.xml         (50 linii — override-uri RO)
res/xml/backup_rules.xml          (BackupAgent rules)
res/xml/data_extraction_rules.xml (Android 12+ data extraction)
```

### 0.6. Structură directoare relevante non-Android

```
tools/                          → pipeline Python + benchmarks + card generator
tools/knowledge_pipeline/       → builder knowledge_pack.sqlite + fetch surse web (WHO, CDC, NPS, Salvamont, ANM)
tools/card_generator/           → generator cards conversaționale (campfire scenarios)
tools/benchmarks/               → bench expression layer, Jina reranker, tool call parser
tools/bin/                      → PMTiles CLI (go-pmtiles_windows)
docs/                           → application-status.md, planning/, offline-map-packs.md, project-structure.md
scratch/                        → workspace local (gitignored)
D:\ScoutyDatasets\              → datasets YOLO + runs antrenare (EXTERN, nu în repo)
```

**[VERIFICAT]** secțiune 0.

---

## 1. CONFIGURARE BUILD ȘI DEPENDENȚE [VERIFICAT]

### 1.1. `build.gradle.kts` (project root) — integral

```kotlin
plugins {
    id("com.android.application") version "8.13.2" apply false
    id("org.jetbrains.kotlin.android") version "2.0.21" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21" apply false
    id("com.google.devtools.ksp") version "2.0.21-1.0.27" apply false
    id("org.jetbrains.kotlin.plugin.serialization") version "2.0.21" apply false
}
```

### 1.2. `app/build.gradle.kts` — integral

Reprodus în secțiunea **14.2**. Highlight-urile:

- `namespace = "com.scouty.app"`, `compileSdk = 35`
- `minSdk = 28`, `targetSdk = 35`, `versionCode = 1`, `versionName = "1.0.0"`
- `testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"`
- `buildConfigField("String", "METEOBLUE_API_KEY", ...)` — injectat din `local.properties` (`meteoblue.apiKey`) sau env var
- `buildFeatures { compose = true; buildConfig = true }`
- `packaging.resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"`
- **Asset-uri remap**: `sourceSets.main.assets.setSrcDirs(listOf("src/main/scouty_assets"))` (NU folosește `src/main/assets/` standard)
- `release { isMinifyEnabled = false }` — **R8/ProGuard DEZACTIVAT pe release** [WIP — vezi 13]
- **Niciun `externalNativeBuild` în build.gradle.kts** — chatul offline curent nu folosește runtime nativ JNI pentru generare text.

### 1.3. Dependențe — lista completă cu versiuni

**UI / Compose**
| Lib | Versiune |
|---|---|
| `androidx.compose:compose-bom` | `2024.09.00` |
| `androidx.compose.ui:ui`, `ui-graphics`, `ui-tooling-preview`, `material3`, `material-icons-extended` | BOM |
| `androidx.compose.ui:ui-text-google-fonts` | `1.7.0` |
| `com.composables:icons-lucide` | `1.0.0` |
| `androidx.activity:activity-compose` | `1.9.2` |
| `androidx.lifecycle:lifecycle-runtime-ktx`, `lifecycle-runtime-compose`, `lifecycle-viewmodel-compose` | `2.8.7` |
| `androidx.core:core-ktx` | `1.13.1` |
| `androidx.exifinterface:exifinterface` | `1.3.7` (rotații EXIF pentru poze track) |
| `com.google.android.material:material` | `1.12.0` |

**Hartă / geo**
| Lib | Versiune |
|---|---|
| `org.maplibre.gl:android-sdk` | `12.3.1` |
| `com.google.android.gms:play-services-location` | `21.3.0` |

**Persistență (Room + KSP)**
| Lib | Versiune |
|---|---|
| `androidx.room:room-runtime`, `room-ktx` | `2.6.1` |
| `androidx.room:room-compiler` (ksp) | `2.6.1` |

**Networking / serializare**
| Lib | Versiune |
|---|---|
| `com.squareup.retrofit2:retrofit` | `2.11.0` |
| `org.jetbrains.kotlinx:kotlinx-serialization-json` | `1.7.3` |
| `com.jakewharton.retrofit:retrofit2-kotlinx-serialization-converter` | `1.0.0` |
| `com.squareup.okhttp3:logging-interceptor` | `4.12.0` |
| `io.coil-kt:coil-compose` | `2.7.0` |

**AI / ML**
| Lib | Versiune | Rol |
|---|---|---|
| `com.google.mediapipe:tasks-genai` | `0.10.27` | Runtime alternativ pentru **Gemma 3 1B IT INT4** (.task / .litertlm) |
| `ai.djl.huggingface:tokenizers` | `0.33.0` | Tokenizer pentru Jina reranker (BERT-like multilingual) |
| `ai.djl.android:tokenizer-native` (runtimeOnly) | `0.33.0` | Native binding tokenizers |
| `com.microsoft.onnxruntime:onnxruntime-android` | `1.25.0` | Inferență ONNX pentru **YOLO11 track detector** și **Jina reranker** |

**Camera (pentru track scanner)**
| Lib | Versiune |
|---|---|
| `androidx.camera:camera-core`, `camera-camera2`, `camera-lifecycle`, `camera-view` | `1.4.2` |

**Native runtime LLM**
- Nu există runtime nativ LLM în varianta curentă a produsului.

**Test**
| Lib | Versiune |
|---|---|
| `junit:junit` (test) | `4.13.2` |
| `androidx.test.ext:junit` (androidTest) | `1.2.1` |
| `androidx.test.espresso:espresso-core` | `3.6.1` |
| `androidx.compose.ui:ui-test-junit4` (androidTest) | BOM |
| `androidx.compose.ui:ui-tooling`, `ui-test-manifest` (debug) | BOM |

**Observații cheie:**
- **NU există** dependențe Firebase (auth, firestore, storage), nu există SDK-uri generative suplimentare, nu există MLKit, nu există ZXing, nu există TensorFlow Lite, nu există PyTorch Mobile.
- **NU există** plugin `com.google.gms.google-services` și nici `google-services.json`.
- Chatul offline nu folosește un LLM local. Răspunsul este selectat din cardurile locale și reordonat semantic; generarea naturală este disponibilă doar pe calea online prin Gemini.

### 1.4. `gradle.properties`

```properties
org.gradle.jvmargs=-Xmx2g -Dfile.encoding=UTF-8
android.useAndroidX=true
kotlin.code.style=official
android.nonTransitiveRClass=true
android.suppressUnsupportedCompileSdk=35
```

### 1.5. `settings.gradle.kts`

```kotlin
pluginManagement {
    repositories { google(); mavenCentral(); gradlePluginPortal() }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories { google(); mavenCentral() }
}
rootProject.name = "Scouty"
include(":app")
```

### 1.6. Permisiuni declarate în `AndroidManifest.xml`

```
android.permission.INTERNET
android.permission.ACCESS_NETWORK_STATE
android.permission.ACCESS_FINE_LOCATION
android.permission.ACCESS_COARSE_LOCATION
android.permission.CAMERA
```

### 1.7. Componente declarate

| Tip | Nume | Path | Note |
|---|---|---|---|
| `<activity>` | `.MainActivity` | `MainActivity.kt` | `android:exported="true"`, `android:theme="@style/Theme.Scouty"`, intent-filter `MAIN` + `LAUNCHER` |
| `<service>` | — | — | **niciunul** |
| `<provider>` | — | — | **niciunul** |
| `<receiver>` | — | — | **niciunul** — receiver-ul de baterie este înregistrat dinamic în `MainViewModel.init` cu `RECEIVER_NOT_EXPORTED` pe Android 13+ |

Manifestul integral este reprodus la **14.9**.

### 1.8. Python `requirements.txt` / `pyproject.toml`

**[NU SE GĂSEȘTE ÎN COD]** — nu există `requirements.txt` și nici `pyproject.toml` în repo. Pipeline-ul Python pare să fie rulat ad-hoc cu pachete instalate global (Ultralytics, sentence-transformers etc.). [WIP — recomandare pentru lucrare: documentează manual environment-ul Python folosit]

**[VERIFICAT]** secțiune 1.

---

## 2. ARHITECTURA SOFTWARE [VERIFICAT]

### 2.1. Pachete și clase Kotlin

Arhitectură identificabilă: **MVVM cu Compose + grafică de runtime asistent custom (RAG + LLM local + tools)**, single-module, single-Activity. ViewModel-urile sunt injectate manual (factory pattern), fără DI framework (Hilt/Dagger absent — `grep` nu returnează nimic).

Pattern-ul asistentului este o **RAG (Retrieval-Augmented Generation) pipeline** cu:
1. **QueryAnalyzer** (clasificare query: route_context, gear, campfire, etc.)
2. **SqliteKnowledgeChunkStore + KnowledgePackManager** (retrieval prin FTS4 SQLite)
3. **CrossEncoderReranker** (re-ranking cu Jina v2 ONNX)
4. **DirectAnswerComposer / TemplateGenerationEngine** (compunere deterministă din cardul local selectat)
5. **GeminiRemoteGenerationEngine** (formulare online când există conexiune)
6. **MedicalSafetyPolicy** (clasificare safety înainte de generare)
7. **ConversationStore** (SQLite cu summary compaction)

Schema runtime cablată în `AssistantRuntimeGraph.kt` (vezi 14.6).

#### Pachete

| Pachet | Rol | Clase principale |
|---|---|---|
| `com.scouty.app` | Entry point | `MainActivity` (50 LOC; cere permisiuni runtime, montează `ScoutyTheme { ScoutyApp() }`) |
| `com.scouty.app.api` | Data — networking Meteoblue | `MeteoblueService` (Retrofit interface), `MeteoblueResponse/Metadata/CurrentWeather/HourlyData/DailyData`, `MeteoblueLocationSearchResponse/Result` |
| `com.scouty.app.assistant.data` | Data — assistant DB & runtime providers | `KnowledgePackManager` (instalează SQLite pack din assets cu SHA-256 + PRAGMA integrity_check), `SqliteKnowledgeChunkStore` (FTS4 queries + cross-domain fallbacks), `ConversationStore` (SQLite cu schema conversations+turns, summary compaction), `DeviceContextProvider` (interface), `KnowledgeChunkStore` (interface), `CampfireEmbeddingStore`, `CampfirePhrasingEmbedding`, `CampfireCardEmbedding` |
| `com.scouty.app.assistant.diagnostics` | Logging asistent | `AssistantDiagnostics` |
| `com.scouty.app.assistant.domain` | Domain — RAG orchestrator | `AssistantRepository` (2 902 LOC, **clasa centrală**), `AssistantRuntimeGraph` (DI graph manual + feature flags), `QueryAnalyzer`, `RetrievalEngine`, `PromptBuilder`, `MedicalSafetyPolicy`, `GenerationEngine` (interface), `TemplateGenerationEngine`, `LocalLlmGenerationEngine`, `ModelManager`, `TrailContextEngine`, `CampfireConversationEngine` (1 778 LOC), `InterpretationPipeline`, `OnDeviceGroundedWordingEngine`, `DisabledGroundedWordingEngine` |
| `com.scouty.app.assistant.domain.expression` | Domain — paraphrase | `CardParaphraseEngine`, `ModelManagerCardParaphraseModel` |
| `com.scouty.app.assistant.domain.memory` | Domain — context conversational | `ConversationContextAssembler`, `ConversationHistory`, `SummaryCompactor` |
| `com.scouty.app.assistant.domain.retrieval` | Domain — reranking | `CrossEncoderReranker` (Jina v2 ONNX cu DJL HF tokenizer, padded sequence length 96, sigmoid scoring) |
| `com.scouty.app.assistant.domain.tools` | Domain — grammar-constrained tool calling | `AssistantTools`, `ToolDispatcher`, `GrammarToolCallPlanner`, `ModelManagerToolCallModel` |
| `com.scouty.app.assistant.model` | Domain — DTO | `SafetyOutcome` (enum), `DeviceContextSnapshot`, `TrailContextSnapshot`, `AssistantCitation`, `AssistantResponse`, `AssistantUiState`, `StructuredAssistantOutput`, `StructuredResponseSection`, `ResponseSectionStyle`, `ModelStatus`, `ModelRuntimeState`, `KnowledgePackStatus`, `KnowledgePackManifest`, `KnowledgeChunkRecord`, `GenerationMode`, `ReasoningType`, `ConversationLane`, `DomainHint`, `QueryAnalysis`, `CardFamily`, etc. (~ 30+ data classes/enum-uri în 2 fișiere) |
| `com.scouty.app.assistant.ui` | UI — assistant | `AssistantViewModel`, `AssistantFollowUpPrompts` |
| `com.scouty.app.data` | Data — trasee | `RouteEnrichmentCatalog/Entry/Description/Image/MnData/RouteEnrichmentRepository`, `RouteGeometryIndex/Entry/Center/Bounds/RouteCoordinate/RouteGeometryRepository`, `UserTrailProfileStore` |
| `com.scouty.app.profile` | Profile / auth local | `ScoutyLevel` (enum 10 nivele), `UserProfile`, `LocalAccountRecord`, `OnboardingDraft`, `ProfileOption/Question`, `AssessmentResult`, `SessionStage`, `ProfileSessionUiState`, `LocalAccountRepository` (SharedPreferences + SHA-256), `ProfileAssessmentEngine` (10 întrebări weighted), `ProfileProgressionEngine`, `ProfileViewModel` |
| `com.scouty.app.tracks.data` | Data — track ML assets | `TrackModelAssets` (asset bootstrap + SHA-256 verificare model `25cb2b16...`), `TrackSpeciesCatalog` |
| `com.scouty.app.tracks.domain` | Domain — detecție urme | `TrackDetector` (ONNX Runtime, input `[1, 3, 640, 640]`), `TrackPreprocessor` (letterbox 640×640, pad gray 114), `TrackPostprocessor` (NMS @ IoU 0.45, conf 0.25, max 10 detections), `TrackConfidencePolicy` (3 bands: PROBABIL ≥ 0.70 / POSIBIL ≥ 0.40 / INCERT cu ambiguous margin 0.08), `TrackIdentificationUseCase` (EXIF rotation + detect), `TrackModels` (`TrackBoundingBox`, `TrackPrediction`, `TrackIdentificationResult`, `TrackConfidenceBand` enum, `LetterboxInfo`) |
| `com.scouty.app.tracks.ui` | UI — track scanner | `TrackCameraScreen` (1 283 LOC — CameraX preview + capture + inferență live) |
| `com.scouty.app.ui` | UI — ViewModel root + Scaffold | `MainViewModel` (1 672 LOC, `AndroidViewModel, DeviceContextProvider`; GPS via `FusedLocationProviderClient`, baterie via `BroadcastReceiver`, Meteoblue via Retrofit), `ScoutyApp` (root Compose cu `BottomBar` și `TopDestination` enum) |
| `com.scouty.app.ui.components` | UI — shared widgets | `CommonComponents`, `RouteRemoteImage` (Coil), `ScoutyBottomBar`, `ScoutyComponents`, `TrailListItem` |
| `com.scouty.app.ui.models` | View models data | `GearItem`, `GearNecessity` (enum), `ActiveTrail`, `HomeStatus`, `GearRecommendationEngine` (22 reguli rule-based — vezi 8.6), `RouteRecommendationEngine`, `TrailPlanningModels` |
| `com.scouty.app.ui.screens` | Compose screens | `AuthFlowScreen`, `ChatScreen`, `GearScreen`, `HomeScreen`, `MapScreen` (4 005 LOC), `OnboardingStepScaffold`, `ProfileScreen`, `SosScreen` |
| `com.scouty.app.ui.theme` | Material3 theme | `Color`, `Shape`, `Theme`, `Type` |
| `com.scouty.app.utils` | Utility | `MapConnectivityManager`, `MapDataConfig`, `MapLifecycleManager` (LifecycleObserver pentru MapView), `MapPackRegistry`/`Manager` (`MapPackId` enum cu `ROMANIA_BASE` + `BUCEGI_HIGH`, `MapPackStatus`, `InstalledMapPack`), `MapStyleConfig` (23 layer ID-uri, 4 simboluri custom — 1 013 LOC), `MapOverlayState`, `SolarCalculator` (NOAA sunset offline), `TrailDifficultyCalculator`, `TrailEntity`+`TrailDao`+`AppDatabase` (declarate dar nefolosite — vezi 13) |

### 2.2. Activități Android

| Atribut | Valoare |
|---|---|
| Nr. Activity | **1** singură (`MainActivity`) |
| Path | `app/src/main/java/com/scouty/app/MainActivity.kt` |
| Layout XML | **n/a** — folosește `setContent { ScoutyTheme { ScoutyApp() } }` (Compose) |
| Permisiuni runtime cerute | `ACCESS_FINE_LOCATION`, `ACCESS_COARSE_LOCATION`, `CAMERA` prin `ActivityResultContracts.RequestMultiplePermissions()` în `onCreate` |
| Pattern | Single-Activity Compose — toate ecranele sunt destinații `enum TopDestination` în `ScoutyApp.kt`: `HOME`, `MAP`, `CHAT`, `SOS`, `GEAR`, `PROFILE` |

> **Nu există `Intent(...::class.java)` între Activity-uri** — toată navigarea este state hoisting prin `MainViewModel` partajat (`grep -rn "Intent(" app/src/main/java | grep "::class.java"` returnează 0 rezultate).

### 2.3. Fragmente / Compose Screens

Nu există Fragment-uri. Lista composables principali (`@Composable` top-level pentru fiecare destinație):

| Composable | Path | LOC | Rol | Acțiuni runtime cheie |
|---|---|---:|---|---|
| `HomeScreen` | `ui/screens/HomeScreen.kt` | 741 | Dashboard: status GPS/baterie/online, trail activ, sugestii apropiate, quick actions | callback `onActiveTrailClick` care comută la `MAP` |
| `MapScreen` | `ui/screens/MapScreen.kt` | 4 005 | MapLibre + PMTiles + căutare trasee (cu `RouteEnrichmentRepository.search`) + alegere dată + `setActiveTrail` | Compose-> `AndroidView<MapView>` + lifecycle observer |
| `ChatScreen` | `ui/screens/ChatScreen.kt` | (mic) | Conversație cu asistentul; afișează `AssistantMessageUiModel` cu chips de safety și citations | `onSend`, `onInputChange`, `onPromptSelected` |
| `SosScreen` | `ui/screens/SosScreen.kt` | (mic) | Compose mesaj SOS din GPS + sunset + ipoteză incident | (UI doar) |
| `GearScreen` | `ui/screens/GearScreen.kt` | 635 | Checklist echipament generat dinamic | `onToggleItem` → `MainViewModel.toggleGearItem` |
| `ProfileScreen` | `ui/screens/ProfileScreen.kt` | 1 486 | Profil user, edit, sign-out | `onEditProfile`, `onSignOut` |
| `AuthFlowScreen` (cu `AuthScreen` + `ProfileOnboardingScreen`) | `ui/screens/AuthFlowScreen.kt` | 1 773 | Login / Register / 10-step onboarding | apelează `ProfileViewModel.login/register/completeRegistration` |
| `TrackCameraScreen` | `tracks/ui/TrackCameraScreen.kt` | 1 283 | CameraX preview + photo capture + `TrackIdentificationUseCase` (YOLO11 ONNX) | inferență on-device |

### 2.4. Persistență locală

Folosit:
- **Room** (cu KSP 2.6.1) — pentru `TrailEntity` (DECLARAT dar nefolosit, vezi 13). **NU mai există o bază Room pentru asistent** — versiunea anterioară (din `C:\Scouty\app\`) folosea Room FTS4, dar versiunea actuală folosește **SQLite raw** prin `SQLiteDatabase.openDatabase` cu un pack pre-built.
- **SQLite raw** (`android.database.sqlite.SQLiteDatabase`) — pentru:
  - **knowledge_pack.sqlite** (asset, 13.51 MB) — bază cunoștințe cu tabele `knowledge_chunks`, `knowledge_chunks_fts` (FTS4), `card_embeddings`, `phrasing_embeddings`. Gestionată de `KnowledgePackManager` + `SqliteKnowledgeChunkStore`.
  - **conversations.sqlite** (creat la runtime în `noBackupFilesDir`) — tabele `conversations(conversation_id, started_at, last_active_at, trail_id, summary, summary_token_count)` și `turns(conversation_id, turn_idx, role, text, timestamp, retrieved_chunk_id)` cu FK CASCADE. Gestionat de `ConversationStore.kt`.
- **SharedPreferences** — pentru:
  - `scouty_profile_store` cheia `local_account_json` (`LocalAccountRepository`)
  - `scouty_knowledge_pack` cheile `installed_version`, `installed_at` (versiune pack instalat)
- **Asset JSON files** (parsate la cerere, cache `@Volatile`):
  - `local_route_enriched_catalog.json` (846 trasee Wikimedia + Muntii Nostri)
  - `local_route_geometry_index.json` (polilinii Google-encoded per cod local)
  - `local_route_image_attribution_manifest.json`

#### Entități Room (declarate)

Singura entitate Room este **`TrailEntity`** (`utils/TrailEntity.kt`) care declară `@Entity(tableName = "trail_difficulty")` cu câmpurile: `id (PK)`, `name`, `difficulty`, `totalAscent`, `totalDescent`, `avgIncline`, `lengthKm`, `durationHours`. Există un DAO `TrailDao` cu `getTrail`, `insertTrail`, `getAll`, și un `@Database(entities = [TrailEntity::class], version = 1)` abstract numit `AppDatabase` — **dar nu este referit nicăieri în restul aplicației**. KSP totuși generează implementarea (în `app/build/generated/ksp/.../`).

> **Concluzie**: Versiunea curentă a asistentului folosește **knowledge_pack.sqlite ca asset pre-built** (generat de `tools/knowledge_pipeline/build_knowledge_pack.py` cu SHA-256 verificare la instalare), NU Room cu seed în cod. `AppDatabase` este moștenire din versiunea anterioară (vezi 13).

#### Schema knowledge_pack.sqlite (extrasă din interogări SQL în `SqliteKnowledgeChunkStore`)

Tabele:
- `knowledge_chunks` (chunk_id PK, domain, topic, language, title, body, source_title, source_url, publisher, source_language, adapted_language, publish_or_review_date, source_trust, safety_tags, country_scope, pack_version, keywords, card_family, priority, metadata_json, row_id)
- `knowledge_chunks_fts` — virtual FTS4 table peste body+title+keywords; JOIN pe `kc.row_id = fts.rowid`
- `card_embeddings` (card_id, topic, language, query_embedding BLOB, content_embedding BLOB, embedding_model, embedding_backend, embedding_dimension)
- `phrasing_embeddings` (card_id, topic, language, phrase_text, normalized_phrase, phrase_kind, embedding BLOB, embedding_model, embedding_backend, embedding_dimension)

Manifest (`knowledge_pack_manifest.json`, vezi 9.4):
- `pack_version = "2026.03.24-v1"`
- `generated_at = "2026-05-10T14:33:43+00:00"`
- `chunk_count = 2 116`
- `card_embedding_count = 2 116`
- `route_chunk_count = 1 692`
- `draft_chunk_count = 398`
- `db_sha256 = "d2d62befb2ac59a447a18d579bb212cda0e369eba44e8db139171c3cbd388fb9"`
- 11 domenii, 19 surse externe (vezi 9.4 pentru lista completă)

### 2.5. Repository și sincronizare

| Clasa | Path | Rol | Dependențe injectate |
|---|---|---|---|
| `AssistantRepository` | `assistant/domain/AssistantRepository.kt` (2 902 LOC) | Orchestrator central RAG pipeline | `featureFlags`, `knowledgePackManager`, `knowledgeStore`, `queryAnalyzer`, `retrievalEngine`, `promptBuilder`, `modelManager`, `groundedWordingEngine`, `generationEngine`, `medicalSafetyPolicy`, `trailContextEngine`, `crossEncoderReranker?`, `conversationStore?`, `conversationContextAssembler?`, `summaryCompactor?`, `cardParaphraseEngine?`, `toolCallPlanner?`, `toolDispatcher?` (cca. 18 dependențe, toate prin constructor primary). Vezi 14.3. |
| `AssistantRuntimeGraph` | `assistant/domain/AssistantRuntimeGraph.kt` | "DI container" manual — singleton thread-safe care construiește toate componentele asistentului în funcție de `RuntimeFeatureFlags` (vezi 14.6) | `Context`, `RuntimeFeatureFlags` |
| `KnowledgePackManager` | `assistant/data/KnowledgePackManager.kt` | Instalează knowledge_pack.sqlite din assets în `noBackupFilesDir/knowledge_pack/`, verifică SHA-256 + PRAGMA integrity_check | `Context` |
| `SqliteKnowledgeChunkStore` | `assistant/data/KnowledgePackManager.kt` | Implementare `KnowledgeChunkStore` cu FTS4 queries, fallback cross-domain, embedding cache | `KnowledgePackManager` |
| `ConversationStore` | `assistant/data/ConversationStore.kt` | Persistare conversații în SQLite cu summary compaction | `Context` |
| `ModelManager` | `assistant/domain/ModelManager.kt` | Status/discovery pentru bundle MediaPipe legacy; chatul offline curent folosește `allowLocalModel=false` și răspunde din carduri locale | `LocalModelLocator`, `LocalLlmRuntimeAdapter` |
| `LocalAccountRepository` | `profile/LocalAccountRepository.kt` | Persistă LocalAccountRecord (JSON serializat) în SharedPreferences | `Context` |
| `RouteEnrichmentRepository` (object) | `data/RouteEnrichmentCatalog.kt` | Încarcă și cache-uiește `local_route_enriched_catalog.json`; search accent-insensitive cu scoring | (object) |
| `RouteGeometryRepository` (object) | `data/RouteGeometryIndex.kt` | Decodează polilinii Google + pruning de leaf segments | (object) |
| `UserTrailProfileStore` | `data/UserTrailProfileStore.kt` | (preferințe utilizator trail) | `Context` |

#### Sincronizare offline-first

`MainViewModel.checkSmartSync()` (`ui/MainViewModel.kt`) implementează sincronizare Meteoblue condiționată pe proximitate + time-to-event (mai puternică decât în versiunea anterioară):

```kotlin
val syncIntervalMs = when {
    distanceKm < 10 -> 30 * 60 * 1000L
    diffHours < 12  -> 1 * 60 * 60 * 1000L
    diffHours < 48  -> 6 * 60 * 60 * 1000L
    else            -> 24 * 60 * 60 * 1000L
}
```

`loadForecastWithFallbacks(lat, lon, asl)` încearcă mai întâi punctul exact, apoi locații apropiate sortate după `featureClass` (P=populated > T=terrain) cu prag de distanță 40 km.

**[VERIFICAT]** secțiune 2.
