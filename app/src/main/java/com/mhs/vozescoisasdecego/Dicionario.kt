package com.mhs.vozescoisasdecego

import java.util.Locale

/**
 * Corrige a pronuncia trocando o texto antes de ele ir para o motor.
 *  - regras do app (letras soltas, "-gem/-gens" que o motor le como "gue", "RHVoice");
 *  - entradas do usuario, uma por linha, no formato  palavra=como falar  (ignora linhas vazias e as que comecam com #).
 */
object Dicionario {
    private val LETRAS = mapOf(
        'a' to "á", 'b' to "bê", 'c' to "cê", 'd' to "dê", 'e' to "é", 'f' to "éfe", 'g' to "gê", 'h' to "agá",
        'i' to "í", 'j' to "jota", 'k' to "cá", 'l' to "éle", 'm' to "ême", 'n' to "ene", 'o' to "ó", 'p' to "pê",
        'q' to "quê", 'r' to "érre", 's' to "ésse", 't' to "tê", 'u' to "ú", 'v' to "vê", 'w' to "dáblio",
        'x' to "xis", 'y' to "ípsilon", 'z' to "zê",
    )

    /** Palavras que o motor le errado e o jeito de escrever para sair certo. */
    private val PADRAO = mapOf(
        "rhvoice" to "érre agá vóice",
        // o motor le "cego" com o e fechado (cêgo); o certo e aberto (cégo)
        "cego" to "cégo",
        "cega" to "céga",
        "cegos" to "cégos",
        "cegas" to "cégas",
    )

    // -gem / -gens depois de vogal: o g e sempre "j" (o motor chegou a falar "imaguens").
    private val GEM = Regex("(?<=[aeiouáéíóúâêôãõ])g(ens|em)(?![\\p{L}])", RegexOption.IGNORE_CASE)

    fun entradas(texto: String): List<Pair<String, String>> =
        texto.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") && it.contains('=') }
            .map { it.substringBefore('=').trim() to it.substringAfter('=').trim() }
            .filter { it.first.isNotEmpty() && it.second.isNotEmpty() }
            .toList()

    private fun palavra(p: String) = Regex(
        "(?<![\\p{L}\\p{N}])" + Regex.escape(p) + "(?![\\p{L}\\p{N}])",
        RegexOption.IGNORE_CASE,
    )

    /** [usuario] = texto do campo do dicionario; [regras] liga as regras do app. */
    fun aplicar(texto: String, usuario: String = "", regras: Boolean = true): String {
        var t = texto
        // 1) entradas do usuario (as mais longas primeiro, para "mercado pago" ganhar de "mercado")
        for ((de, para) in entradas(usuario).sortedByDescending { it.first.length }) {
            t = palavra(de).replace(t) { para }
        }
        if (!regras) return t
        // 2) uma letra sozinha (o TalkBack soletra assim): fala o nome da letra
        val solta = t.trim()
        if (solta.length == 1) LETRAS[solta[0].lowercaseChar()]?.let { return it }
        // 3) palavras do app
        for ((de, para) in PADRAO) t = palavra(de).replace(t) { para }
        // 4) -gem / -gens
        t = GEM.replace(t) { m ->
            val g = m.value[0]
            val j = if (g.isUpperCase()) 'J' else 'j'
            val resto = m.value.substring(1)
            "" + j + resto
        }
        return t
    }

    /** Chave do cache: o texto ja corrigido, sem diferenca de maiusculas nem de espacos. */
    fun chave(idioma: String, texto: String): String =
        idioma + "|" + texto.trim().lowercase(Locale("pt", "BR")).replace(Regex("\\s+"), " ")
}
