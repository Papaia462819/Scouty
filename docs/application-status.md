# Starea aplicației în varianta publicată

## Versiune

Versiunea declarată este `1.0.0`.

## Ce funcționează acum

### Shell și navigație

Aplicația pornește în `MainActivity` și folosește Jetpack Compose pentru UI. Navigația principală are 6 secțiuni:

- Home
- Map
- Chat
- SOS
- Gear
- Profile

### Home

Ecranul `Home` afișează:

- status GPS
- coordonate și altitudine
- status online/offline
- sumar pentru traseul activ
- acțiuni rapide

### Hartă

`MapScreen` este nucleul funcțional actual.

Ce face:

- randare hartă prin MapLibre
- stil compus din tileset-uri Mapbox configurate local
- search local de trasee din asset-uri incluse în aplicație
- selectare traseu și highlight pe hartă
- bottom sheet cu detalii și setare traseu activ
- overlay toggles pentru trasee, vârfuri, localități, apă, faună și atracții

### Date locale la runtime

Aplicația citește local:

- `local_route_enriched_catalog.json`
- `local_route_geometry_index.json`

Aceste fișiere sunt împachetate în APK și permit căutare și detalii locale fără să depindă de pipeline-ul original.

### Meteo și apus

`MainViewModel` gestionează:

- GPS
- baterie
- traseul activ
- sincronizarea prognozei Meteoblue
- fallback local pentru ora apusului

### Gear Checklist

`GearScreen` folosește un motor real de reguli pentru:

- listă de bază fără traseu selectat
- adaptarea echipamentului în funcție de dificultate, durată, diferență de nivel și caracterul turei
- păstrarea stării packed/not packed

## Ce este încă parțial sau placeholder

### Chat

`ChatScreen` este un shell UI fără backend AI real.

### SOS

`SosScreen` este în prezent mai mult un demo de flux decât un mecanism operațional complet.

### Profile

`ProfileScreen` conține încă date și grafice placeholder.

## Dependențe externe la runtime

Deși asset-urile de traseu sunt locale, aplicația depinde în continuare de servicii externe pentru:

- tileset-urile Mapbox folosite de hartă
- forecast-ul Meteoblue
- imaginile traseelor încărcate prin URL

## Testare existentă

Sunt incluse teste unitare pentru:

- repository-urile de trasee
- motorul de recomandare a echipamentului

Comanda de verificare:

```powershell
.\gradlew.bat testDebugUnitTest
```
