package com.mhs.vozescoisasdecego

import java.io.File
import java.util.Locale

/** As vozes que vem dentro do app (cada uma e um arquivo voices/Nome.safetensors do modelo). */
object Vozes {
    /** Na ordem em que aparecem na tela. O nome e o do arquivo, sem o .safetensors. */
    val LISTA = listOf("Meriellen", "Michel", "Daiane")
    const val PADRAO = "Meriellen"

    /** Devolve o nome certo da voz (sem diferenca de maiusculas) ou a voz padrao se nao existir. */
    fun valida(nome: String?): String {
        val n = nome?.trim()
        if (n.equals(NOME_ANTIGO, ignoreCase = true)) return "Meriellen"
        return LISTA.firstOrNull { it.equals(n, ignoreCase = true) } ?: PADRAO
    }

    /** Nome que o app usava antes de acertarem a grafia (Merielen): vira Meriellen. */
    private const val NOME_ANTIGO = "Merielen"

    /** Apaga as pastas de cache que nao pertencem a nenhuma voz atual (por exemplo, a do nome antigo). */
    fun limparCachesAntigos(cacheDir: File) {
        val validas = LISTA.map { pastaDoCache(cacheDir, it).name }.toSet()
        cacheDir.listFiles { f -> f.isDirectory && f.name.startsWith("fala-") && f.name !in validas }
            ?.forEach { it.deleteRecursively() }
    }

    /** Cada voz tem a sua pasta de cache: o audio de uma nunca toca com a voz da outra. */
    fun pastaDoCache(cacheDir: File, voz: String): File = File(cacheDir, "fala-" + valida(voz).lowercase(Locale.ROOT))

    /** Vozes de mulher: a apresentacao em portugues diz "Eu sou a ...". */
    private val FEMININAS = setOf("Meriellen", "Daiane")

    private fun artigo(voz: String) = if (voz in FEMININAS) "a" else "o"

    /** Frase com que a voz se apresenta (teste, amostra do Android e troca de voz). */
    fun apresentacao(idioma3: String, voz: String): String {
        val v = valida(voz)
        val projeto = if (v == "Meriellen") ", do projeto Coisas de Cego" else ""
        return when (idioma3) {
            "eng" -> "Hello! I am $v$projeto. This is my voice on your phone."
            "spa" -> "¡Hola! Soy $v$projeto. Esta es mi voz en tu teléfono."
            "fra" -> "Bonjour ! Je suis $v$projeto. Voici ma voix sur votre téléphone."
            "deu" -> "Hallo! Ich bin $v$projeto. Das ist meine Stimme auf deinem Telefon."
            else -> "Olá! Eu sou ${artigo(v)} $v$projeto. Esta é a minha voz no seu celular."
        }
    }
}

/**
 * Guarda qual voz o motor esta usando agora e so manda trocar quando o pedido e de outra voz.
 * Se a troca falhar, continua marcada a voz de antes (e a proxima fala tenta de novo).
 */
class VozAtiva {
    @Volatile
    var atual: String? = null
        private set

    /** Marca a voz que ja foi aplicada (por exemplo, ao carregar o motor). */
    fun definir(voz: String) {
        atual = voz
    }

    /** O motor foi liberado: na proxima vez a voz precisa ser aplicada de novo. */
    fun definirDesconhecida() {
        atual = null
    }

    /** Chama [aplicar] so se [desejada] for diferente da atual. Devolve true se ao final a voz certa esta em uso. */
    fun trocarSePreciso(desejada: String, aplicar: (String) -> Boolean): Boolean {
        if (atual == desejada) return true
        val ok = aplicar(desejada)
        if (ok) atual = desejada
        return ok
    }
}
