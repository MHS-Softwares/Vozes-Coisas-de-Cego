package com.mhs.vozescoisasdecego

import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URL

/**
 * Baixa um arquivo grande e continua de onde parou quando a conexao cai, como o SandFly faz: ate cinco
 * tentativas, cada uma pedindo so o que falta (cabecalho Range). Nao depende do Android, entao da para testar no
 * computador com um servidor de mentira.
 */
class Baixador(
    private val abrir: (String) -> HttpURLConnection = { URL(it).openConnection() as HttpURLConnection },
    private val tentativas: Int = 5,
    private val esperar: (Int) -> Unit = { n -> Thread.sleep(1000L * n) },
) {
    class Falha(mensagem: String, causa: Throwable? = null) : Exception(mensagem, causa)
    class Cancelado : Exception("Download cancelado")

    /**
     * Baixa [url] para [parcial] e confere o tamanho no fim. Se [parcial] ja existe, continua dele.
     * [progresso] recebe o que ja foi baixado e o total (0 se nao se sabe).
     */
    fun baixar(
        url: String,
        parcial: File,
        tamanhoEsperado: Long,
        cancelado: () -> Boolean = { false },
        progresso: (Long, Long) -> Unit,
    ) {
        var falhas = 0
        var ultimoErro: Throwable? = null
        while (true) {
            if (cancelado()) throw Cancelado()
            val ja = if (parcial.exists()) parcial.length() else 0L
            if (tamanhoEsperado > 0 && ja > tamanhoEsperado) parcial.delete()
            if (tamanhoEsperado > 0 && parcial.exists() && parcial.length() == tamanhoEsperado) {
                progresso(tamanhoEsperado, tamanhoEsperado)
                return
            }
            try {
                uma(url, parcial, tamanhoEsperado, cancelado, progresso)
                val tamanho = parcial.length()
                if (tamanhoEsperado > 0 && tamanho != tamanhoEsperado) {
                    throw IOException("Veio $tamanho bytes, eram $tamanhoEsperado.")
                }
                progresso(tamanho, if (tamanhoEsperado > 0) tamanhoEsperado else tamanho)
                return
            } catch (e: Cancelado) {
                throw e
            } catch (e: IOException) {
                ultimoErro = e
                falhas++
                if (falhas >= tentativas) throw Falha("Nao consegui baixar o arquivo.", e)
                esperar(falhas)
            }
        }
    }

    private fun uma(
        url: String,
        parcial: File,
        tamanhoEsperado: Long,
        cancelado: () -> Boolean,
        progresso: (Long, Long) -> Unit,
    ) {
        var ja = if (parcial.exists()) parcial.length() else 0L
        val c = abrir(url)
        try {
            c.connectTimeout = 15_000
            c.readTimeout = 20_000
            c.setRequestProperty("User-Agent", "VozesCoisasDeCego")
            if (ja > 0) c.setRequestProperty("Range", "bytes=$ja-")
            when (val codigo = c.responseCode) {
                206 -> Unit
                200 -> if (ja > 0) {
                    // O servidor ignorou o pedido de continuar: recomeca do zero.
                    parcial.delete()
                    ja = 0
                }
                416 -> {
                    parcial.delete()
                    throw IOException("O servidor nao aceitou continuar de $ja.")
                }
                else -> throw IOException("O servidor respondeu $codigo.")
            }
            val doServidor = c.contentLengthLong
            val total = when {
                tamanhoEsperado > 0 -> tamanhoEsperado
                doServidor > 0 -> ja + doServidor
                else -> 0L
            }
            parcial.parentFile?.mkdirs()
            RandomAccessFile(parcial, "rw").use { arquivo ->
                arquivo.seek(ja)
                c.inputStream.use { entrada ->
                    val bloco = ByteArray(1 shl 16)
                    var baixado = ja
                    var ultimoAviso = 0L
                    while (true) {
                        if (cancelado()) throw Cancelado()
                        val n = entrada.read(bloco)
                        if (n < 0) break
                        arquivo.write(bloco, 0, n)
                        baixado += n
                        val agora = System.nanoTime()
                        if (agora - ultimoAviso > 200_000_000L) {
                            ultimoAviso = agora
                            progresso(baixado, total)
                        }
                    }
                }
            }
        } finally {
            c.disconnect()
        }
    }
}
