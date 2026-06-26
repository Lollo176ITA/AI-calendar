# AI-calendar

Agenda Android intelligente basata su AI + algoritmi di scheduling.

L'app aiuta a gestire la giornata: l'utente descrive la propria routine in linguaggio
naturale, aggiunge eventi parlando/scrivendo (es. "il 14 ho il medico alle 15"), e un
motore di scheduling inserisce le attività, rileva i conflitti, riorganizza quando serve
e notifica l'utente.

## Stato

🚧 **Fase di design (brainstorming).** Nessun codice dell'app ancora scritto.

## Stack previsto

- **Android nativo** — Kotlin + Jetpack Compose
- **Material 3 Expressive**
- **Local-first** (dati sul telefono), architettura modulare pronta a scalare
- AI dietro un'interfaccia `AiService`; dati dietro un'interfaccia repository

## Sottosistemi

1. Onboarding / profilo utente
2. Modello della routine (ricorrenze, settimane alternate)
3. AI in linguaggio naturale (creazione eventi/task)
4. Motore di scheduling (conflitti, riorganizzazione)
5. Notifiche
6. UI Material 3 Expressive
