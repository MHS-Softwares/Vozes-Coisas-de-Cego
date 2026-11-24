package com.mhs.vozescoisasdecego

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EmojisTest {
    private val tabela = TabelaEmojis.deLinhas(
        listOf(
            "😀\trosto risonho",
            "😂\trosto chorando de rir",
            "❤️\tcoração vermelho",          // com seletor de variacao
            "👍\tpolegar para cima",
            "👍🏽\tpolegar para cima: pele morena",
            "👨‍👩‍👧\tfamília: homem, mulher, menina",
            "🇧🇷\tbandeira: Brasil",
            "1️⃣\ttecla: 1",
            "→\tseta para a direita",
        ).asSequence(),
    )

    @Test
    fun trocaOEmojiPeloNome() {
        assertEquals("Oi rosto risonho tudo bem", tabela.substituir("Oi 😀 tudo bem"))
        assertEquals("rosto risonho", tabela.substituir("😀"))
        assertEquals("Que legal polegar para cima", tabela.substituir("Que legal👍"))
    }

    @Test
    fun seletorDeVariacaoNaoAtrapalha() {
        assertEquals("coração vermelho", tabela.substituir("❤️"))
        assertEquals("coração vermelho", tabela.substituir("❤"))
    }

    @Test
    fun usaOMaiorNomePossivel() {
        assertEquals("polegar para cima: pele morena", tabela.substituir("👍🏽"))
        assertEquals("família: homem, mulher, menina", tabela.substituir("👨‍👩‍👧"))
        assertEquals("bandeira: Brasil", tabela.substituir("🇧🇷"))
        assertEquals("tecla: 1", tabela.substituir("1️⃣"))
    }

    @Test
    fun emojisRepetidosViramVezes() {
        assertEquals("rosto chorando de rir, três vezes", tabela.substituir("😂😂😂"))
        assertEquals("rosto chorando de rir, duas vezes ha", tabela.substituir("😂😂 ha"))
        assertEquals("rosto chorando de rir, muitas vezes", tabela.substituir("😂".repeat(15)))
        assertEquals("rosto chorando de rir rosto risonho", tabela.substituir("😂 😀"))
        // desligado: cada um por extenso
        assertEquals("rosto chorando de rir rosto chorando de rir", tabela.substituir("😂😂", repetidos = false))
    }

    @Test
    fun emojiSemNomeSomeETextoComumFicaIgual() {
        assertEquals("a b", tabela.substituir("a 🦖 b"))
        assertEquals("Olá, tudo bem? 123 é 5%", tabela.substituir("Olá, tudo bem? 123 é 5%"))
        assertFalse(tabela.temEmoji("texto simples com acentos çã"))
        assertTrue(tabela.temEmoji("tem 😀"))
        assertEquals("seta para a direita próximo", tabela.substituir("→ próximo"))
    }

    @Test
    fun tabelaVaziaSoDescartaEmojis() {
        assertEquals("oi tchau", TabelaEmojis.VAZIA.substituir("oi 😀 tchau"))
        assertEquals("sem emoji", TabelaEmojis.VAZIA.substituir("sem emoji"))
    }
}
