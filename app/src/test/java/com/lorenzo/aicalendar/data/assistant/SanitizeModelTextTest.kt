package com.lorenzo.aicalendar.data.assistant

import org.junit.Assert.assertEquals
import org.junit.Test

class SanitizeModelTextTest {

    @Test
    fun stripsPadToken_seenOnDevice() {
        assertEquals(
            "Tenga presente che questo impegno si sovrappone.",
            sanitizeModelText("Tenga presente che<pad> questo impegno si sovrappone."),
        )
    }

    @Test
    fun stripsChatMlAndSentinelTokens() {
        assertEquals("Ciao!", sanitizeModelText("Ciao!<|im_end|>"))
        assertEquals("Fatto.", sanitizeModelText("<s>Fatto.</s>"))
        assertEquals("Ok va bene", sanitizeModelText("Ok <unk> va bene"))
    }

    @Test
    fun leavesNormalTextAndNewlinesAlone() {
        val text = "Prima riga.\nSeconda riga con < e > normali (2 < 3)."
        assertEquals(text, sanitizeModelText(text))
    }
}
