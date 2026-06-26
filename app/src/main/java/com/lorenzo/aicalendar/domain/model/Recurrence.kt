package com.lorenzo.aicalendar.domain.model

/**
 * How an event repeats, as an RFC-5545 RRULE (the AI produces it; lib-recur expands it),
 * plus a short human-readable [label] for the UI (e.g. "Ogni primo sabato del mese").
 *
 * Examples: "FREQ=DAILY", "FREQ=WEEKLY;BYDAY=MO,WE", "FREQ=MONTHLY;BYDAY=1SA",
 * "FREQ=MONTHLY;BYDAY=-1FR" (last Friday), "FREQ=YEARLY".
 */
data class Recurrence(
    val rrule: String,
    val label: String,
)
