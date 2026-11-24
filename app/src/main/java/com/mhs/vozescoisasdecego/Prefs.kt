package com.mhs.vozescoisasdecego

import android.content.Context

/** Ajustes do usuario. O servico de voz le a cada fala, entao vale na hora. */
class Prefs(context: Context) {
    private val sp = context.applicationContext.getSharedPreferences("vozes_coisas_de_cego", Context.MODE_PRIVATE)

    /** Voz em uso (veja [Vozes.LISTA]). O servico de voz le a cada fala, entao a troca vale na hora. */
    var voz: String
        get() = Vozes.valida(sp.getString("voz", Vozes.PADRAO))
        set(v) = sp.edit().putString("voz", Vozes.valida(v)).apply()

    /** 1,0 = ritmo natural da voz (sem processar). Chave nova: descarta valores de versoes de teste anteriores. */
    var velocidade: Float
        get() = sp.getFloat("velocidade_v3", 1.0f).coerceIn(0.25f, 3.0f)
        set(v) = sp.edit().putFloat("velocidade_v3", v).apply()

    /** Se ligado, a velocidade pedida pelo Android/TalkBack multiplica a do app; desligado, so vale a do app. */
    var seguirVelocidadeAndroid: Boolean
        get() = sp.getBoolean("seguir_velocidade", true)
        set(v) = sp.edit().putBoolean("seguir_velocidade", v).apply()

    var seguirTomAndroid: Boolean
        get() = sp.getBoolean("seguir_tom", true)
        set(v) = sp.edit().putBoolean("seguir_tom", v).apply()

    /** Corta o silencio que o motor deixa no comeco de cada frase (responde mais rapido). */
    var cortarInicio: Boolean
        get() = sp.getBoolean("cortar_inicio", true)
        set(v) = sp.edit().putBoolean("cortar_inicio", v).apply()

    /** Liga as regras de pronuncia do app (letras soltas, -gem/-gens, RHVoice). */
    var corrigirPronuncia: Boolean
        get() = sp.getBoolean("corrigir_pronuncia", true)
        set(v) = sp.edit().putBoolean("corrigir_pronuncia", v).apply()

    /** Entradas do usuario, uma por linha: palavra=como falar. */
    var dicionario: String
        get() = sp.getString("dicionario", "") ?: ""
        set(v) = sp.edit().putString("dicionario", v).apply()

    /** Le os emojis pelo nome em portugues (sem isso o motor nao sabe falar emojis). */
    var lerEmojis: Boolean
        get() = sp.getBoolean("ler_emojis", true)
        set(v) = sp.edit().putBoolean("ler_emojis", v).apply()

    /** Emojis repetidos viram "nome, 3 vezes". */
    var emojisRepetidos: Boolean
        get() = sp.getBoolean("emojis_repetidos", true)
        set(v) = sp.edit().putBoolean("emojis_repetidos", v).apply()

    /** Gera, com o celular parado, as frases comuns de leitor de telas e deixa no cache. */
    var preGerar: Boolean
        get() = sp.getBoolean("pre_gerar", true)
        set(v) = sp.edit().putBoolean("pre_gerar", v).apply()

    /** Ultimo pedido recebido do Android, so para mostrar na tela e ajudar a entender o ritmo. */
    var ultimoPedido: String
        get() = sp.getString("ultimo_pedido", "") ?: ""
        set(v) = sp.edit().putString("ultimo_pedido", v).apply()

    var tom: Float
        get() = sp.getFloat("tom", 1.0f).coerceIn(0.5f, 2.0f)
        set(v) = sp.edit().putFloat("tom", v).apply()

    var volume: Float
        get() = sp.getFloat("volume", 1.0f).coerceIn(0.0f, 2.0f)
        set(v) = sp.edit().putFloat("volume", v).apply()

    /** 0 = inicio rapido, 1 = automatico, 2 = sem engasgos (espera a frase pronta). */
    var modo: Int
        get() = sp.getInt("modo", 1).coerceIn(0, 2)
        set(v) = sp.edit().putInt("modo", v).apply()

    /** 0 = automatico. So vale na proxima vez que o servico de voz iniciar. */
    var nucleos: Int
        get() = sp.getInt("nucleos", 0).coerceIn(0, 8)
        set(v) = sp.edit().putInt("nucleos", v).apply()

    /** Reserva de audio no comeco da fala gerada (modo Automatico): indice em [Reserva.OPCOES_MS]. */
    var reserva: Int
        get() = sp.getInt("reserva", Reserva.PADRAO).coerceIn(0, Reserva.OPCOES_MS.size - 1)
        set(v) = sp.edit().putInt("reserva", v).apply()

    /** Procura versao nova no GitHub uma vez por dia (e avisa por notificacao). So esta checagem usa a internet. */
    var verificarAtualizacoes: Boolean
        get() = sp.getBoolean("verificar_atualizacoes", true)
        set(v) = sp.edit().putBoolean("verificar_atualizacoes", v).apply()

    /** Quando foi a ultima vez que o app perguntou ao GitHub (milissegundos). */
    var ultimaChecagem: Long
        get() = sp.getLong("ultima_checagem", 0L)
        set(v) = sp.edit().putLong("ultima_checagem", v).apply()

    /** A ultima versao que ja foi anunciada por notificacao, para nao repetir o aviso. */
    var versaoAnunciada: String
        get() = sp.getString("versao_anunciada", "") ?: ""
        set(v) = sp.edit().putString("versao_anunciada", v).apply()

    /** O app ja pediu a permissao de notificacoes (so se pede uma vez). */
    var pediuPermissaoAvisos: Boolean
        get() = sp.getBoolean("pediu_permissao_avisos", false)
        set(v) = sp.edit().putBoolean("pediu_permissao_avisos", v).apply()

    /** Restaura os ajustes de voz. A escolha de procurar atualizacoes e o historico dela ficam como estavam. */
    fun restaurar() {
        val verificar = verificarAtualizacoes
        val checagem = ultimaChecagem
        val anunciada = versaoAnunciada
        val pediu = pediuPermissaoAvisos
        sp.edit().clear().apply()
        sp.edit().putBoolean("verificar_atualizacoes", verificar).putLong("ultima_checagem", checagem)
            .putString("versao_anunciada", anunciada).putBoolean("pediu_permissao_avisos", pediu).apply()
    }
}
