# Log decisioni autonome & migliorie

Registro delle decisioni prese in autonomia (utente assente) e delle migliorie, più recenti in alto.
Per ogni voce: **cosa**, **perché**. Fedeltà alla visione: agenda *intelligente* (AI + algoritmi che
conoscono la routine, gestiscono ricorrenze complesse, rilevano conflitti, riarrangiano e notificano).

## Scheduling deterministico + eval suite dell'assistente (lug 2026)

- **SlotFinder (dominio, puro)**: l'algoritmo — non l'AI — rileva i conflitti E propone gli slot
  liberi più vicini della stessa durata (interval scheduling: intervalli occupati ordinati e fusi,
  complemento = buchi liberi, slot piazzato nel buco più vicino all'orario richiesto, arrotondato
  al quarto d'ora). Il warning in chat ora dice anche "Se preferisci, quel giorno sei libero:
  16:00–17:00 oppure 18:15–19:15." Coperto da unit test (`SlotFinderTest`). Nota: non esiste una
  libreria pubblica "scheduling di Google"; questo è l'approccio standard dietro le feature
  "trova un orario". Per la ri-ottimizzazione dell'intera giornata c'è Google OR-Tools (constraint
  solver open source, gira su Android/JVM) — in backlog, per ora sovradimensionato.
- **Fix dal collaudo, terzo giro (2 lug)**: (1) *l'onboarding ora mette la routine IN AGENDA* —
  la chat iniziale salvava la routine solo come testo nel profilo, senza creare eventi ("l'AI fa le
  domande poi non inserisce"): al termine dell'intervista il riassunto passa per l'event assistant
  e ogni blocco diventa un evento ricorrente vero (best-effort: un errore non blocca l'onboarding);
  (2) *occupazione multipla* — l'enum Professione a scelta singola non permetteva "lavoro e studio":
  ora `occupations: Set<Occupation>` con FilterChip multi-selezione (migrazione dal vecchio valore
  DataStore), prompt aggiornati ("studente e lavoratore"); (3) *grafica* — EventCard ridisegnata
  (barra accento colorata per origine, orari in colonna, etichetta ricorrenza, gestione "tutto il
  giorno" che prima mostrava orari senza senso, icona origine telefono) e barra di input a pillola
  stile messaggistica (TextField arrotondato senza underline). (1) *richiamo agenda scarso* —
  "che compleanni ho questo mese?" rispondeva "nessuno" con due compleanni in agenda (uno col
  refuso "CompleBbo"): nuova sezione prompt "DOMANDE SULL'AGENDA" (leggi ogni riga, includi
  refusi/abbreviazioni), MAX_AGENDA 25→40 (con un calendario reale il tetto basso troncava il
  contesto in silenzio), caso eval `compleanni-del-mese`; (2) *token `<pad>` trapelato nel reply*
  ("Tenga presente che<pad> questo impegno"): `sanitizeModelText` deterministica su ogni reply
  (pad/unk/s/ChatML), unit test; (3) *dava del lei a intermittenza*: regola "dai SEMPRE del tu".
  Confermati funzionanti sul dispositivo: coerenza di luogo (ha chiesto invece di creare il pranzo
  a Napoli, poi creato su conferma), slot liberi suggeriti dal SlotFinder, update via riferimento.
- **Fix dal collaudo sul telefono (feedback utente, 2 lug)**: (1) *coerenza di luogo* — il pranzo a
  Napoli col treno per Torino in agenda veniva creato senza obiezioni (nessuna sovrapposizione di
  orario): nuova regola nel prompt "COERENZA DI LUOGO E SPOSTAMENTI" (impegni incompatibili
  spazialmente nello stesso giorno -> non creare, chiedere); (2) *niente emoji* — il "⚠️" della nota
  di conflitto deterministica sostituito con "Attenzione:" e regola "MAI emoji" nel prompt;
  (3) *data già passata* — "ricordami il 14 maggio" detto a luglio veniva messo nel passato: regola
  "DATA GIA PASSATA -> prossima occorrenza futura o chiedi". Tutti e tre coperti da nuovi casi eval
  (conflitto-di-luogo, data-passata, check anti-emoji trasversale). L'import dal calendario di
  sistema è confermato funzionante sul dispositivo ("dal telefono · account", card distinte).
- **Eval suite dell'assistente** (`AssistantEvalTest` + `docs/EVAL.md`): 17 richieste utente
  realistiche in italiano con attese verificabili (date relative, RRULE insidiose, routine
  multi-evento, update/delete con ref, casi "zero operazioni"), eseguite in parallelo su più
  modelli OpenRouter con tabella comparativa. Opt-in via chiave (env o local.properties): senza
  chiave il test è SKIPPED e la CI non ne risente. `EVAL_MODELS` restringe i modelli,
  `EVAL_MIN_PASS` trasforma la suite in guardia anti-regressione del prompt. Per abilitarla:
  `OpenRouterApi.chat` accetta ora un override dei modelli e `OpenRouterAssistant` ha un overload
  `respond(..., models)`; `unitTests.isReturnDefaultValues=true` per android.util.Log nei test JVM.

## Voice-first: voce + home unificata + scrittura su Google Calendar (lug 2026)

- **Home unificata (agenda + assistente)**: la chat non è più una schermata separata dietro il FAB.
  La barra di input dell'assistente (stile app di messaggistica, con microfono) è sempre ancorata in
  basso nella home; la conversazione si espande come pannello richiudibile sopra l'agenda. Il gesto
  chiave — dire qualcosa all'AI — è a zero tap dall'apertura dell'app. Rotta `assistant` e
  `AssistantScreen` rimossi; i componenti riusabili vivono in `AssistantPanel.kt`.
- **Dettatura (SpeechRecognizer)**: mic nella barra di input, italiano, risultati parziali in
  streaming nel campo di testo, permesso `RECORD_AUDIO` a runtime, errori gestiti con messaggi di
  cortesia (no-match, busy, permesso negato). Toggle "Invio automatico dopo la dettatura" (default
  ON): il risultato finale parte da solo verso l'assistente.
- **Risposte vocali (TextToSpeech)**: per i turni iniziati a voce l'assistente legge la risposta ad
  alta voce (toggle "Risposte vocali", default ON). Il gate sta nel ViewModel (flow `speakReplies`);
  la UI possiede il TTS e lo spegne nel DisposableEffect.
- **App shortcut "Parla con l'assistente"**: shortcut statico (`res/xml/shortcuts.xml`) con action
  dedicata → `MainActivity` (`singleTop` + `onNewIntent`) incrementa un `voiceTrigger` che la home
  osserva per aprire la chat con il microfono già attivo. Primo aggancio agli assistenti di sistema;
  l'integrazione profonda (App Functions/Gemini) rimandata finché l'API non si stabilizza.
- **Scrittura sul calendario di sistema (CalendarContract, senza OAuth)**: opt-in in Impostazioni
  (toggle + scelta del calendario di destinazione, permesso `WRITE_CALENDAR`). Gli eventi
  creati/modificati/eliminati nell'app vengono specchiati sul calendario Google/Samsung scelto e
  quindi sincronizzati ovunque. Mappatura `systemEventId` in Room (DB v5); il reader esclude gli
  eventi specchiati per non mostrarli due volte; le ricorrenze passano l'RRULE al provider con
  DURATION al posto di DTEND (regola di CalendarContract). Se la copia viene cancellata su Google
  Calendar, al salvataggio successivo viene ricreata.
- **Manifest**: `<queries>` per RecognitionService/TTS (package visibility Android 11+),
  `windowSoftInputMode=adjustResize` per la barra di input con la tastiera aperta.
- (Non verificato su emulatore in questo ambiente — niente device; compilazione e unit test JVM
  verdi in locale, APK debug dalla CI.)

## Fix routine multi-evento (giu 2026)

- **L'assistente ora pianifica PIU eventi in un solo messaggio (FIX "la routine da errore")**: prima la chat
  poteva creare un solo evento per turno, quindi descrivendo l'intera routine ("lavoro lun/mer/gio/ven 7-18,
  martedi volontariato il pomeriggio, weekend la mattina fino alle 16") l'assistente *diceva* di averla
  registrata ma non creava nulla nel calendario. Ora il contratto JSON usa `"events":[...]` (lista) e
  `OpenRouterAssistant` accetta sia `events` (array) sia `event` (singolo, retrocompatibile); il VM applica
  ogni operazione e controlla i conflitti per ciascun evento creato. Prompt aggiornato: regola esplicita
  "se l'utente descrive una routine crea un evento per blocco, accorpando i giorni con stesso orario in un
  BYDAY multiplo (es. FREQ=WEEKLY;BYDAY=MO,WE,TH,FR)" + esempio completo. (Rivisto a codice; non verificato su
  emulatore in questo ambiente perché manca l'Android SDK.)

## Verificato (giu 2026, emulatore + endpoint AI)

- **Integrazione calendario di sistema (CORE "non standalone")**: l'app legge gli eventi del calendario del telefono (CalendarContract.Instances, permesso READ_CALENDAR) e li mostra nel calendario in card distinte ("dal telefono · nome-calendario"), read-only. Toggle in Impostazioni con richiesta permesso. Gli eventi di sistema entrano anche nel **contesto dell'AI** (consapevolezza, ma non modificabili). Verificato: evento "Dentista" seedato via adb → appare nel giorno; toggle on/off. ✓
- **Rilevatore conflitti DETERMINISTICO**: dopo create/update, il codice controlla le sovrapposizioni temporali con eventi app **e di sistema** e accoda un avviso ("⚠️ Attenzione: si sovrappone con «X» HH:mm–HH:mm"). Affidabile indipendentemente dal modello. Verificato: "palestra 19:45" → segnala sia «Chiamata di lavoro» (app) sia «Dentista» (sistema). ✓
- **Rete più robusta**: timeout 35s, 1 retry con backoff (i modelli free a volte sono lenti/limitati).
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

1. **Motore conflitti automatico**: oltre al rilevamento conversazionale (già attivo), evidenziare
   visivamente le sovrapposizioni nel calendario e proporre slot alternativi.
2. **Integrazione CalendarContract** (Google/Samsung Calendar) — requisito core "non standalone".
3. **Login PKCE** (sostituisce la dev key).
4. **Rifinitura**: tocchi Material 3 Expressive, stati loading/errore, più test.
