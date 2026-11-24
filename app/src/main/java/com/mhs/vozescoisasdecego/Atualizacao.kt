package com.mhs.vozescoisasdecego

import org.json.JSONObject

/** Uma versao publicada no GitHub: o numero, o que mudou e o APK para baixar. */
data class Lancamento(
    val versao: String,
    val notas: String,
    val nomeApk: String,
    val urlApk: String,
    val tamanho: Long,
)

/**
 * A parte da atualizacao que nao depende do Android: de onde vem a versao nova, como ler a resposta do GitHub,
 * como comparar versoes e como dizer o tempo que falta. Igual ao que o SandFly faz com as versoes dele.
 */
object Atualizacao {
    const val REPOSITORIO = "MHS-Softwares/Vozes-Coisas-de-Cego"
    const val URL_ULTIMA = "https://api.github.com/repos/$REPOSITORIO/releases/latest"

    /** "v1.10.2-beta" vira [1, 10, 2]. */
    fun numeros(versao: String): List<Int> {
        val limpa = versao.trim().removePrefix("v").removePrefix("V")
        val base = limpa.takeWhile { it.isDigit() || it == '.' }
        return base.split('.').filter { it.isNotEmpty() }.map { it.toIntOrNull() ?: 0 }
    }

    /** Positivo se [a] e mais nova que [b]. Compara numero a numero, entao 1.10 e mais nova que 1.9. */
    fun comparar(a: String, b: String): Int {
        val x = numeros(a)
        val y = numeros(b)
        for (i in 0 until maxOf(x.size, y.size)) {
            val d = x.getOrElse(i) { 0 }.compareTo(y.getOrElse(i) { 0 })
            if (d != 0) return d
        }
        return 0
    }

    /** A versao publicada e mais nova que a instalada? Uma etiqueta sem numero nunca conta como nova. */
    fun haNova(instalada: String, publicada: String): Boolean =
        numeros(publicada).isNotEmpty() && comparar(publicada, instalada) > 0

    /**
     * Le a resposta de `releases/latest`. Devolve nulo se nao for uma versao de verdade (rascunho ou pre-versao),
     * se a etiqueta nao tiver numero ou se nao houver um APK entre os arquivos.
     */
    fun interpretar(json: String): Lancamento? {
        val o = JSONObject(json)
        if (o.optBoolean("draft") || o.optBoolean("prerelease")) return null
        val tag = o.optString("tag_name")
        if (numeros(tag).isEmpty()) return null
        val arquivos = o.optJSONArray("assets") ?: return null
        for (i in 0 until arquivos.length()) {
            val a = arquivos.optJSONObject(i) ?: continue
            val nome = a.optString("name")
            val url = a.optString("browser_download_url")
            if (nome.endsWith(".apk", ignoreCase = true) && url.startsWith("https://")) {
                return Lancamento(
                    versao = tag.trim().removePrefix("v").removePrefix("V"),
                    notas = o.optString("body").trim(),
                    nomeApk = nome,
                    urlApk = url,
                    tamanho = a.optLong("size", 0L),
                )
            }
        }
        return null
    }

    /** "1 minuto e 20 segundos", "45 segundos", "2 minutos". */
    fun descreverTempo(segundos: Long): String {
        val s = segundos.coerceAtLeast(1)
        val horas = s / 3600
        val minutos = (s % 3600) / 60
        val resto = s % 60
        val partes = ArrayList<String>()
        if (horas > 0) partes += if (horas == 1L) "1 hora" else "$horas horas"
        if (minutos > 0) partes += if (minutos == 1L) "1 minuto" else "$minutos minutos"
        if (resto > 0 && horas == 0L) partes += if (resto == 1L) "1 segundo" else "$resto segundos"
        return when (partes.size) {
            0 -> "1 segundo"
            1 -> partes[0]
            else -> partes.dropLast(1).joinToString(", ") + " e " + partes.last()
        }
    }

    /** Quanto falta, so depois de alguns segundos de download (antes disso a conta engana). */
    fun tempoQueFalta(baixado: Long, total: Long, decorridoMs: Long): String? {
        if (total <= 0 || baixado <= 0 || decorridoMs < 3000 || baixado >= total) return null
        val porSegundo = baixado * 1000.0 / decorridoMs
        if (porSegundo <= 0) return null
        return descreverTempo(Math.round((total - baixado) / porSegundo))
    }

    /** "40 por cento, 1 minuto e 20 segundos restantes". */
    fun andamento(baixado: Long, total: Long, decorridoMs: Long): String {
        val pct = if (total > 0) (baixado * 100 / total).toInt().coerceIn(0, 100) else 0
        val falta = tempoQueFalta(baixado, total, decorridoMs)
        return if (falta != null) "$pct por cento, $falta restantes" else "$pct por cento"
    }

    /** Tamanho para falar: "98 MB". */
    fun tamanhoLegivel(bytes: Long): String {
        if (bytes <= 0) return ""
        val mb = bytes / (1024.0 * 1024.0)
        return if (mb >= 1) "${Math.round(mb)} MB" else "${Math.round(bytes / 1024.0)} KB"
    }
}
