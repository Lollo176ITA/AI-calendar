# AI-calendar — MVP Design Spec

**Data:** 2026-06-26
**Fase:** 1 (MVP "Agenda con AI Quick-Add")
**Stato:** approvato (brainstorming) → implementazione

## 1. Visione

App Android nativa che funge da **agenda personale intelligente** usando AI + algoritmi.
L'utente aggiunge eventi in linguaggio naturale (scritto o a voce), l'app li interpreta,
li mostra nell'agenda, avvisa sui conflitti e li ricorda con notifiche puntuali. (Routine,
riorganizzazione automatica e integrazioni vocali di sistema = fasi successive, vedi §11.)

## 2. Obiettivi e principi

- **Uso personale** (utente + poche persone) ma **codice modulare e pronto a scalare**.
- **Local-first**: i dati vivono sul telefono; nessun login obbligatorio per i dati locali
  (il login OpenRouter serve solo per l'AI cloud).
- **Confini netti**: ogni capacità esterna (dati, AI, promemoria, auth) sta dietro
  un'**interfaccia** del dominio → si potranno aggiungere sync cloud / proxy / on-device
  senza riscrivere UI o dominio.
- **Mai bloccarsi**: l'app degrada con grazia se offline, non loggata o senza permessi.
- **Standard alti**: architettura a strati, test sulle parti logiche, gestione errori esplicita.

## 3. Stack tecnico

- **Android nativo**, Kotlin, **Jetpack Compose**, **Material 3 Expressive**
  (`androidx.compose.material3` linea 1.5.0-alpha, dietro un wrapper `AppTheme`).
- `compileSdk = 36` (Android 16), `targetSdk = 36`, `minSdk = 26` (java.time nativo, ~95%+ device).
- Build con il **JDK 21 di Android Studio (JBR)**; Gradle via **wrapper**; AGP/Kotlin/Compose
  versioni stabili correnti (pinnate nei file Gradle dopo verifica su Maven).
- Librerie: **Room** (DB), **Hilt** (DI), **Ktor client** (OpenRouter), **WorkManager**,
  **ML Kit Entity Extraction**, AndroidX `security-crypto` (token), `browser` (Custom Tabs per PKCE),
  Coroutines/Flow. Test: JUnit, Turbine, Room testing, (instrumented) AndroidX Test.

## 4. Architettura (a strati, local-first)

```
UI (Compose, Material 3 Expressive)  ── ViewModel (StateFlow) ──┐
                                                                 ▼
Domain (Kotlin puro)  ── modelli + use case + INTERFACCE ───────┤
                                                                 ▼
Data  ── implementazioni: Room · ML Kit+OpenRouter · AlarmManager · Keystore
```

Interfacce-chiave (i punti di estensione per scalare):

| Interfaccia        | Implementazione MVP                          | Evoluzione |
|--------------------|----------------------------------------------|------------|
| `EventRepository`  | Room (DAO + entity + mapper)                 | + sync cloud |
| `EventExtractor`   | `HybridEventExtractor` (ML Kit + OpenRouter)  | + Gemini Nano / proxy |
| `ReminderScheduler`| `AlarmManager.setAlarmClock` + BootReceiver  | invariato |
| `AuthSessionStore` | EncryptedSharedPreferences / Keystore         | + backend proxy |
| `AiClient`         | OpenRouter via Ktor (PKCE)                    | + altri provider |

DI: Hilt. Async: Coroutines/Flow. Modularità: **modulo singolo, package per-strato/feature**
all'inizio; split in moduli Gradle quando cresce.

## 5. Modello dati

`CalendarEvent` (dominio): `id: String(UUID)`, `title: String`, `start: Instant` (+ `ZoneId`),
`end: Instant?` (default +1h), `allDay: Boolean`, `location: String?`, `notes: String?`,
`source: enum{MANUAL, AI_TEXT, AI_VOICE}`, `reminderOffsetMin: Int?` (default 30),
`createdAt/updatedAt: Instant`.

Persistenza: `EventEntity` di Room (tempo = epoch-millis UTC + stringa fuso); mapper `Entity↔dominio`.
Date/ora con `java.time` (`Instant`/`ZoneId`), nativo da minSdk 26.

## 6. Pipeline AI (`HybridEventExtractor`)

Input: testo grezzo (digitato o trascritto) + contesto (`now`, fuso, locale IT).

1. **ML Kit Entity Extraction (sempre, offline):** estrae/normalizza data e ora → timestamp + granularità.
2. **OpenRouter (cloud, structured output):** `response_format=json_schema(strict)` → oggetto evento
   completo (titolo, start, end, luogo…). Nel prompt: data odierna + locale IT + esito ML Kit come ancora.
   `models:[primario, fallback]`, `provider:{require_parameters:true, data_collection:"deny", zdr:true}`.
3. **Riconciliazione:** data/ora di ML Kit come ground-truth; riempi default; produci
   `ExtractionResult = CalendarEvent proposto + confidenza + warning`.

**Degrado:** se offline o non loggato → salta lo step 2, crea evento base (ML Kit + euristica titolo),
marcato "da verificare".

**Auth OpenRouter (PKCE):** `code_verifier`/`code_challenge` → Custom Tab su
`openrouter.ai/auth` → al redirect (deep link) scambia il `code` su `/api/v1/auth/keys` → salva la
key in EncryptedSharedPreferences. Ogni utente usa i propri crediti (nessuna chiave nell'APK).

## 7. Rilevamento conflitti (leggero)

`DetectConflicts(proposed)`: query Room per eventi sovrapposti nello stesso giorno → ritorna overlap;
la UI mostra un avviso. MVP: **solo avviso**, nessuna riorganizzazione (Fase 3).

## 8. UI / schermate

`Onboarding` (nome) · `Auth` (collega OpenRouter) · `Today` (lista eventi del giorno, FAB "+",
navigazione a giorni, empty state) · `QuickAdd` (campo testo + 🎤 `SpeechRecognizer`) ·
`EventConfirm` (campi editabili + chip conflitto) · `EventDetail` (vedi/modifica/elimina).
Tema `MaterialExpressiveTheme` dietro `AppTheme`; `WavyProgressIndicator` per il loading AI;
Navigation Compose single-activity.

## 9. Promemoria, permessi, background

- `ReminderScheduler` → `AlarmManager.setAlarmClock()` (esente Doze). `USE_EXACT_ALARM` in manifest
  (l'app qualifica come agenda). Fallback `SCHEDULE_EXACT_ALARM` + `canScheduleExactAlarms()`.
- `BootReceiver` (RECEIVE_BOOT_COMPLETED) ri-arma gli allarmi dallo schedule in Room.
- Canale notifiche; `POST_NOTIFICATIONS` (Android 13+) e `RECORD_AUDIO` richiesti a runtime.
- `WorkManager` per ri-armo dopo reboot/update (in Fase 3 anche re-planning periodico).

## 10. Gestione errori & testing

Errori: offline/non loggato → degrado ML Kit-only; modello giù → fallback `models`; JSON malformato →
validazione/repair; token scaduto → ri-collega; permesso negato → degrado pulito. Risultati async come
tipi `Result` sealed mappati a stati UI.

Testing (standard alti): **TDD** su riconciliazione extractor, rilevamento conflitti, gestione date;
unit test su use case/ViewModel con fake + Turbine; instrumented test su Room DAO.

## 11. Fuori scope MVP (fasi successive)

Vedi roadmap completa in memoria di progetto (`project-roadmap`): Fase 2 routine & profilo
(ricorrenze, settimane alterne) · Fase 3 motore di riorganizzazione + task ricorrenti · Fase 4
App Shortcuts/AppFunctions · Fase 5+ sync cloud, widget, viste settimana/mese, Gemini Nano on-device.

## 12. Sequenza di build (vertical slices, commit per fetta)

1. Scaffold Gradle/Compose/Hilt che builda e gira sull'emulatore ("Hello Expressive").
2. Dominio + Room (`CalendarEvent`, `EventEntity`, DAO, `EventRepository`) + test.
3. Schermata `Today` che legge da Room (con dati seed) — visibile sull'emulatore.
4. `ReminderScheduler` + notifiche + BootReceiver + test/manuale.
5. `HybridEventExtractor`: ML Kit (offline) + riconciliazione + test; Quick-Add (solo testo) → conferma → salva.
6. OpenRouter (Ktor) + PKCE auth + structured output; Quick-Add ricco; degrado offline.
7. Voce (`SpeechRecognizer`) nel Quick-Add. Rilevamento conflitti con avviso.
8. Rifinitura Expressive, gestione permessi/errori, onboarding.
```
