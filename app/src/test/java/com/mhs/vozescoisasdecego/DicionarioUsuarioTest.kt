package com.mhs.vozescoisasdecego

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DicionarioUsuarioTest {
    @Test
    fun listaSoAsLinhasValidasEGuardaOIndiceDaLinha() {
        val texto = "# meu dicionario\n\nmega=méga\nsem igual\n=vazio\nsó=\ncone de festa = festa "
        val l = DicionarioUsuario.listar(texto)
        assertEquals(listOf("mega", "cone de festa"), l.map { it.palavra })
        assertEquals(listOf("méga", "festa"), l.map { it.pronuncia })
        assertEquals(listOf(2, 6), l.map { it.linha })
    }

    @Test
    fun textoVazioNaoTemPalavras() {
        assertTrue(DicionarioUsuario.listar("").isEmpty())
    }

    @Test
    fun adicionarPoeNoFimEDicionarioAplicarEntende() {
        val t = DicionarioUsuario.adicionar("", "mega", "méga")
        assertEquals("mega=méga", t)
        val t2 = DicionarioUsuario.adicionar(t, "RHVoice", "érre agá vóice")
        assertEquals("mega=méga\nRHVoice=érre agá vóice", t2)
        // o motor de correcao usa o mesmo texto
        assertEquals("Abra o méga", Dicionario.aplicar("Abra o mega", t2, false))
    }

    @Test
    fun adicionarPalavraQueJaExisteTrocaAPronunciaEmVezDeDuplicar() {
        val t = DicionarioUsuario.adicionar("mega=méga\noutra=ou-tra", "MEGA", "mêga")
        assertEquals(listOf("MEGA", "outra"), DicionarioUsuario.listar(t).map { it.palavra })
        assertEquals("mêga", DicionarioUsuario.listar(t)[0].pronuncia)
    }

    @Test
    fun adicionarPreservaComentariosEtiraLinhasVaziasDoFim() {
        val t = DicionarioUsuario.adicionar("# comentario\nmega=méga\n\n\n", "nova", "nôva")
        assertEquals("# comentario\nmega=méga\nnova=nôva", t)
    }

    @Test
    fun editarTrocaAsDuasPartes() {
        val base = "# c\nmega=méga\noutra=ou-tra"
        val e = DicionarioUsuario.listar(base)[0]
        val t = DicionarioUsuario.editar(base, e.linha, "mega pro", "méga pró")
        assertEquals("# c\nmega pro=méga pró\noutra=ou-tra", t)
    }

    @Test
    fun editarParaUmaPalavraQueJaExisteFicaComUmaSoEntrada() {
        val base = "a=1\nb=2\nc=3"
        val t = DicionarioUsuario.editar(base, 0, "c", "novo")
        assertEquals(listOf("c" to "novo", "b" to "2"), DicionarioUsuario.listar(t).map { it.palavra to it.pronuncia })
    }

    @Test
    fun removerTiraSoAQuelaLinha() {
        val base = "# c\nmega=méga\noutra=ou-tra"
        val e = DicionarioUsuario.listar(base).first { it.palavra == "mega" }
        assertEquals("# c\noutra=ou-tra", DicionarioUsuario.remover(base, e.linha))
    }

    @Test
    fun validarPedeOsDoisCamposESoRecusaOQueQuebraOFormato() {
        assertNotNull(DicionarioUsuario.validar("", "x"))
        assertNotNull(DicionarioUsuario.validar("   ", "x"))
        assertNotNull(DicionarioUsuario.validar("x", ""))
        assertNotNull(DicionarioUsuario.validar("a=b", "x"))
        assertNotNull(DicionarioUsuario.validar("#tag", "x"))
        assertNull(DicionarioUsuario.validar("mercado pago", "mercádo págo"))
        assertNull(DicionarioUsuario.validar("x", "a=b")) // o igual so atrapalha na palavra, nao na pronuncia
    }

    @Test
    fun quebrasDeLinhaNosCamposVirmEspaco() {
        val t = DicionarioUsuario.adicionar("", "duas\npalavras", "duas\r\npalavras")
        assertEquals("duas palavras=duas palavras", t)
        assertEquals(1, DicionarioUsuario.listar(t).size)
    }

    @Test
    fun pronunciaComIgualFicaInteira() {
        val t = DicionarioUsuario.adicionar("", "x", "a=b")
        assertEquals("a=b", DicionarioUsuario.listar(t)[0].pronuncia)
    }
}
