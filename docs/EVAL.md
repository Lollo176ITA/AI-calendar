# Eval dell'assistente — testare le risposte AI e affinare il prompt

L'harness sta in `app/src/test/java/com/lorenzo/aicalendar/eval/AssistantEvalTest.kt`:
una suite di **18 richieste utente realistiche in italiano** con attese verificabili in codice
(azione, date risolte, RRULE, numero di operazioni), eseguita in parallelo contro uno o più
modelli OpenRouter con una tabella comparativa finale.

È **opt-in**: senza chiave il test viene SALTATO (la CI non ne risente). La chiave si prende da
`OPENROUTER_API_KEY` (variabile d'ambiente) o da `openrouter.devKey` in `local.properties`
(la stessa già usata dall'app in debug).

## Come si lancia

```bash
# suite completa sui 3 modelli dell'app (qwen, gemma, nemotron)
OPENROUTER_API_KEY=sk-or-... ./gradlew :app:testDebugUnitTest --tests '*AssistantEvalTest*'

# un solo modello (più veloce, utile mentre si itera sul prompt)
EVAL_MODELS="qwen/qwen3-next-80b-a3b-instruct:free" OPENROUTER_API_KEY=... ./gradlew ...

# soglia di regressione: fallisce se il modello migliore scende sotto 12/15
EVAL_MIN_PASS=12 OPENROUTER_API_KEY=... ./gradlew ...
```

Il report compare nello stdout del test e in `app/build/reports/assistant-eval.txt`:

```
── qwen/qwen3-next-80b-a3b-instruct:free — 13/15 ──
  PASS pranzo-domani
  FAIL visita-mensile
       · manca l'ordinale mensile (atteso BYDAY=<n>SA, trovato 'FREQ=MONTHLY;BYDAY=SA')
  ...
── Riepilogo ──
  qwen/qwen3-next-80b-a3b-instruct:free   13/15
  google/gemma-4-26b-a4b-it:free          11/15
```

Note pratiche: il "adesso" della suite è fisso (martedì 7 lug 2026, Europe/Rome) così le date
relative ("domani", "lunedì prossimo") hanno risposte deterministiche. I modelli free hanno
rate-limit al minuto: la concorrenza è limitata a 3 chiamate e la suite completa richiede
qualche minuto.

## Cosa copre la suite

| Area | Casi |
|---|---|
| Date relative | domani, dopodomani, lunedì prossimo |
| Ricorrenze semplici | ogni lun+mer, ogni martedì con durata |
| Ricorrenze insidiose | "un sabato al mese" (ordinale!), ultimo venerdì, il 15 del mese |
| Routine multi-evento | lun-ven lavoro + sabato studio in un messaggio |
| Update/Delete con ref | sposta la riunione, cancella la palestra |
| Nessuna operazione | domanda sull'agenda, fuori ambito (meteo), dati mancanti |
| Campi | luogo, allDay, endDateTime |
| Regressioni dal telefono | coerenza di luogo (treno per Torino + pranzo a Napoli), data già passata ("il 14 maggio" detto a luglio), niente emoji nel reply (check trasversale), richiamo esaustivo dell'agenda ("che compleanni ho questo mese?" con titoli refusati) |

## Il ciclo di miglioramento del prompt

1. **Esegui** la suite e leggi i FAIL: ogni fallimento dice esattamente cosa si è rotto
   ("rrule non contiene BYMONTHDAY=15", "atteso ref=1, trovato null", …).
2. **Correggi** in uno di due posti:
   - `AssistantPrompts.kt` — la regola/l'esempio contrastivo per il pattern sbagliato
     (è già organizzato così: procedura RRULE numerata, esempi input→rrule, self-check);
   - `OpenRouterAssistant.kt` — una rete di sicurezza deterministica quando il modello sbaglia
     in modo sistematico (com'è già `fixMonthlyByDay` per l'ordinale mensile).
3. **Riesegui** e confronta i punteggi. Con `EVAL_MODELS` su un solo modello il giro è rapido.
4. Quando il punteggio ti soddisfa, fissa la soglia con `EVAL_MIN_PASS` nel tuo flusso locale:
   da lì in poi ogni modifica al prompt ha una rete di protezione contro le regressioni.

Il confronto tra modelli serve anche a scegliere l'**ordine della catena di fallback** in
`OPENROUTER_EVENT_MODELS` (OpenRouterDtos.kt): il primo della lista dovrebbe essere quello col
punteggio migliore.

### Auto-miglioramento con Claude

Il ciclo sopra è meccanico apposta: si può delegare. In una sessione Claude Code con la chiave
disponibile basta chiedere: *"esegui la eval, analizza i fallimenti, proponi una modifica ad
AssistantPrompts, riesegui e confronta"* — e iterare finché il punteggio converge. La suite fa
da funzione obiettivo; le attese verificabili impediscono i miglioramenti illusori.

## Aggiungere casi

Un caso nuovo = una riga in `dataset()`: id, messaggio utente, agenda opzionale (per
update/delete) e un check che restituisce la lista dei problemi (vuota = PASS). I casi migliori
nascono dalle conversazioni reali finite male: quando l'assistente sbaglia sul telefono,
trasforma quel messaggio in un caso e poi sistema il prompt finché non passa.
