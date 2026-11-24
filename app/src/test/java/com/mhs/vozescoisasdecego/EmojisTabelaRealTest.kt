package com.mhs.vozescoisasdecego

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Testa com a tabela de verdade (assets/emojis_pt.tsv, gerada do CLDR). */
class EmojisTabelaRealTest {
    private val arquivo = File("src/main/assets/emojis_pt.tsv")
    private val tabela by lazy { TabelaEmojis.deLinhas(arquivo.readLines(Charsets.UTF_8).asSequence()) }

    @Test
    fun tabelaTemMilharesDeEmojis() {
        assertTrue("arquivo ausente: ${arquivo.absolutePath}", arquivo.isFile)
        assertTrue("so ${tabela.tamanho}", tabela.tamanho > 3000)
    }

    @Test
    fun emojisDoDiaADia() {
        assertEquals("rosto risonho", tabela.substituir("😀"))
        assertEquals("Boa noite coração vermelho", tabela.substituir("Boa noite ❤️"))
        assertEquals("polegar para cima: pele morena", tabela.substituir("👍🏽"))
        assertEquals("bandeira: Brasil", tabela.substituir("🇧🇷"))
        assertEquals("rosto chorando de rir, três vezes", tabela.substituir("😂😂😂"))
        assertEquals("mãos juntas", tabela.substituir("🙏"))
        assertEquals("fogo", tabela.substituir("🔥"))
    }

    @Test
    fun sequenciasComplexas() {
        assertEquals("família: homem, mulher e menina", tabela.substituir("👨‍👩‍👧"))
        assertEquals("tecla: 1", tabela.substituir("1️⃣"))
        // numero comum e texto comum nao mudam
        assertEquals("Chegamos às 10 horas, ok? 100%", tabela.substituir("Chegamos às 10 horas, ok? 100%"))
    }

    @Test
    fun cargaERapida() {
        val t0 = System.nanoTime()
        tabela.tamanho
        val ms = (System.nanoTime() - t0) / 1_000_000
        assertTrue("carga levou $ms ms", ms < 1500)
        val t1 = System.nanoTime()
        repeat(200) { tabela.substituir("Oi 😀😀 tudo bem? 👍🏽 vamos ❤️ 🇧🇷 e mais texto normal aqui") }
        val porTexto = (System.nanoTime() - t1) / 200 / 1000
        assertTrue("cada texto levou $porTexto us", porTexto < 5000)
    }
}
