package com.lorenzo.aicalendar.ui.assistant

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale

/**
 * Reads assistant replies aloud with the platform [TextToSpeech] in Italian.
 * TTS initializes asynchronously, so a reply that arrives before the engine is
 * ready is parked in [pending] and spoken from the init callback. Owned by the
 * UI — call [shutdown] from a DisposableEffect.
 */
class TtsSpeaker(context: Context) {

    private var ready = false
    private var pending: String? = null

    private val tts: TextToSpeech = TextToSpeech(context) { status ->
        if (status == TextToSpeech.SUCCESS) {
            ready = true
            pending?.let { speak(it) }
        }
        pending = null
    }

    fun speak(text: String) {
        val clean = text.replace("⚠️", "Attenzione:").trim()
        if (clean.isEmpty()) return
        if (!ready) {
            pending = clean
            return
        }
        tts.language = Locale.ITALIAN
        tts.speak(clean, TextToSpeech.QUEUE_FLUSH, null, "assistant-reply")
    }

    fun shutdown() {
        tts.stop()
        tts.shutdown()
    }
}
