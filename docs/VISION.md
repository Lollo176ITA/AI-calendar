# Visione — AI Calendar

> **Parlo al telefono e la mia agenda si organizza da sola.**

Questa frase è il metro di ogni scelta di prodotto e di codice. Se una feature non
avvicina l'app a questa esperienza, non è prioritaria.

## L'esperienza obiettivo

1. Apro l'app (o dico "Hey Google, apri AI Calendar") e posso **parlare subito**:
   nessun menu da attraversare, il microfono è a portata di pollice.
2. Dico cose come *"il 14 ho il medico alle 15"*, *"sposta la palestra a giovedì"*,
   *"che giornata ho domani?"* — e l'assistente capisce, agisce e **risponde a voce**.
3. Gli eventi non vivono in un silo: finiscono nel **calendario Google del telefono**
   e si sincronizzano ovunque. L'app vede anche gli impegni già presenti e li usa
   per rilevare conflitti e riorganizzare.
4. La grafica è al servizio della conversazione: **agenda + assistente in un'unica
   schermata**, non un calendario con una chat nascosta dietro un bottone.

## Principi tecnici (già in vigore)

- **L'AI capisce e struttura, l'app è la verità.** Il modello interpreta il linguaggio
  e produce dati strutturati (eventi, RRULE); il codice deterministico salva, espande
  le ricorrenze, rileva i conflitti e fa scattare i promemoria.
- **Local-first.** I dati stanno sul telefono (Room + calendario di sistema);
  il cloud serve solo per l'intelligenza linguistica.
- **Niente lock-in di modello.** L'assistente è dietro interfacce di dominio
  (`domain/assistant/`); oggi OpenRouter con modelli free, domani qualunque provider.

## Tappe

| Tappa | Cosa | Stato |
|---|---|---|
| 1 | Assistente testuale che crea/sposta/cancella eventi, ricorrenze RRULE, promemoria | ✅ fatta |
| 2 | Lettura del calendario di sistema (contesto AI + conflitti) | ✅ fatta |
| 3 | **Voce in-app**: dettatura (SpeechRecognizer) + risposte vocali (TTS) | ✅ questa iterazione |
| 4 | **Home unificata**: agenda + barra assistente sempre visibile | ✅ questa iterazione |
| 5 | **Scrittura sul calendario di sistema** (eventi veri su Google Calendar, senza OAuth) | ✅ questa iterazione |
| 6 | App shortcut "Parla con l'assistente" (aggancio Assistant/Gemini) | ✅ questa iterazione |
| 7 | **Scheduling deterministico**: conflitti + slot liberi suggeriti dall'algoritmo, non dall'AI | ✅ questa iterazione |
| 8 | **Eval suite**: richieste-tipo con attese verificabili, confronto multi-modello, ciclo di affinamento del prompt ([docs/EVAL.md](EVAL.md)) | ✅ questa iterazione |
| 9 | **AI del telefono**: Gemini Nano on-device (ML Kit Prompt API) nella catena dell'assistente + opzione "Elabora solo con AI locali" + selezionabile come app assistente di sistema (ACTION_ASSIST) | ✅ questa iterazione |
| 10 | Integrazione profonda con Gemini (App Functions, `androidx.appfunctions`) | ⏳ quando l'API si stabilizza |
| 11 | Login per-utente (PKCE) al posto della chiave dev | ⏳ pianificata |
| 12 | Scheduling proattivo: l'app propone riorganizzazioni dell'intera giornata (es. con un constraint solver tipo Google OR-Tools) | 💡 idea |
