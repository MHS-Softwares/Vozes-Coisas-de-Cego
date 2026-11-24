package com.mhs.vozescoisasdecego

import android.content.Context

/**
 * Troca cada emoji pelo nome dele em portugues (tabela do CLDR, em assets/emojis_pt.tsv: "emoji<TAB>nome").
 * Emojis repetidos viram "nome, 3 vezes". Emoji sem nome na tabela e descartado (o motor nao sabe le-lo).
 */
class TabelaEmojis(private val mapa: Map<String, String>) {
    private val maxLen = (mapa.keys.maxOfOrNull { it.codePointCount(0, it.length) } ?: 1) + 4

    val tamanho get() = mapa.size

    companion object {
        private const val VS16 = 0xFE0F
        private const val VS15 = 0xFE0E
        private const val ZWJ = 0x200D
        private const val KEYCAP = 0x20E3

        val VAZIA = TabelaEmojis(emptyMap())

        fun normalizar(s: String): String = s.replace("️", "").replace("︎", "")

        fun deLinhas(linhas: Sequence<String>): TabelaEmojis {
            val m = HashMap<String, String>()
            for (l in linhas) {
                val p = l.indexOf('\t')
                if (p <= 0) continue
                val nome = l.substring(p + 1).trim()
                if (nome.isNotEmpty()) m[normalizar(l.substring(0, p))] = nome
            }
            return TabelaEmojis(m)
        }

        @Volatile
        private var carregada: TabelaEmojis? = null

        /** Le a tabela dos assets uma vez. Sem o arquivo, devolve tabela vazia (e o app so descarta emojis). */
        fun dosAssets(ctx: Context): TabelaEmojis =
            carregada ?: synchronized(this) {
                carregada ?: try {
                    ctx.assets.open("emojis_pt.tsv").bufferedReader(Charsets.UTF_8).useLines { deLinhas(it) }
                } catch (_: Exception) {
                    VAZIA
                }.also { carregada = it }
            }

        private fun pareceEmoji(cp: Int): Boolean =
            cp >= 0x1F000 || cp in 0x2600..0x27BF || cp in 0x2B00..0x2BFF || cp in 0x2190..0x21FF ||
                cp in 0x2300..0x23FF || cp == 0x203C || cp == 0x2049 || cp == 0x3030 || cp == 0x303D ||
                cp == 0x3297 || cp == 0x3299

        /** Emoji que sobrou sem nome: descarta. Setas e formas comuns de texto ficam como estao. */
        private fun descartavel(cp: Int) = cp >= 0x1F000 || cp in 0x2600..0x27BF || cp == VS16 || cp == VS15 || cp == ZWJ || cp == KEYCAP

        private val VEZES = mapOf(2 to "duas", 3 to "três", 4 to "quatro", 5 to "cinco", 6 to "seis", 7 to "sete", 8 to "oito", 9 to "nove", 10 to "dez")
    }

    /** True se o texto tem algo que precisa de troca (para nao mexer em texto comum). */
    fun temEmoji(texto: String): Boolean {
        var i = 0
        while (i < texto.length) {
            val cp = texto.codePointAt(i)
            if (pareceEmoji(cp) || cp == KEYCAP) return true
            i += Character.charCount(cp)
        }
        return false
    }

    fun substituir(texto: String, repetidos: Boolean = true): String {
        if (!temEmoji(texto)) return texto
        val cps = texto.codePoints().toArray()
        val n = cps.size
        val sb = StringBuilder()
        var i = 0
        while (i < n) {
            val cp = cps[i]
            val candidato = pareceEmoji(cp) || cp in 0x30..0x39 || cp == 0x23 || cp == 0x2A
            val achado = if (candidato) casar(cps, i) else null
            if (achado == null) {
                if (!descartavel(cp)) sb.appendCodePoint(cp)
                i++
                continue
            }
            val (nome, comprimento) = achado
            var vezes = 1
            var j = i + comprimento
            if (repetidos) {
                while (j < n) {
                    val outro = casar(cps, j)
                    if (outro == null || outro.first != nome || outro.second != comprimento) break
                    vezes++
                    j += outro.second
                }
            }
            sb.append(' ').append(nome)
            if (vezes > 1) sb.append(", ").append(VEZES[vezes] ?: "muitas").append(" vezes")
            sb.append(' ')
            i = j
        }
        return sb.toString().replace(Regex(" {2,}"), " ").trim()
    }

    /** Maior sequencia que comeca em [i] e existe na tabela: (nome, quantidade de code points). */
    private fun casar(cps: IntArray, i: Int): Pair<String, Int>? {
        val max = minOf(maxLen, cps.size - i)
        for (l in max downTo 1) {
            val chave = normalizar(String(cps, i, l))
            val nome = mapa[chave]
            if (nome != null) return nome to l
        }
        return null
    }
}
