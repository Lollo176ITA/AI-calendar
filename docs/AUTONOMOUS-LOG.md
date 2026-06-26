# Log decisioni autonome & migliorie

Registro delle decisioni prese in autonomia (utente assente) e delle migliorie, più recenti in alto.
Per ogni voce: **cosa**, **perché**. Fedeltà alla visione: agenda *intelligente* (AI + algoritmi che
conoscono la routine, gestiscono ricorrenze complesse, rilevano conflitti, riarrangiano e notificano).

## Verificato (giu 2026, emulatore + endpoint AI)


- **Assistente che RIARRANGIA l'agenda** (cuore della visione): la chat ora crea, **sposta/modifica** ed **elimina** eventi esistenti, non solo li crea. L'agenda è numerata (#N) nel prompt; il modello risponde con `event.action` (create/update/delete) e `ref` (#N); il VM risolve il riferimento e applica via use case. Verificato: "riunione domani alle 10" → "sposta la riunione alle 15" (UPDATE) → "cancella la riunione" (DELETE), con persistenza confermata nel calendario. ✓
- **Dettaglio/modifica/elimina evento**: tap su un evento → schermata con titolo/data/ora/luogo modificabili (Salva) e cestino con conferma ("L'evento e le sue ripetizioni verrà rimosso"). Verificato: creazione via chat → tap → elimina → sparisce. ✓ Le occorrenze (`id@millis`) editano/eliminano l'evento "master".
- **Robustezza AI**: `chat()` ora ritenta con backoff (700ms, 1800ms) sui fallimenti transitori dei modelli free (rate-limit/empty), evitando il falso "Ops". Conferma: 1° tentativo fallito → retry OK.
- **Pulizia**: migrato `hiltViewModel` al package non deprecato (`androidx.hilt.lifecycle.viewmodel.compose`) — zero warning.
- **Onboarding 2 passi**: profilo (senza routine) → **chat routine** (l'AI chiede una cosa alla volta, es. "E per i pasti e il weekend?") → salva e va al calendario. ✓
- **Ricorrenza complessa FIX**: "un sabato al mese" → l'assistente risponde "ogni primo sabato del mese alle 20:00"; sezione Ricorrenti mostra "Un sabato al mese". ✓ (Curl AI: gemma genera `FREQ=MONTHLY;BYDAY=1SA` corretto col prompt migliorato.)
- **Modello**: qwen3-next-80b primo (miglior italiano), poi gemma-4 (ottima aderenza JSON/RRULE), poi nemotron; **gpt-oss rimosso**. Rete di sicurezza `fixMonthlyByDay` corregge l'ordinale mancante.
- **Prompt**: agente di ricerca (best practice OpenAI/Anthropic/Google + RFC-5545) → prompt con procedura RRULE numerata, contrasto "ogni sabato"≠"un sabato al mese", legenda giorni, self-check. Centralizzati in `AssistantPrompts.kt`.

## Decisioni architetturali chiave


- **AI capisce + struttura, l'app è la verità.** L'AI interpreta il linguaggio e produce dati strutturati
  (evento, **RRULE**); il **DB + codice deterministico** salva, espande le occorrenze, disegna il calendario
  e fa scattare i promemoria (timestamp esatti). Non si delega all'AI il calcolo delle date (lento, costoso,
  offline-rotto, e sbaglia). *Confermato con l'utente.*
- **Ricorrenze → RRULE (RFC-5545) con lib-recur** invece di (freq+intervallo). Perché: gestire "primo sabato
  del mese", "ultimo venerdì", "ogni 2 settimane lun+mer", ecc. — e correggere il bug "un sabato al mese".
- **Onboarding in 2 passi**: (1) profilo, (2) **chat guidata** in cui l'AI chiede la routine giorno per giorno
  e la salva. Il campo routine è nascosto nel form di onboarding, modificabile in Impostazioni.
- **Chat unica al posto della conferma** (per richiesta utente); Quick-Add rimosso.
- **Viste calendario** Giorno/Settimana/Mese nel menu **⋮**; header con profilo (sx) e impostazioni (dx);
  sezioni (Prossimi, Ricorrenti, Ricerca, Riepilogo) in un **drawer**.

## Scelte di libreria (l'utente gradisce librerie utili)


- **kizitonwose Calendar Compose** (griglia mese), **DataStore** (profilo/impostazioni), **lib-recur** (RRULE),
  **material-icons-extended**, **Ktor** (OpenRouter), **Compose Material 3 Expressive**.

## AI / OpenRouter


- Modelli **free** con structured output / json_object + **parsing tollerante** (i free ignorano `strict`).
- Chiave **dev via BuildConfig** (da `local.properties`, gitignored) per i test; login **PKCE** per-utente pianificato.
- `encodeDefaults=true` obbligatorio (altrimenti `response_format` perde `type` → 400).

## Stato build


- Gradle 9.6 / AGP 9.2.1 (built-in Kotlin) / Kotlin 2.3.10 / KSP 2.3.9 / compileSdk 37 / minSdk 26.
- DB Room con **migrazione distruttiva** (fase dev).

## Backlog migliorie pianificate (in autonomia)


1. **Motore conflitti + riarrangiamento** (cuore della visione): rilevare sovrapposizioni con routine/eventi,
   proporre/riarrangiare, notificare.
2. **Assistente che modifica/sposta/elimina eventi esistenti** (riarrangiamento via chat).
3. **Login PKCE** (sostituisce la dev key).
4. **Integrazione CalendarContract** (Google/Samsung Calendar) — requisito core "non standalone".
5. **Rifinitura**: tocchi Material 3 Expressive, stati loading/errore, più test.
