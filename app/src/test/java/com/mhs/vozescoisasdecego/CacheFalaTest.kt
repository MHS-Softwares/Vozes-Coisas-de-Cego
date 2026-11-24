package com.mhs.vozescoisasdecego

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class CacheFalaTest {
    @get:Rule
    val pasta = TemporaryFolder()

    private fun onda(n: Int, base: Int = 1) = ShortArray(n) { (it * base % 30000 - 15000).toShort() }

    @Test
    fun guardaEPegaNaMemoriaEDepoisDoReinicioNoDisco() {
        val raiz = pasta.newFolder("cache")
        val c = CacheFala(raiz, "v1")
        val a = onda(5000, 7)
        assertNull(c.get("pt|botão"))
        assertFalse(c.contem("pt|botão"))
        c.put("pt|botão", a)
        assertTrue(c.contem("pt|botão"))
        assertArrayEquals(a, c.get("pt|botão"))
        c.esperarDisco()
        assertEquals(1, c.quantosNoDisco())

        // "reiniciar o app": cache novo, mesma pasta -> le do disco
        val c2 = CacheFala(raiz, "v1")
        assertTrue(c2.contem("pt|botão"))
        assertArrayEquals(a, c2.get("pt|botão"))
    }

    @Test
    fun versaoNovaApagaOCacheAntigo() {
        val raiz = pasta.newFolder("cache2")
        val c = CacheFala(raiz, "v1")
        c.put("pt|voltar", onda(2000))
        c.esperarDisco()
        assertEquals(1, c.quantosNoDisco())
        val c2 = CacheFala(raiz, "v2")
        assertFalse(c2.contem("pt|voltar"))
        assertNull(c2.get("pt|voltar"))
    }

    @Test
    fun memoriaCheiaDescartaOsMenosUsados() {
        val raiz = pasta.newFolder("cache3")
        // limite de memoria de 20 mil bytes = 10 mil amostras; sem disco sobrando para nao confundir
        val c = CacheFala(raiz, "v1", limiteMemoria = 20_000, limiteDisco = 10_000_000)
        c.put("a", onda(6000, 1))
        c.put("b", onda(6000, 2))
        c.put("c", onda(6000, 3))
        c.esperarDisco()
        // todos continuam acessiveis (os antigos voltam do disco)
        assertNotNull(c.get("a"))
        assertNotNull(c.get("b"))
        assertArrayEquals(onda(6000, 3), c.get("c"))
    }

    @Test
    fun discoCheioApagaOsMaisAntigos() {
        val raiz = pasta.newFolder("cache4")
        val c = CacheFala(raiz, "v1", limiteMemoria = 1_000_000, limiteDisco = 30_000)
        for (i in 0 until 8) {
            c.put("k$i", onda(5000, i + 1)) // 10 mil bytes cada
            c.esperarDisco()
            Thread.sleep(15)
        }
        assertTrue("sobraram ${c.quantosNoDisco()}", c.quantosNoDisco() <= 3)
    }

    @Test
    fun arquivoCorrompidoNaoQuebra() {
        val raiz = pasta.newFolder("cache5")
        val c = CacheFala(raiz, "v1")
        c.put("x", onda(1000))
        c.esperarDisco()
        val arq = java.io.File(raiz, "v1").listFiles()!!.first { it.name.endsWith(".pcm") }
        arq.writeBytes(byteArrayOf(1, 2, 3)) // tamanho impar
        val c2 = CacheFala(raiz, "v1")
        assertNull(c2.get("x"))
    }

    @Test
    fun shortBuilderJuntaEConverte() {
        val b = ShortBuilder()
        b.add(floatArrayOf(0f, 0.5f, -0.5f), 3)
        b.add(floatArrayOf(2f, -2f), 2) // passa de 1: limita
        assertArrayEquals(shortArrayOf(0, 16383, -16383, 32767, -32768), b.toArray())
    }
}
