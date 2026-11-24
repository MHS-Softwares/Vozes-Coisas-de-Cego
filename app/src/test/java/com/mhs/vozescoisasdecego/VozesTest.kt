package com.mhs.vozescoisasdecego

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VozesTest {
    @Test
    fun listaTemAsTresVozesNaOrdemCerta() {
        assertEquals(listOf("Meriellen", "Michel", "Daiane"), Vozes.LISTA)
        assertEquals("Meriellen", Vozes.PADRAO)
    }

    @Test
    fun validaAceitaMaiusculasEEspacosEVoltaAoPadraoSeNaoExistir() {
        assertEquals("Meriellen", Vozes.valida("meriellen"))
        assertEquals("Meriellen", Vozes.valida("  MERIELEN "))
        assertEquals("Michel", Vozes.valida("Michel"))
        assertEquals("Daiane", Vozes.valida("daiane"))
        assertEquals("Daiane", Vozes.valida("  DAIANE "))
        assertEquals("Meriellen", Vozes.valida("Outra"))
        assertEquals("Meriellen", Vozes.valida(null))
        assertEquals("Meriellen", Vozes.valida(""))
    }

    @Test
    fun onomeAntigoDaMeriellenVirouOnomeNovo() {
        assertEquals("Meriellen", Vozes.valida("Merielen"))
        assertEquals("Meriellen", Vozes.valida("merielen"))
    }

    @Test
    fun limpaSoOsCachesQueNaoSaoDeNenhumaVozAtual() {
        val base = java.nio.file.Files.createTempDirectory("vozes-limpeza").toFile()
        try {
            for (nome in listOf("fala-meriellen", "fala-michel", "fala-merielen", "fala-outra", "outra-coisa")) File(base, nome).mkdirs()
            Vozes.limparCachesAntigos(base)
            assertEquals(listOf("fala-meriellen", "fala-michel", "outra-coisa"), base.list()!!.sorted())
        } finally {
            base.deleteRecursively()
        }
    }

    @Test
    fun cadaVozTemASuaPastaDeCache() {
        val base = File("cache")
        val a = Vozes.pastaDoCache(base, "Michel")
        val b = Vozes.pastaDoCache(base, "Meriellen")
        assertNotEquals(a, b)
        assertEquals(a, Vozes.pastaDoCache(base, "michel"))
    }

    @Test
    fun cachesDeVozesDiferentesNaoSeMisturam() {
        val base = java.nio.file.Files.createTempDirectory("vozes-cache").toFile()
        try {
            val michel = CacheFala(Vozes.pastaDoCache(base, "Michel"), "v1")
            val meriellen = CacheFala(Vozes.pastaDoCache(base, "Meriellen"), "v1")
            val chave = Dicionario.chave("pt", "Botão")
            michel.put(chave, shortArrayOf(1, 2, 3))
            michel.esperarDisco()
            assertTrue(michel.contem(chave))
            assertFalse(meriellen.contem(chave))
            assertNull(meriellen.get(chave))
            // um cache novo da mesma voz (depois de reiniciar o servico) ainda acha o audio no disco
            val michelDeNovo = CacheFala(Vozes.pastaDoCache(base, "Michel"), "v1")
            assertEquals(listOf<Short>(1, 2, 3), michelDeNovo.get(chave)?.toList())
            // abrir o cache de uma voz nao apaga o da outra
            assertTrue(michelDeNovo.contem(chave))
        } finally {
            base.deleteRecursively()
        }
    }

    @Test
    fun apresentacaoDizOnomeCertoEOProjetoDaMeriellen() {
        assertEquals("Olá! Eu sou o Michel. Esta é a minha voz no seu celular.", Vozes.apresentacao("por", "Michel"))
        val m = Vozes.apresentacao("por", "Meriellen")
        assertTrue(m, m.startsWith("Olá! Eu sou a Meriellen, do projeto Coisas de Cego."))
        assertTrue(Vozes.apresentacao("eng", "Meriellen").contains("Meriellen"))
        assertTrue(Vozes.apresentacao("deu", "Michel").contains("Michel"))
        assertTrue(Vozes.apresentacao("xyz", "Meriellen").startsWith("Olá!"))
        assertEquals("Olá! Eu sou a Daiane. Esta é a minha voz no seu celular.", Vozes.apresentacao("por", "Daiane"))
        assertTrue(Vozes.apresentacao("spa", "Daiane").contains("Daiane"))
    }

    // ---- troca de voz em tempo real: so manda trocar quando a voz pedida e outra ----

    @Test
    fun naoTrocaSeJaEstaNaVozPedida() {
        val v = VozAtiva()
        v.definir("Michel")
        var chamadas = 0
        assertTrue(v.trocarSePreciso("Michel") { chamadas++; true })
        assertEquals(0, chamadas)
    }

    @Test
    fun trocaUmaVezEDepoisReconheceAVozNova() {
        val v = VozAtiva()
        v.definir("Michel")
        val aplicadas = mutableListOf<String>()
        assertTrue(v.trocarSePreciso("Meriellen") { aplicadas += it; true })
        assertTrue(v.trocarSePreciso("Meriellen") { aplicadas += it; true })
        assertEquals(listOf("Meriellen"), aplicadas)
        assertEquals("Meriellen", v.atual)
        // e volta
        assertTrue(v.trocarSePreciso("Michel") { aplicadas += it; true })
        assertEquals(listOf("Meriellen", "Michel"), aplicadas)
    }

    @Test
    fun seATrocaFalharContinuaNaVozDeAntesETentaDeNovoNaProxima() {
        val v = VozAtiva()
        v.definir("Michel")
        var tentativas = 0
        assertFalse(v.trocarSePreciso("Meriellen") { tentativas++; false })
        assertEquals("Michel", v.atual)
        assertTrue(v.trocarSePreciso("Meriellen") { tentativas++; true })
        assertEquals(2, tentativas)
        assertEquals("Meriellen", v.atual)
    }

    @Test
    fun depoisDeLiberarOMotorAVozPrecisaSerAplicadaDeNovo() {
        val v = VozAtiva()
        v.definir("Meriellen")
        v.definirDesconhecida()
        var chamadas = 0
        assertTrue(v.trocarSePreciso("Meriellen") { chamadas++; true })
        assertEquals(1, chamadas)
    }
}
