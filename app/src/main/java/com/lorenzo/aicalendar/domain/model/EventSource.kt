package com.lorenzo.aicalendar.domain.model

/** How an event entered the agenda — useful for analytics and for flagging AI-created events. */
enum class EventSource {
    /** Created/edited by hand in the UI. */
    MANUAL,

    /** Extracted from typed natural-language input. */
    AI_TEXT,

    /** Extracted from voice input (speech-to-text → extractor). */
    AI_VOICE,

    /** Read-only, mirrored from the device's system calendar (Google/Samsung) via CalendarContract. */
    SYSTEM,
}
