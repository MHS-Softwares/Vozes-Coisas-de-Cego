package com.mhs.vozescoisasdecego

import android.content.Context
import android.content.res.AssetManager
import java.io.File
import java.io.IOException

/** O motor abre o modelo por caminho de arquivo, entao copiamos do APK para a pasta do app uma vez. */
object ModelStore {
    /** Muda quando o modelo ou a voz mudam: refaz a copia do modelo e apaga o cache de frases. */
    const val PACOTE = "vozes-q8-v3"

    fun pasta(ctx: Context) = File(ctx.filesDir, "model")

    fun pronto(ctx: Context): Boolean {
        val marca = File(pasta(ctx), ".pronto")
        return marca.exists() && runCatching { marca.readText() }.getOrNull() == PACOTE
    }

    @Synchronized
    fun garantir(ctx: Context): File {
        val destino = pasta(ctx)
        if (pronto(ctx)) return destino
        val tmp = File(ctx.filesDir, "model.novo")
        tmp.deleteRecursively()
        copiar(ctx.assets, "model", tmp)
        File(tmp, ".pronto").writeText(PACOTE)
        destino.deleteRecursively()
        if (!tmp.renameTo(destino)) throw IOException("Nao consegui preparar o modelo de voz.")
        return destino
    }

    private fun copiar(am: AssetManager, caminho: String, destino: File) {
        val filhos = am.list(caminho) ?: emptyArray()
        if (filhos.isEmpty()) {
            destino.parentFile?.mkdirs()
            am.open(caminho).use { i -> destino.outputStream().use { o -> i.copyTo(o, 1 shl 16) } }
        } else {
            destino.mkdirs()
            for (f in filhos) copiar(am, "$caminho/$f", File(destino, f))
        }
    }
}
