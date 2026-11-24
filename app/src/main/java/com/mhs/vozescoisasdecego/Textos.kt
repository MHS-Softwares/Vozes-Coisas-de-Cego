package com.mhs.vozescoisasdecego

/** Divide o texto em frases para falar uma de cada vez (comeca mais cedo e permite parar no meio). */
object Textos {
    private val fim = Regex("(?<=[.!?…;:\\n])\\s+")

    fun temFala(s: String): Boolean = s.any { it.isLetterOrDigit() }

    fun frases(texto: String, minimo: Int = 40, maximo: Int = 300): List<String> {
        val brutas = fim.split(texto.trim()).map { it.trim() }.filter { it.isNotEmpty() }
        val juntas = ArrayList<String>()
        var atual = StringBuilder()
        for (b in brutas) {
            if (atual.isNotEmpty()) atual.append(' ')
            atual.append(b)
            if (atual.length >= minimo) {
                juntas.add(atual.toString())
                atual = StringBuilder()
            }
        }
        if (atual.isNotEmpty()) juntas.add(atual.toString())

        val resultado = ArrayList<String>()
        for (f in juntas) {
            var resto = f
            while (resto.length > maximo) {
                var corte = resto.lastIndexOf(',', maximo)
                if (corte < maximo / 2) corte = resto.lastIndexOf(' ', maximo)
                if (corte < maximo / 2) corte = maximo
                resultado.add(resto.substring(0, corte + 1).trim())
                resto = resto.substring(corte + 1).trim()
            }
            if (resto.isNotEmpty()) resultado.add(resto)
        }
        return resultado.filter { temFala(it) }
    }
}
