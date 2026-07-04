# Starea aplicației în repo-ul de producție

Actualizat local pe 2026-07-02. Pentru descrierea completă tehnică și funcțională folosește `../SCOUTY_PREZENTARE_TEHNICA.md`; acest fișier este un status scurt.

## Versiune

Versiunea declarată în aplicație rămâne `1.0.0`.

## Ce funcționează acum

### Shell și navigație

Aplicația pornește în `MainActivity`, folosește Jetpack Compose și păstrează cele 6 secțiuni principale:

- Home
- Map
- Chat
- SOS
- Gear
- Profile

### Date locale și reasoning offline

La runtime, aplicația citește local:

- `app/src/main/scouty_assets/local_route_enriched_catalog.json`
- `app/src/main/scouty_assets/local_route_geometry_index.json`
- `app/src/main/scouty_assets/Trasee_Varfuri.geojson`
- `app/src/main/scouty_assets/Pradatori.geojson`
- `app/src/main/scouty_assets/Atractii.geojson`
- `app/src/main/scouty_assets/Izvoare_Adapost.geojson`
- `app/src/main/scouty_assets/knowledge_pack.sqlite`
- `app/src/main/scouty_assets/knowledge_pack_manifest.json`

`KnowledgePackManager` verifică existența, versiunea, hash-ul și integritatea SQLite pentru knowledge pack-ul offline și îl copiază în storage-ul aplicației. Retrieval-ul assistant-ului nu mai depinde de un seed simplu din JSON.

Pentru hartă, overlay-urile locale sunt acum în repo. Master-ul PMTiles și pack-urile per traseu se generează în `tools/generated-map-packs/`, apoi se publică pe serverul de hărți sau se pot împinge pe device pentru debug cu `tools/sync_map_packs.ps1`.

### Home

`HomeScreen` afișează:

- status GPS
- coordonate și altitudine
- baterie și Battery Safe
- stare online/offline
- sumar pentru traseul activ
- recomandări de trasee bazate pe profil

### Hartă și trasee

`MapScreen` rămâne nucleul de explorare și selecție de traseu.

Ce face acum:

- randare hartă prin MapLibre
- folosește master-ul online `romania-high-detail.pmtiles` când există internet
- pregătește automat pack-ul offline `trails/{trailCode}/offline.pmtiles` pentru traseul curent
- păstrează local pack-ul traseului curent și ultimele 3 pack-uri recente
- căutare locală de trasee din asset-uri
- selectare traseu și highlight pe hartă, inclusiv tap tolerance extins pentru linii subțiri
- setare traseu activ
- buton discret de reîncadrare a traseului planificat
- HUD pentru traseul activ care se extinde/restrânge prin tap sau drag vertical
- busolă MapLibre repoziționată sus-dreapta ca să nu intre peste acțiunile din dreapta-jos
- expunere clară a metadata-ului de traseu:
  - regiune
  - descriere locală / rezumat
  - dificultate
  - durată
  - distanță
  - diferență de nivel
  - `from` / `to`
  - marcaj citit din `symbols`
  - provenance și surse locale

### Profil utilizator

Profilul curent este legat de Firebase Auth și Firestore, cu fallback local pentru situații offline. `ProfileAssessmentEngine` construiește profilul inițial din onboarding, iar `ProfileProgressionEngine` calculează XP, niveluri și achievement-uri.

Persistența curentă:

- Firestore pentru profil, preferințe și istoric trasee
- `ProfileCacheStore` pentru ultimul profil autentificat
- `PendingTrailCompletionStore` pentru finalizări făcute offline
- `ActiveTrail.ownerUid` pentru legarea traseului activ de contul care l-a selectat
- `MainViewModel.discardActiveTrailIfForeign(...)` elimină traseul restaurat local dacă aparține altui UID

### Recomandări de trasee

`RouteRecommendationEngine` rulează peste catalogul local și profilul utilizatorului.

Scopul actual:

- filtrează traseele incompatibile
- calculează scoruri explicabile
- produce recomandări coerente, nu doar listare brută
- expune motive scurte și ușor de afișat în UI

### Recomandări de gear

`GearRecommendationEngine` este compact și determinist.

Ce livrează:

- categorii mici și utile
- iteme obligatorii / recomandate / condiționale
- motive clare pentru fiecare item
- adaptare la traseu activ, vreme, apus și profil

### Assistant offline

`ChatScreen` și `AssistantRepository` folosesc retrieval offline din knowledge pack versionat. Răspunsul offline este selectat determinist din carduri locale, fără descărcare sau rulare de model generativ pe dispozitiv.

Fluxul implementat:

1. detectare limbă
2. corecție typo pe vocabularul din `search_vocabulary`
3. rezolvare deterministă pentru follow-up-uri și pronume, când contextul o permite
4. candidate retrieval din `knowledge_pack.sqlite`
5. rerank lexical/contextual și semantic
6. selecție card top
7. safety policy separat pe textul utilizatorului
8. răspuns cu secțiuni, citări și chips de follow-up pentru cardurile hub

Rerank-ul combină:

- relevanță lexicală
- potrivire pe domeniu
- limbă
- source trust
- freshness
- safety tags
- boost pentru contextul traseului activ
- redundancy penalty

Assistant-ul folosește și context local de teren:

- nume traseu
- regiune
- marcaj
- `from` / `to`
- rezumat traseu
- vreme
- apus
- baterie
- GPS
- shortlist de gear

### Debug / settings

`ProfileScreen` expune acum pentru debug:

- versiunea knowledge pack-ului
- versiunea și starea modelului
- ultimul sync / generated-at
- modul curent de generare

## Ce este încă parțial

### Asistent conversațional offline

Runtime-ul activ pentru offline este determinist: pachet local de cunoștințe, FTS SQLite, reranking și compunere directă din cardul selectat. Modelarea generativă rămâne pe calea online, prin Gemini, când există conexiune și politica de runtime o permite.

### SOS

`SosScreen` construiește un mesaj de urgență din GPS, baterie, traseu activ, progres și date medicale opționale. Aplicația deschide compozitorul SMS sau dialer-ul prin intent-uri de sistem; nu trimite SMS și nu sună automat în fundal.

Limitarea rămasă: nu există un flux automat de escaladare fără confirmarea utilizatorului, intenționat din motive de siguranță și control Android.

## Dependențe externe la runtime

Produsul rămâne offline-first pentru reasoning și datele locale de trasee, dar există în continuare dependențe externe pentru:

- autentificare și sincronizare Firebase
- tileset-urile hărții când nu există pack local pentru traseul curent
- forecast-ul Meteoblue
- imagini externe de traseu, unde există
- formulare Gemini pe calea online

## Testare și validare

Sunt acoperite teste unitare pentru:

- retrieval și rerank assistant
- safety policy
- structured assistant output
- route recommendation
- gear recommendation

Comenzile de validare folosite în repo:

```powershell
.\gradlew.bat :app:compileDebugKotlin :app:testDebugUnitTest --continue
```

Rezultat 2026-07-02:

- `:app:compileDebugKotlin` trece
- `:app:testDebugUnitTest` rulează 210 teste și are 6 eșecuri rămase în `SosMessageBuilderTest` și `GearRecommendationEngineTest`
