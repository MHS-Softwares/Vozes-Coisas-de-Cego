package com.mhs.vozescoisasdecego

import java.io.File
import java.security.MessageDigest
import java.util.concurrent.Executors

/** Junta o audio que o motor entrega aos pedacos (float) num unico vetor de 16 bits. */
class ShortBuilder {
    private var buf = ShortArray(24000)
    private var n = 0

    fun add(pcm: FloatArray, count: Int) {
        if (n + count > buf.size) buf = buf.copyOf(maxOf(n + count, buf.size * 2))
        for (i in 0 until count) {
            buf[n + i] = (pcm[i] * 32767f).toInt().coerceIn(-32768, 32767).toShort()
        }
        n += count
    }

    fun toArray(): ShortArray = buf.copyOf(n)
}

/**
 * Guarda o audio bruto (PCM 16 bits, 24 kHz, mono) de cada frase ja gerada, na memoria (as mais usadas)
 * e no disco, para a mesma frase tocar na hora da proxima vez, sem passar pelo motor.
 * A [versao] entra no nome da pasta: se o modelo ou a voz mudarem, o cache antigo e apagado.
 */
class CacheFala(
    base: File,
    versao: String,
    private val limiteMemoria: Long = 24L * 1024 * 1024,
    private val limiteDisco: Long = 120L * 1024 * 1024,
) {
    private val dir = File(base, versao)
    private val memoria = object : LinkedHashMap<String, ShortArray>(64, 0.75f, true) {}
    private var bytesMemoria = 0L
    private val disco = Executors.newSingleThreadExecutor { r -> Thread(r, "vozes-tts-cache").apply { isDaemon = true } }

    init {
        dir.mkdirs()
        // apaga caches de outras versoes
        base.listFiles()?.filter { it.isDirectory && it.name != versao }?.forEach { it.deleteRecursively() }
    }

    private fun arquivo(chave: String): File {
        val h = MessageDigest.getInstance("SHA-1").digest(chave.toByteArray(Charsets.UTF_8))
        return File(dir, h.joinToString("") { "%02x".format(it) } + ".pcm")
    }

    fun contem(chave: String): Boolean {
        synchronized(memoria) { if (memoria.containsKey(chave)) return true }
        return arquivo(chave).isFile
    }

    fun get(chave: String): ShortArray? {
        synchronized(memoria) { memoria[chave]?.let { return it } }
        val f = arquivo(chave)
        if (!f.isFile) return null
        val dados = try {
            val b = f.readBytes()
            if (b.size < 2 || b.size % 2 != 0) return null
            ShortArray(b.size / 2) { ((b[2 * it].toInt() and 0xFF) or (b[2 * it + 1].toInt() shl 8)).toShort() }
        } catch (_: Exception) {
            return null
        }
        guardarMemoria(chave, dados)
        runCatching { f.setLastModified(System.currentTimeMillis()) }
        return dados
    }

    fun put(chave: String, pcm: ShortArray) {
        if (pcm.isEmpty()) return
        guardarMemoria(chave, pcm)
        val destino = arquivo(chave)
        if (destino.isFile) return
        disco.execute {
            try {
                val b = ByteArray(pcm.size * 2)
                for (i in pcm.indices) {
                    b[2 * i] = (pcm[i].toInt() and 0xFF).toByte()
                    b[2 * i + 1] = (pcm[i].toInt() shr 8).toByte()
                }
                val tmp = File(dir, destino.name + ".tmp")
                tmp.writeBytes(b)
                if (!tmp.renameTo(destino)) tmp.delete()
                aparar()
            } catch (_: Exception) {
            }
        }
    }

    private fun guardarMemoria(chave: String, pcm: ShortArray) {
        synchronized(memoria) {
            memoria.put(chave, pcm)?.let { bytesMemoria -= it.size * 2L }
            bytesMemoria += pcm.size * 2L
            val it = memoria.entries.iterator()
            while (bytesMemoria > limiteMemoria && it.hasNext()) {
                val e = it.next()
                if (e.key == chave) continue
                bytesMemoria -= e.value.size * 2L
                it.remove()
            }
        }
    }

    /** Se o disco passar do limite, apaga os arquivos menos usados. */
    private fun aparar() {
        val arqs = dir.listFiles { f -> f.name.endsWith(".pcm") } ?: return
        var total = arqs.sumOf { it.length() }
        if (total <= limiteDisco) return
        for (f in arqs.sortedBy { it.lastModified() }) {
            if (total <= limiteDisco * 0.9) break
            total -= f.length()
            f.delete()
        }
    }

    /** Para os testes: espera as gravacoes em disco terminarem. */
    fun esperarDisco() {
        disco.submit {}.get()
    }

    fun quantosNoDisco(): Int = dir.listFiles { f -> f.name.endsWith(".pcm") }?.size ?: 0
}
