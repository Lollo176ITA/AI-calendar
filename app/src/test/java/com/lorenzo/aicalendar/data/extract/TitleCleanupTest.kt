package com.lorenzo.aicalendar.data.extract

import org.junit.Assert.assertEquals
import org.junit.Test

class TitleCleanupTest {

    @Test
    fun `drops italian article and temporal filler`() {
        assertEquals("Medico", cleanTitle("il ho il medico alle"))
    }

    @Test
    fun `keeps meaningful words like con and names`() {
        assertEquals("Cena con Anna", cleanTitle("cena con Anna"))
    }

    @Test
    fun `keeps prepositions inside a title`() {
        assertEquals("Appuntamento dal dentista", cleanTitle("ho appuntamento dal dentista"))
    }

    @Test
    fun `drops bare numbers and times`() {
        assertEquals("Medico", cleanTitle("15 medico 14:30"))
    }

    @Test
    fun `blank input falls back to a default title`() {
        assertEquals("Evento", cleanTitle("   "))
    }
}
