# AI Calendar

Agenda Android **voice-first** basata su AI: parli (o scrivi) e il calendario si organizza.

> *"il 14 ho il medico alle 15"* → evento creato, conflitti segnalati, promemoria armato.
> *"sposta la palestra a giovedì"* → l'agenda si riorganizza.

La visione completa è in [`docs/VISION.md`](docs/VISION.md); il registro delle decisioni
in [`docs/AUTONOMOUS-LOG.md`](docs/AUTONOMOUS-LOG.md).

## Funzionalità

- **Home unificata**: agenda (vista Giorno/Settimana/Mese) con la barra dell'assistente
  sempre visibile in basso, stile app di messaggistica.
- **Assistente AI in italiano** che crea, sposta ed elimina eventi — anche intere routine
  in un solo messaggio — con ricorrenze RFC-5545 (RRULE) espanse in modo deterministico.
- **Voce**: dettatura con `SpeechRecognizer` (invio automatico opzionale) e risposte
  vocali con TextToSpeech; app shortcut "Parla con l'assistente".
- **Calendario di sistema**: legge gli impegni già presenti sul telefono (contesto AI +
  rilevamento conflitti) e può **scrivere gli eventi nel calendario Google** del
  dispositivo via `CalendarContract` — sincronizzazione senza OAuth.
- **Conflitti deterministici**: le sovrapposizioni sono calcolate dal codice, non dall'AI.
- **Promemoria** via AlarmManager, riarmati al riavvio.
- **Onboarding in 2 passi**: profilo + chat guidata che impara la tua routine.

## Architettura

- **Android nativo** — Kotlin + Jetpack Compose, Material 3 Expressive
- **Local-first**: Room + DataStore; il cloud serve solo per l'AI
- Layering `domain / data / ui / di` con Hilt; l'assistente è dietro interfacce di
  dominio (`domain/assistant/`) — oggi OpenRouter (modelli free), sostituibile
- Ricorrenze con **lib-recur** (RRULE), griglia mese con **kizitonwose Calendar Compose**,
  HTTP con **Ktor**, estrazione date offline con **ML Kit Entity Extraction**

## Build

Requisiti: JDK 17+, Android SDK (compileSdk 37).

```bash
# chiave AI di sviluppo (gitignored)
echo "OPENROUTER_DEV_KEY=sk-or-..." >> local.properties

./gradlew :app:assembleDebug        # APK debug
./gradlew :app:testDebugUnitTest    # unit test (JVM)
```

La CI (GitHub Actions) compila e pubblica l'APK debug a ogni push su `master`.

## Stato

🚀 **0.1.0 — MVP funzionante.** Prossime tappe (vedi [roadmap](docs/VISION.md#tappe)):
integrazione profonda con Gemini/App Functions e login per-utente (PKCE).
