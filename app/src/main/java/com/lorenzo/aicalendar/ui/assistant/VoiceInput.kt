package com.lorenzo.aicalendar.ui.assistant

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import java.util.Locale

/**
 * Thin wrapper around the platform [SpeechRecognizer]: Italian dictation with streaming
 * partial results. Owned by the UI — create it in a `remember` and call [destroy] from a
 * DisposableEffect so the recognizer never leaks past the composable.
 */
class VoiceInputController(
    private val context: Context,
    private val onListeningChange: (Boolean) -> Unit,
    private val onPartial: (String) -> Unit,
    private val onFinal: (String) -> Unit,
    private val onErrorMessage: (String) -> Unit,
) {
    private var recognizer: SpeechRecognizer? = null

    /** Starts a dictation session (RECORD_AUDIO must already be granted). */
    fun start() {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            onErrorMessage("Il riconoscimento vocale non è disponibile su questo dispositivo.")
            return
        }
        stopInternal()
        recognizer = SpeechRecognizer.createSpeechRecognizer(context).also {
            it.setRecognitionListener(listener)
            it.startListening(recognizeIntent())
        }
        onListeningChange(true)
    }

    /** Ends the audio capture early; the recognizer still delivers what it heard. */
    fun stop() {
        recognizer?.stopListening()
    }

    fun destroy() = stopInternal()

    private fun stopInternal() {
        recognizer?.destroy()
        recognizer = null
        onListeningChange(false)
    }

    private fun recognizeIntent() = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.ITALIAN.toLanguageTag())
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
    }

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {}
        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() {}
        override fun onEvent(eventType: Int, params: Bundle?) {}

        override fun onPartialResults(partialResults: Bundle?) {
            firstResult(partialResults)?.takeIf { it.isNotBlank() }?.let(onPartial)
        }

        override fun onResults(results: Bundle?) {
            val text = firstResult(results)
            stopInternal()
            if (text.isNullOrBlank()) {
                onErrorMessage("Non ho sentito nulla, riprova.")
            } else {
                onFinal(text)
            }
        }

        override fun onError(error: Int) {
            stopInternal()
            when (error) {
                SpeechRecognizer.ERROR_NO_MATCH,
                SpeechRecognizer.ERROR_SPEECH_TIMEOUT,
                -> onErrorMessage("Non ho capito, riprova.")

                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS ->
                    onErrorMessage("Serve il permesso del microfono per usare la voce.")

                SpeechRecognizer.ERROR_RECOGNIZER_BUSY ->
                    onErrorMessage("Il microfono è occupato, riprova tra un attimo.")

                else -> onErrorMessage("Errore del riconoscimento vocale, riprova.")
            }
        }
    }

    private fun firstResult(bundle: Bundle?): String? =
        bundle?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
}
