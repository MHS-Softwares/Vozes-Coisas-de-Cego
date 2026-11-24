package com.mhs.vozescoisasdecego

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TextosTest {
    @Test
    fun juntaFrasesCurtas() {
        val f = Textos.frases("Oi. Tudo bem? Que bom ver você por aqui hoje.")
        assertEquals(1, f.size)
    }

    @Test
    fun separaFrasesLongas() {
        val a = "Esta é uma frase bem comprida para passar do mínimo."
        val b = "Esta é outra frase também comprida o bastante."
        val f = Textos.frases("$a $b")
        assertEquals(listOf(a, b), f)
    }

    @Test
    fun descartaSoPontuacao() {
        assertTrue(Textos.frases("... ?!").isEmpty())
        assertTrue(Textos.frases("   ").isEmpty())
    }

    @Test
    fun quebraTextoSemPontuacao() {
        val longo = "palavra ".repeat(200)
        val f = Textos.frases(longo)
        assertTrue(f.size > 1)
        assertTrue(f.all { it.length <= 300 })
    }
}
