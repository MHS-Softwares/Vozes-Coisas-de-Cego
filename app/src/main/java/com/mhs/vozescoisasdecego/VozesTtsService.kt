package com.mhs.vozescoisasdecego

import android.media.AudioFormat
import android.os.SystemClock
import android.speech.tts.SynthesisCallback
import android.speech.tts.SynthesisRequest
import android.speech.tts.TextToSpeech
import android.speech.tts.TextToSpeechService
import android.speech.tts.Voice
import android.util.Log
import java.io.ByteArrayOutputStream
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

/** Motor de texto para fala do Android com as vozes do projeto Coisas de Cego (Meriellen, Michel e Daiane). */
class VozesTtsService : TextToSpeechService() {
    private companion object {
        const val TAG = "VozesTTS"

        // idioma do Android (3 letras) -> codigo do motor
        val IDIOMAS = mapOf("por" to "pt", "eng" to "en", "spa" to "es", "fra" to "fr", "deu" to "de")

        val VOZES = listOf(
            Locale("pt", "BR"), Locale("pt", "PT"),
            Locale("en", "US"), Locale("es", "ES"), Locale("fr", "FR"), Locale("de", "DE"),
        )

        fun nomeDaVoz(l: Locale) = "coisas-de-cego-" + l.language + "-" + l.country
    }

    private lateinit var motor: PhononEngine
    private var idiomaAtual = arrayOf("por", "BRA", "")

    override fun onCreate() {
        super.onCreate()
        motor = PhononEngine.get(this)
        motor.preparar() // carrega o modelo enquanto o Android ainda nao pediu nada
        motor.iniciarPreGeracao() // com o celular parado, vai deixando as frases comuns prontas no cache
        Atualizador.checagemAutomatica(this) // no maximo uma vez por dia; so avisa se houver versao nova
    }

    override fun onDestroy() {
        motor.cancelado = true
        runCatching { motor.noMotor { motor.liberar() } }
        super.onDestroy()
    }

    private fun codigo(lang: String?): String? = IDIOMAS[(lang ?: "").lowercase(Locale.ROOT)]

    override fun onIsLanguageAvailable(lang: String?, country: String?, variant: String?): Int {
        if (codigo(lang) == null) return TextToSpeech.LANG_NOT_SUPPORTED
        return if (country.isNullOrEmpty()) TextToSpeech.LANG_AVAILABLE else TextToSpeech.LANG_COUNTRY_AVAILABLE
    }

    override fun onLoadLanguage(lang: String?, country: String?, variant: String?): Int {
        val r = onIsLanguageAvailable(lang, country, variant)
        if (r != TextToSpeech.LANG_NOT_SUPPORTED) idiomaAtual = arrayOf(lang ?: "", country ?: "", variant ?: "")
        return r
    }

    override fun onGetLanguage(): Array<String> = idiomaAtual

    override fun onGetVoices(): MutableList<Voice> =
        VOZES.map { Voice(nomeDaVoz(it), it, Voice.QUALITY_HIGH, Voice.LATENCY_NORMAL, false, emptySet()) }.toMutableList()

    override fun onIsValidVoiceName(name: String?): Int =
        if (VOZES.any { nomeDaVoz(it) == name }) TextToSpeech.SUCCESS else TextToSpeech.ERROR

    override fun onLoadVoice(name: String?): Int = onIsValidVoiceName(name)

    override fun onGetDefaultVoiceNameFor(lang: String?, country: String?, variant: String?): String {
        val iso2 = codigo(lang) ?: "pt"
        val v = VOZES.firstOrNull { it.language == iso2 && it.country.equals(country?.take(2), true) }
            ?: VOZES.firstOrNull { it.language == iso2 } ?: VOZES.first()
        return nomeDaVoz(v)
    }

    override fun onStop() {
        motor.cancelado = true
    }

    override fun onSynthesizeText(request: SynthesisRequest, callback: SynthesisCallback) {
        // Avisa a pre-geracao (se estiver rodando) para sair da frente.
        motor.pedidosAtivos.incrementAndGet()
        motor.abortarPreGeracao = true
        try {
            sintetizarPedido(request, callback)
        } finally {
            motor.ultimaFalaMs = SystemClock.elapsedRealtime()
            motor.pedidosAtivos.decrementAndGet()
        }
    }

    private fun sintetizarPedido(request: SynthesisRequest, callback: SynthesisCallback) {
        motor.cancelado = false
        val bruto = (request.charSequenceText ?: "").toString()
        val idioma = codigo(request.language) ?: "pt"
        val prefs = Prefs(this)
        val velocidade = Velocidade.bruta(prefs.velocidade, request.speechRate, prefs.seguirVelocidadeAndroid)
        val tom = Velocidade.tom(prefs.tom, request.pitch, prefs.seguirTomAndroid)
        val volume = prefs.volume
        val voz = prefs.voz // lida a cada fala: trocar a voz na tela vale a partir da proxima frase
        val pedido = "Voz $voz. O Android pediu velocidade ${request.speechRate} e tom ${request.pitch} " +
            "(100 é normal). Falei com velocidade ${"%.2f".format(Locale.US, velocidade)}x e tom ${"%.2f".format(Locale.US, tom)}x."
        prefs.ultimoPedido = pedido

        val t0 = SystemClock.elapsedRealtime()
        val medidas = Medidas()
        // Dicionario de pronuncia, depois divisao em frases. Se TODAS ja estao no cache, nem passa pelo motor.
        val semEmoji = if (prefs.lerEmojis) TabelaEmojis.dosAssets(this).substituir(bruto, prefs.emojisRepetidos) else bruto
        val texto = Dicionario.aplicar(semEmoji, prefs.dicionario, prefs.corrigirPronuncia)
        val frases = Textos.frases(texto)
        medidas.totalFrases = frases.size
        val cache = motor.cacheDa(voz)
        val tudoNoCache = frases.isNotEmpty() && frases.all { cabeNoCache(it) && cache.contem(Dicionario.chave(idioma, it)) }
        callback.start(PhononEngine.TAXA, AudioFormat.ENCODING_PCM_16BIT, 1)
        val ok = try {
            if (tudoNoCache) {
                sintetizar(frases, idioma, voz, velocidade, tom, volume, prefs.modo, callback, t0, medidas, false)
            } else {
                motor.noMotor { sintetizar(frases, idioma, voz, velocidade, tom, volume, prefs.modo, callback, t0, medidas, true) }
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Falha ao sintetizar", t)
            false
        }
        prefs.ultimoPedido = pedido + " Frases tiradas do cache: ${medidas.doCache} de ${medidas.totalFrases}. " +
            "Primeiro som em ${medidas.primeiroSomMs} ms " +
            "(o motor levou ${medidas.motorMs.toInt()} ms para a primeira amostra; " +
            "calcula a fala em ${"%.2f".format(Locale.US, motor.rtf)} vez o tempo real; " +
            "espera de segurança ${medidas.esperaMs} ms; ${motor.nucleosUsados} núcleos; ${motor.biblioteca}). " +
            "Folga mínima de áudio: " +
            (if (medidas.menorFolgaMs == Long.MAX_VALUE) "sem medida (fala curta)" else "${medidas.menorFolgaMs} ms") +
            "; vezes em que o áudio acabou antes do próximo trecho: ${medidas.lacunas}."
        if (motor.cancelado) return
        if (ok) callback.done() else callback.error()
    }

    /** Tempos medidos na fala, so para mostrar na tela de ajustes. */
    private class Medidas {
        @Volatile var primeiroSomMs = -1L
        @Volatile var motorMs = 0.0
        @Volatile var esperaMs = 0L
        @Volatile var menorFolgaMs = Long.MAX_VALUE   // menor "sobra" de audio entregue e ainda nao tocado
        @Volatile var lacunas = 0                      // vezes em que o audio acabou antes do proximo trecho
        @Volatile var doCache = 0                      // frases que tocaram direto do cache
        @Volatile var totalFrases = 0
    }

    /** So frases curtas vao para o cache (as longas quase nunca se repetem). */
    private fun cabeNoCache(frase: String) = frase.length <= 160

    /**
     * Fala as frases. Se [naThreadDoMotor] for verdadeiro, roda na thread do motor; senao so pode usar o cache
     * (e qualquer frase que faltar e pedida ao motor por [PhononEngine.noMotor]).
     */
    private fun sintetizar(
        frases: List<String>, idioma: String, voz: String, velocidade: Float, tom: Float, volume: Float,
        modo: Int, callback: SynthesisCallback, t0: Long, medidas: Medidas, naThreadDoMotor: Boolean,
    ): Boolean {
        if (frases.isEmpty()) return true
        val cache = motor.cacheDa(voz)
        val saida = SaidaDeAudio(callback, modo, velocidade, t0, medidas, Reserva.ms(Prefs(this).reserva))
        val cortarInicio = Prefs(this).cortarInicio
        var primeira = true
        val proc = AudioProcessor(velocidade, tom, volume) { s, n -> saida.escrever(s, n) }
        for (frase in frases) {
            if (motor.cancelado || saida.falhou) return false
            // Folga antes da voz: 15 ms na primeira frase; nas seguintes ~70 ms, para manter uma pausa natural.
            val corte = if (cortarInicio) CortaInicio(if (primeira) 360 else 1680) else null
            primeira = false
            val passa: (FloatArray, Int) -> Unit = { pcm, n ->
                if (corte != null) corte.push(pcm, n) { a, c -> proc.push(a, c) } else proc.push(pcm, n)
            }
            val chave = Dicionario.chave(idioma, frase)
            val guardado = if (cabeNoCache(frase)) cache.get(chave) else null
            if (guardado != null) {
                medidas.doCache++
                saida.comecarFraseDoCache()
                val bloco = FloatArray(1920)
                var pos = 0
                while (pos < guardado.size && !motor.cancelado && !saida.falhou) {
                    val n = min(bloco.size, guardado.size - pos)
                    for (i in 0 until n) bloco[i] = guardado[pos + i] / 32768f
                    passa(bloco, n)
                    pos += n
                }
                saida.terminarFrase()
                if (motor.cancelado || saida.falhou) return false
                continue
            }
            saida.comecarFrase(frase.length, motor.rtf)
            val bruto = if (cabeNoCache(frase)) ShortBuilder() else null
            val gerar: () -> ResultadoFala = {
                motor.falar(frase, idioma, voz) { pcm, n ->
                    bruto?.add(pcm, n)
                    passa(pcm, n)
                    !motor.cancelado && !saida.falhou
                }
            }
            val r = if (naThreadDoMotor) gerar() else motor.noMotor(gerar)
            saida.terminarFrase()
            if (medidas.motorMs == 0.0) medidas.motorMs = r.primeiroMs
            if (motor.cancelado || saida.falhou) return false
            if (!r.ok) return false
            if (bruto != null) cache.put(chave, bruto.toArray())
            if (r.amostras > 0) {
                val medido = r.totalMs / (r.amostras * 1000.0 / PhononEngine.TAXA)
                motor.rtf = 0.5 * motor.rtf + 0.5 * medido
            }
        }
        proc.finish()
        saida.terminarFrase()
        return !saida.falhou
    }

    /**
     * Entrega o audio ao Android. Se o motor for mais lento que a fala (rtf > 1), segura um pouco
     * do inicio de cada frase para o resto chegar a tempo e a fala nao engasgar.
     */
    private class SaidaDeAudio(
        private val cb: SynthesisCallback,
        private val modo: Int,
        private val velocidade: Float,
        private val t0: Long,
        private val medidas: Medidas,
        private val reservaMs: Int,
    ) {
        private val maximo = max(cb.maxBufferSize, 1024)
        private val pendente = ByteArrayOutputStream()
        private var amostrasPendentes = 0
        private var aberto = true
        private var limite = 0
        private var inicio = 0L
        private var enviadas = 0L
        var falhou = false
            private set

        /** Frase que ja esta pronta (vem do cache): toca direto, sem espera de seguranca. */
        fun comecarFraseDoCache() {
            amostrasPendentes = 0
            aberto = true
        }

        fun comecarFrase(caracteres: Int, rtf: Double) {
            amostrasPendentes = 0
            // Espera um pouco de audio antes de soltar o primeiro bloco (veja Reserva.kt).
            limite = Reserva.limite(modo, caracteres, velocidade, rtf, reservaMs, PhononEngine.TAXA)
            aberto = limite == 0 || adiante() >= 1.0
        }

        private var primeiraEscrita = 0L

        fun escrever(s: ShortArray, n: Int) {
            if (falhou) return
            if (primeiraEscrita == 0L) primeiraEscrita = SystemClock.elapsedRealtime()
            val bytes = ByteArray(n * 2)
            for (i in 0 until n) {
                bytes[2 * i] = (s[i].toInt() and 0xFF).toByte()
                bytes[2 * i + 1] = (s[i].toInt() shr 8).toByte()
            }
            if (aberto) {
                enviar(bytes)
                return
            }
            pendente.write(bytes)
            amostrasPendentes += n
            if (amostrasPendentes >= limite || adiante() >= 1.0) abrir()
        }

        fun terminarFrase() {
            if (!aberto) abrir()
        }

        private fun abrir() {
            aberto = true
            enviar(pendente.toByteArray())
            pendente.reset()
            amostrasPendentes = 0
        }

        private fun enviar(b: ByteArray) {
            if (b.isEmpty()) return
            val agora = SystemClock.elapsedRealtime()
            if (inicio == 0L) {
                inicio = agora
                medidas.primeiroSomMs = inicio - t0
                if (primeiraEscrita != 0L) medidas.esperaMs = inicio - primeiraEscrita
            } else {
                val folga = enviadas * 1000 / PhononEngine.TAXA - (agora - inicio)
                if (folga < medidas.menorFolgaMs) medidas.menorFolgaMs = folga
                if (folga < 0) medidas.lacunas++
            }
            var off = 0
            while (off < b.size) {
                val len = min(maximo, b.size - off)
                if (cb.audioAvailable(b, off, len) != TextToSpeech.SUCCESS) {
                    falhou = true
                    return
                }
                off += len
            }
            enviadas += b.size / 2
        }

        /** Segundos de fala ja entregues que ainda nao terminaram de tocar. */
        private fun adiante(): Double =
            if (inicio == 0L) 0.0
            else enviadas / PhononEngine.TAXA.toDouble() - (SystemClock.elapsedRealtime() - inicio) / 1000.0
    }
}
