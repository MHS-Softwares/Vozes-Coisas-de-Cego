package com.mhs.vozescoisasdecego

/** Uma palavra do dicionario do usuario: [palavra] e a palavra certa, [pronuncia] e como a voz deve falar. */
data class EntradaDicionario(val linha: Int, val palavra: String, val pronuncia: String)

/**
 * Lista, adiciona, edita e remove palavras do dicionario do usuario.
 * O dicionario continua guardado como texto, uma linha por palavra, no formato  palavra=como falar
 * (o mesmo que [Dicionario.aplicar] le). Linhas que nao sao palavras (comentarios com #, linhas vazias,
 * escritas a mao) sao preservadas.
 */
object DicionarioUsuario {
    private fun limpar(s: String) = s.replace(Regex("[\\r\\n]+"), " ").trim()

    /** As palavras do dicionario, na ordem das linhas. [EntradaDicionario.linha] e o indice da linha no texto. */
    fun listar(texto: String): List<EntradaDicionario> {
        val saida = mutableListOf<EntradaDicionario>()
        texto.lines().forEachIndexed { i, bruta ->
            val l = bruta.trim()
            if (l.isEmpty() || l.startsWith("#") || !l.contains('=')) return@forEachIndexed
            val p = l.substringBefore('=').trim()
            val q = l.substringAfter('=').trim()
            if (p.isNotEmpty() && q.isNotEmpty()) saida += EntradaDicionario(i, p, q)
        }
        return saida
    }

    /** Mensagem para o usuario se os dois campos nao servem, ou null se estao certos. */
    fun validar(palavra: String, pronuncia: String): String? {
        val p = limpar(palavra)
        val q = limpar(pronuncia)
        if (p.isEmpty()) return "Escreva a palavra correta."
        if (q.isEmpty()) return "Escreva como a voz deve falar."
        if (p.contains('=')) return "A palavra não pode ter o sinal de igual."
        if (p.startsWith("#")) return "A palavra não pode começar com #."
        return null
    }

    private fun mesma(a: String, b: String) = a.equals(b, ignoreCase = true)

    private fun linhaDe(palavra: String, pronuncia: String) = limpar(palavra) + "=" + limpar(pronuncia)

    /** Acrescenta a palavra. Se ela ja estiver no dicionario (sem diferenca de maiusculas), troca a pronuncia dela. */
    fun adicionar(texto: String, palavra: String, pronuncia: String): String {
        require(validar(palavra, pronuncia) == null) { "palavra invalida" }
        val existente = listar(texto).firstOrNull { mesma(it.palavra, limpar(palavra)) }
        if (existente != null) return editar(texto, existente.linha, palavra, pronuncia)
        val linhas = texto.lines().toMutableList()
        while (linhas.isNotEmpty() && linhas.last().isBlank()) linhas.removeAt(linhas.size - 1)
        linhas += linhaDe(palavra, pronuncia)
        return linhas.joinToString("\n")
    }

    /**
     * Troca a palavra e a pronuncia da linha [linha]. Se outra linha ja tinha a mesma palavra,
     * ela sai (a que foi editada vale).
     */
    fun editar(texto: String, linha: Int, palavra: String, pronuncia: String): String {
        require(validar(palavra, pronuncia) == null) { "palavra invalida" }
        val linhas = texto.lines().toMutableList()
        require(linha in linhas.indices) { "linha fora do texto" }
        linhas[linha] = linhaDe(palavra, pronuncia)
        val repetidas = listar(linhas.joinToString("\n"))
            .filter { it.linha != linha && mesma(it.palavra, limpar(palavra)) }
            .map { it.linha }
            .sortedDescending()
        for (i in repetidas) linhas.removeAt(i)
        return linhas.joinToString("\n")
    }

    /** Tira a palavra que esta na linha [linha]. */
    fun remover(texto: String, linha: Int): String {
        val linhas = texto.lines().toMutableList()
        require(linha in linhas.indices) { "linha fora do texto" }
        linhas.removeAt(linha)
        return linhas.joinToString("\n")
    }
}
