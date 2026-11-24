package com.mhs.vozescoisasdecego

import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.io.File
import java.io.IOException
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicInteger

/** O Baixador contra um servidor de mentira que derruba a conexao, ignora o Range ou responde errado. */
class BaixadorTest {
    private lateinit var servidor: ServerSocket
    private lateinit var pasta: File
    private val conteudo = ByteArray(600_000) { (it * 31 % 251).toByte() }
    private val pedidos = AtomicInteger()
    private val ranges = java.util.Collections.synchronizedList(ArrayList<String?>())

    /** Quantos primeiros pedidos devem cair no meio (a conexao fecha depois de [corteBytes]). */
    @Volatile private var pedidosQueCaem = 0
    @Volatile private var corteBytes = 200_000
    @Volatile private var ignoraRange = false
    @Volatile private var sempreErro: Int? = null
    @Volatile private var conteudoServido: ByteArray = conteudo

    @Before
    fun abrir() {
        pasta = Files.createTempDirectory("baixador").toFile()
        servidor = ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))
        Thread {
            while (!servidor.isClosed) {
                val s = try { servidor.accept() } catch (e: IOException) { break }
                try { atender(s) } catch (_: IOException) { } finally { runCatching { s.close() } }
            }
        }.apply { isDaemon = true }.start()
    }

    private val fim = "\r\n"

    /** Um servidor HTTP de brinquedo: uma conexao por pedido, com Range, quedas e erros sob comando. */
    private fun atender(s: Socket) {
        val entrada = s.getInputStream().bufferedReader(Charsets.ISO_8859_1)
        var range: String? = null
        entrada.readLine() ?: return
        while (true) {
            val linha = entrada.readLine() ?: break
            if (linha.isEmpty()) break
            if (linha.startsWith("Range:", ignoreCase = true)) range = linha.substringAfter(":").trim()
        }
        val numero = pedidos.incrementAndGet()
        ranges.add(range)
        val saida = s.getOutputStream()
        fun semCorpo(codigo: Int, texto: String) {
            saida.write(("HTTP/1.1 $codigo $texto" + fim + "Content-Length: 0" + fim + "Connection: close" + fim + fim).toByteArray())
            saida.flush()
        }
        val erro = sempreErro
        if (erro != null) return semCorpo(erro, "Erro")
        val dados = conteudoServido
        val inicio = if (!ignoraRange && range != null) range.removePrefix("bytes=").removeSuffix("-").toLong() else 0L
        if (inicio >= dados.size) return semCorpo(416, "Range Not Satisfiable")
        val resto = dados.size - inicio
        val cabecalho = StringBuilder()
        if (inicio > 0) {
            cabecalho.append("HTTP/1.1 206 Partial Content" + fim + "Content-Range: bytes $inicio-${dados.size - 1}/${dados.size}" + fim)
        } else {
            cabecalho.append("HTTP/1.1 200 OK" + fim)
        }
        cabecalho.append("Content-Length: $resto" + fim + "Connection: close" + fim + fim)
        saida.write(cabecalho.toString().toByteArray())
        // a conexao cai no meio: o Content-Length prometido nao chega inteiro
        val quantos = if (numero <= pedidosQueCaem) minOf(corteBytes.toLong(), resto).toInt() else resto.toInt()
        saida.write(dados, inicio.toInt(), quantos)
        saida.flush()
    }

    @After
    fun fechar() {
        servidor.close()
        pasta.deleteRecursively()
    }

    private fun url() = "http://127.0.0.1:${servidor.localPort}/apk"
    private fun baixador(tentativas: Int = 5) = Baixador(tentativas = tentativas, esperar = {})

    @Test
    fun baixaOArquivoInteiro() {
        val destino = File(pasta, "a.apk.parcial")
        val avisos = ArrayList<Pair<Long, Long>>()
        baixador().baixar(url(), destino, conteudo.size.toLong()) { b, t -> avisos.add(b to t) }
        assertArrayEquals(conteudo, destino.readBytes())
        assertEquals(1, pedidos.get())
        assertEquals(conteudo.size.toLong() to conteudo.size.toLong(), avisos.last())
        assertNull(ranges[0])
    }

    @Test
    fun quandoACaiContinuaDeOndeParou() {
        pedidosQueCaem = 2
        corteBytes = 150_000
        val destino = File(pasta, "a.apk.parcial")
        baixador().baixar(url(), destino, conteudo.size.toLong()) { _, _ -> }
        assertArrayEquals(conteudo, destino.readBytes())
        assertEquals(3, pedidos.get())
        assertNull(ranges[0])
        assertEquals("bytes=150000-", ranges[1])
        assertEquals("bytes=300000-", ranges[2])
    }

    @Test
    fun continuaDeUmArquivoParcialQueJaExiste() {
        val destino = File(pasta, "a.apk.parcial")
        destino.writeBytes(conteudo.copyOfRange(0, 250_000))
        baixador().baixar(url(), destino, conteudo.size.toLong()) { _, _ -> }
        assertArrayEquals(conteudo, destino.readBytes())
        assertEquals("bytes=250000-", ranges[0])
    }

    @Test
    fun seOServidorIgnoraORangeRecomecaDoZero() {
        ignoraRange = true
        val destino = File(pasta, "a.apk.parcial")
        destino.writeBytes(conteudo.copyOfRange(0, 100_000))
        baixador().baixar(url(), destino, conteudo.size.toLong()) { _, _ -> }
        assertArrayEquals(conteudo, destino.readBytes())
    }

    @Test
    fun desisteDepoisDeCincoTentativas() {
        sempreErro = 500
        val destino = File(pasta, "a.apk.parcial")
        try {
            baixador().baixar(url(), destino, conteudo.size.toLong()) { _, _ -> }
            fail("devia falhar")
        } catch (e: Baixador.Falha) {
            assertEquals(5, pedidos.get())
        }
    }

    @Test
    fun arquivoDeTamanhoDiferenteDoAnunciadoNaoPassa() {
        // o que veio nao e o arquivo da versao: tamanho diferente do anunciado
        conteudoServido = ByteArray(conteudo.size + 10) { 7 }
        val destino = File(pasta, "a.apk.parcial")
        try {
            baixador(tentativas = 2).baixar(url(), destino, conteudo.size.toLong()) { _, _ -> }
            fail("devia falhar")
        } catch (e: Baixador.Falha) {
            assertEquals(2, pedidos.get())
        }
    }

    @Test
    fun parcialMaiorQueOTotalComecaDeNovo() {
        val destino = File(pasta, "a.apk.parcial")
        destino.writeBytes(ByteArray(conteudo.size + 500))
        baixador().baixar(url(), destino, conteudo.size.toLong()) { _, _ -> }
        assertArrayEquals(conteudo, destino.readBytes())
    }

    @Test
    fun jaTemTudoNaoBaixaDeNovo() {
        val destino = File(pasta, "a.apk.parcial")
        destino.writeBytes(conteudo)
        baixador().baixar(url(), destino, conteudo.size.toLong()) { _, _ -> }
        assertEquals(0, pedidos.get())
    }

    @Test
    fun oRangeNaoAceitoApagaOParcialERecomeca() {
        // o parcial e maior que o arquivo do servidor: 416, apaga e recomeca
        conteudoServido = conteudo.copyOfRange(0, 100_000)
        val destino = File(pasta, "a.apk.parcial")
        destino.writeBytes(ByteArray(150_000))
        try {
            baixador(tentativas = 3).baixar(url(), destino, 0L) { _, _ -> }
        } catch (e: Baixador.Falha) {
            fail("devia recomecar e conseguir: ${e.message}")
        }
        assertArrayEquals(conteudoServido, destino.readBytes())
    }

    @Test
    fun cancelarParaNaHora() {
        val destino = File(pasta, "a.apk.parcial")
        try {
            baixador().baixar(url(), destino, conteudo.size.toLong(), cancelado = { true }) { _, _ -> }
            fail("devia cancelar")
        } catch (e: Baixador.Cancelado) {
            assertFalse(destino.exists() && destino.length() == conteudo.size.toLong())
        }
    }

    @Test
    fun semTamanhoEsperadoUsaOQueOServidorDiz() {
        val destino = File(pasta, "a.apk.parcial")
        var total = 0L
        baixador().baixar(url(), destino, 0L) { _, t -> total = t }
        assertEquals(conteudo.size.toLong(), total)
        assertTrue(destino.length() == conteudo.size.toLong())
    }
}
