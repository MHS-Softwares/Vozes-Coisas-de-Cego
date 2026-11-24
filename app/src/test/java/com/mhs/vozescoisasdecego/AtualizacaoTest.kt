package com.mhs.vozescoisasdecego

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AtualizacaoTest {
    private fun resposta(
        tag: String = "v1.1",
        draft: Boolean = false,
        prerelease: Boolean = false,
        arquivos: String = """[
            {"name": "Manual das Vozes Coisas de Cego.txt", "browser_download_url": "https://github.com/x/y/releases/download/v1.1/Manual.txt", "size": 9000},
            {"name": "Vozes-Coisas-de-Cego-1.1.apk", "browser_download_url": "https://github.com/x/y/releases/download/v1.1/Vozes-Coisas-de-Cego-1.1.apk", "size": 103400000}
        ]""",
    ) = """{"tag_name": "$tag", "draft": $draft, "prerelease": $prerelease, "body": "  Voz da Daiane e atualizacao pelo app.  ", "assets": $arquivos}"""

    @Test
    fun versoesSaoComparadasNumeroANumero() {
        assertTrue(Atualizacao.comparar("1.10", "1.9") > 0)
        assertTrue(Atualizacao.comparar("1.1", "1.0") > 0)
        assertTrue(Atualizacao.comparar("1.0", "1.1") < 0)
        assertEquals(0, Atualizacao.comparar("v1.1", "1.1.0"))
        assertEquals(0, Atualizacao.comparar("1.2", "1.2"))
        assertTrue(Atualizacao.comparar("2.0", "1.99.99") > 0)
    }

    @Test
    fun sufixosEPrefixosNaoAtrapalham() {
        assertEquals(listOf(1, 10, 2), Atualizacao.numeros("v1.10.2-beta"))
        assertEquals(listOf(1, 0), Atualizacao.numeros(" V1.0 "))
        assertEquals(emptyList<Int>(), Atualizacao.numeros("beta"))
    }

    @Test
    fun soHaNovaQuandoAPublicadaEMaior() {
        assertTrue(Atualizacao.haNova("1.0", "v1.1"))
        assertFalse(Atualizacao.haNova("1.1", "v1.1"))
        assertFalse(Atualizacao.haNova("1.2", "v1.1"))
        assertFalse(Atualizacao.haNova("1.0", "semana-que-vem"))
        assertFalse(Atualizacao.haNova("1.0", ""))
    }

    @Test
    fun leAVersaoEOApkDaResposta() {
        val l = Atualizacao.interpretar(resposta())
        assertNotNull(l)
        assertEquals("1.1", l!!.versao)
        assertEquals("Vozes-Coisas-de-Cego-1.1.apk", l.nomeApk)
        assertTrue(l.urlApk.startsWith("https://github.com/"))
        assertEquals(103_400_000L, l.tamanho)
        assertEquals("Voz da Daiane e atualizacao pelo app.", l.notas)
    }

    @Test
    fun rascunhoEPreVersaoNaoSaoOferecidos() {
        assertNull(Atualizacao.interpretar(resposta(draft = true)))
        assertNull(Atualizacao.interpretar(resposta(prerelease = true)))
    }

    @Test
    fun semApkOuSemNumeroNaoHaAtualizacao() {
        assertNull(Atualizacao.interpretar(resposta(arquivos = "[]")))
        assertNull(
            Atualizacao.interpretar(
                resposta(arquivos = """[{"name": "Manual.txt", "browser_download_url": "https://x/y/Manual.txt", "size": 1}]"""),
            ),
        )
        assertNull(Atualizacao.interpretar(resposta(tag = "teste")))
        assertNull(Atualizacao.interpretar("""{"tag_name": "v1.1"}"""))
    }

    @Test
    fun soBaixaPorHttps() {
        val http = resposta(
            arquivos = """[{"name": "a.apk", "browser_download_url": "http://exemplo.com/a.apk", "size": 5}]""",
        )
        assertNull(Atualizacao.interpretar(http))
    }

    @Test
    fun apkEmMaiusculasTambemServe() {
        val l = Atualizacao.interpretar(
            resposta(arquivos = """[{"name": "App.APK", "browser_download_url": "https://x/y/App.APK", "size": 7}]"""),
        )
        assertEquals("App.APK", l!!.nomeApk)
    }

    @Test
    fun otempoQueFaltaEDitoPorExtenso() {
        assertEquals("45 segundos", Atualizacao.descreverTempo(45))
        assertEquals("1 minuto e 20 segundos", Atualizacao.descreverTempo(80))
        assertEquals("2 minutos", Atualizacao.descreverTempo(120))
        assertEquals("1 hora e 2 minutos", Atualizacao.descreverTempo(3720))
        assertEquals("1 segundo", Atualizacao.descreverTempo(0))
    }

    @Test
    fun oAndamentoSoDizOTempoDepoisDeAlgunsSegundos() {
        assertEquals("40 por cento", Atualizacao.andamento(40, 100, 2000))
        // 40% em 20 s: faltam 30 s
        assertEquals("40 por cento, 30 segundos restantes", Atualizacao.andamento(40, 100, 20_000))
        assertEquals("0 por cento", Atualizacao.andamento(0, 100, 10_000))
        assertEquals("0 por cento", Atualizacao.andamento(10, 0, 10_000))
    }

    @Test
    fun otamanhoEDitoEmMegabytes() {
        assertEquals("99 MB", Atualizacao.tamanhoLegivel(103_400_000))
        assertEquals("500 KB", Atualizacao.tamanhoLegivel(512_000))
        assertEquals("", Atualizacao.tamanhoLegivel(0))
    }
}
