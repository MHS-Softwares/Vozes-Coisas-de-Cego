package com.mhs.vozescoisasdecego

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class DicionarioTest {
    @Test
    fun gemEGensViramJ() {
        assertEquals("imajens e vídeos", Dicionario.aplicar("imagens e vídeos"))
        assertEquals("Imajens e Vídeos", Dicionario.aplicar("Imagens e Vídeos"))
        assertEquals("Mensajem nova", Dicionario.aplicar("Mensagem nova"))
        assertEquals("viajens", Dicionario.aplicar("viagens"))
        assertEquals("IMAJENS", Dicionario.aplicar("IMAGENS"))
    }

    @Test
    fun naoMexeEmPalavrasQueNaoSaoGemGens() {
        for (p in listOf("agenda", "gente", "gemada", "geral", "Mega", "Mercado Pago", "gesto", "vantagem?")) {
            val esperado = if (p == "vantagem?") "vantajem?" else p
            assertEquals(esperado, Dicionario.aplicar(p))
        }
    }

    @Test
    fun rhvoiceVirouLetras() {
        assertEquals("érre agá vóice", Dicionario.aplicar("RHVoice"))
        assertEquals("Abrir érre agá vóice agora", Dicionario.aplicar("Abrir RHVoice agora"))
    }

    @Test
    fun letraSozinhaFalaONomeDaLetra() {
        assertEquals("gê", Dicionario.aplicar("g"))
        assertEquals("agá", Dicionario.aplicar(" H "))
        assertEquals("érre", Dicionario.aplicar("r"))
        // dentro de uma frase, a letra nao muda
        assertEquals("a casa", Dicionario.aplicar("a casa"))
    }

    @Test
    fun entradasDoUsuario() {
        val dic = "mega=méga\n# comentario\nmercado pago = mercádo págo\n\nsemigual\nvazio="
        assertEquals(listOf("mega" to "méga", "mercado pago" to "mercádo págo"), Dicionario.entradas(dic))
        assertEquals("abrir méga", Dicionario.aplicar("abrir Mega", dic))
        assertEquals("Abrir mercádo págo", Dicionario.aplicar("Abrir Mercado Pago", dic))
        // palavra inteira: "megabyte" nao e "mega"
        assertEquals("megabyte", Dicionario.aplicar("megabyte", dic))
    }

    @Test
    fun regrasDesligadasNaoMexem() {
        assertEquals("imagens", Dicionario.aplicar("imagens", regras = false))
        assertEquals("RHVoice", Dicionario.aplicar("RHVoice", regras = false))
        assertEquals("g", Dicionario.aplicar("g", regras = false))
    }

    @Test
    fun chaveIgnoraMaiusculasEEspacos() {
        assertEquals(Dicionario.chave("pt", "Botão  Enviar"), Dicionario.chave("pt", " botão enviar "))
        assertNotEquals(Dicionario.chave("pt", "botão"), Dicionario.chave("en", "botão"))
    }

    @Test
    fun cegoSaiComEAberto() {
        assertEquals("Vozes Coisas de cégo", Dicionario.aplicar("Vozes Coisas de Cego"))
        assertEquals("os cégos e as céga", Dicionario.aplicar("os cegos e as cega".replace("cega", "cega")))
        assertEquals("cégas", Dicionario.aplicar("CEGAS"))
        // so a palavra inteira: "cegueira" nao muda
        assertEquals("cegueira", Dicionario.aplicar("cegueira"))
        // com as regras desligadas nada muda
        assertEquals("cego", Dicionario.aplicar("cego", "", false))
    }
}
