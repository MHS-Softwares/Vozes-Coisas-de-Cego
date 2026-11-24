package com.mhs.vozescoisasdecego

import android.content.Context
import android.os.SystemClock
import android.system.Os
import android.util.Log
import com.sun.jna.Callback
import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.Structure
import java.io.File
import java.util.concurrent.Callable
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.min

/** Funcoes do motor nativo (libptts_ffi.so). Os booleanos do C voltam como 1 byte. */
interface PttsLib : Library {
    fun ptts_new(modelDir: String, unit: Int, lang: String): Pointer?
    fun ptts_speak(handle: Pointer, text: String, cb: FrameCb, user: Pointer?, res: PttsResult): Byte
    fun ptts_set_voice(handle: Pointer, name: String): Byte
    fun ptts_set_language(handle: Pointer, code: String): Byte
    fun ptts_last_error(handle: Pointer?): String?
    fun ptts_free(handle: Pointer)
}

interface FrameCb : Callback {
    fun invoke(pcm: Pointer, count: Long, user: Pointer?): Byte
}

@Structure.FieldOrder("frames", "samples", "ttfaMs", "totalMs")
open class PttsResult : Structure() {
    @JvmField var frames: Int = 0
    @JvmField var samples: Long = 0
    @JvmField var ttfaMs: Double = 0.0
    @JvmField var totalMs: Double = 0.0
}

class ResultadoFala(val ok: Boolean, val amostras: Long, val totalMs: Double, val primeiroMs: Double)

/**
 * Dono do motor de voz. O motor so e usado por UMA thread (a "vozes-tts-engine"):
 * carregar, falar, trocar de voz e liberar acontecem sempre nela.
 */
class PhononEngine private constructor(private val app: Context) {
    companion object {
        const val TAXA = 24000
        private const val TAG = "VozesTTS"

        @Volatile
        private var instancia: PhononEngine? = null

        fun get(ctx: Context): PhononEngine =
            instancia ?: synchronized(this) {
                instancia ?: PhononEngine(ctx.applicationContext).also { instancia = it }
            }

        /** Nucleos "grandes" do processador, no maximo 4 (deixa folga para a thread que toca o som); se o Android nao deixar ler, usa ate 4. */
        fun nucleosAutomatico(): Int {
            val n = Runtime.getRuntime().availableProcessors()
            val freqs = (0 until n).mapNotNull {
                runCatching {
                    File("/sys/devices/system/cpu/cpu$it/cpufreq/cpuinfo_max_freq").readText().trim().toLong()
                }.getOrNull()
            }
            if (freqs.size == n && n > 0) {
                val maior = freqs.max()
                val grandes = freqs.count { it >= maior * 0.7 }
                return grandes.coerceIn(2, 4)
            }
            return min(n, 4).coerceAtLeast(1)
        }

        private fun temDotProd(): Boolean = runCatching {
            File("/proc/cpuinfo").readLines().any { it.startsWith("Features") && it.contains("asimddp") }
        }.getOrDefault(false)
    }

    // Prioridade normal de proposito: com prioridade de audio as threads de calculo atrapalhavam a thread
    // que toca o som (mesmo processo) e a fala dava paradinhas.
    private val executor = Executors.newSingleThreadExecutor { r -> Thread(r, "vozes-tts-engine") }

    /** Audio pronto de frases ja faladas (e das frases comuns, geradas em segundo plano): um cache para cada voz. */
    private val caches = ConcurrentHashMap<String, CacheFala>()

    fun cacheDa(voz: String): CacheFala =
        caches.getOrPut(voz) { CacheFala(Vozes.pastaDoCache(app.cacheDir, voz), ModelStore.PACOTE) }

    /** Qual voz o motor esta usando agora (so muda na thread do motor). */
    private val vozAtiva = VozAtiva()

    /** Quantos pedidos de fala estao em andamento (a pre-geracao so trabalha com zero). */
    val pedidosAtivos = AtomicInteger(0)

    @Volatile
    var ultimaFalaMs = 0L

    @Volatile
    var abortarPreGeracao = false

    private val indicePre = ConcurrentHashMap<String, AtomicInteger>()
    private val preIniciada = AtomicBoolean(false)
    private val agenda by lazy {
        Executors.newSingleThreadScheduledExecutor { r -> Thread(r, "vozes-tts-pregeracao").apply { isDaemon = true } }
    }

    @Volatile
    var nucleosUsados = 0

    @Volatile
    var biblioteca = ""

    private var lib: PttsLib? = null
    @Volatile
    private var handle: Pointer? = null
    private var idiomaAtual = ""

    @Volatile
    var cancelado = false

    /** Quanto tempo o motor gasta para cada segundo de fala (menor que 1 = mais rapido que o tempo real). */
    @Volatile
    var rtf = 1.0

    @Volatile
    var ultimoErro: String? = null

    fun preparar() {
        executor.execute {
            runCatching { Vozes.limparCachesAntigos(app.cacheDir) }
            try {
                garantir()
            } catch (t: Throwable) {
                ultimoErro = t.message ?: t.javaClass.simpleName
                Log.e(TAG, "Falha ao preparar o motor", t)
            }
        }
    }

    fun <T> noMotor(bloco: () -> T): T = executor.submit(Callable { bloco() }).get()

    /**
     * Chamado quando o usuario escolhe outra voz na tela: deixa a voz nova pronta no motor.
     * Nao precisa reiniciar nada: a proxima fala (de qualquer leitor de telas) ja sai com ela.
     */
    fun aplicarVozAgora(voz: String) {
        executor.execute {
            try {
                garantir()
                aplicarVoz(Vozes.valida(voz))
            } catch (t: Throwable) {
                ultimoErro = t.message ?: t.javaClass.simpleName
                Log.e(TAG, "Falha ao trocar de voz", t)
            }
        }
    }

    /** Comeca a gerar as frases comuns, uma a cada poucos segundos, so quando ninguem esta usando a voz. */
    fun iniciarPreGeracao() {
        if (!preIniciada.compareAndSet(false, true)) return
        agenda.scheduleWithFixedDelay({
            try {
                passoPreGeracao()
            } catch (t: Throwable) {
                Log.w(TAG, "Pre-geracao", t)
            }
        }, 25, 3, TimeUnit.SECONDS)
    }

    private fun passoPreGeracao() {
        val prefs = Prefs(app)
        if (!prefs.preGerar || pedidosAtivos.get() > 0 || handle == null) return
        if (SystemClock.elapsedRealtime() - ultimaFalaMs < 10_000) return
        val voz = prefs.voz // as frases comuns sao preparadas para a voz que esta escolhida agora
        val cache = cacheDa(voz)
        val indice = indicePre.getOrPut(voz) { AtomicInteger(0) }
        while (true) {
            val i = indice.getAndIncrement()
            if (i >= FrasesComuns.LISTA.size) return
            val texto = Dicionario.aplicar(FrasesComuns.LISTA[i], prefs.dicionario, prefs.corrigirPronuncia)
            val chave = Dicionario.chave("pt", texto)
            if (cache.contem(chave)) continue
            executor.execute { gerarParaCache(texto, chave, voz) }
            return
        }
    }

    /** Na thread do motor. Desiste na hora se chegar um pedido de fala de verdade. */
    private fun gerarParaCache(texto: String, chave: String, voz: String) {
        if (pedidosAtivos.get() > 0 || handle == null) return
        if (cancelado) cancelado = false
        abortarPreGeracao = false
        val b = ShortBuilder()
        val r = falar(texto, "pt", voz) { pcm, n ->
            b.add(pcm, n)
            !abortarPreGeracao && pedidosAtivos.get() == 0
        }
        if (r.ok) cacheDa(voz).put(chave, b.toArray())
    }

    private val preparoAtivo = AtomicBoolean(false)

    /** Ha um preparo manual das frases comuns em andamento? */
    val preparando: Boolean get() = preparoAtivo.get()

    /**
     * Gera de uma vez as frases comuns que ainda faltam no cache de [voz] (botao da tela). Uma frase por vez, na
     * thread do motor: se o leitor de telas pedir uma fala no meio, ela entra na fila entre duas frases.
     * [aoProgredir] recebe quantas ja estao prontas e o total; [aoTerminar] recebe se todas ficaram prontas.
     */
    fun prepararFrasesAgora(voz: String, aoProgredir: (Int, Int) -> Unit, aoTerminar: (Boolean) -> Unit) {
        if (!preparoAtivo.compareAndSet(false, true)) return
        Thread({
            var todas = false
            try {
                val prefs = Prefs(app)
                val cache = cacheDa(voz)
                val itens = FrasesComuns.LISTA.map {
                    val texto = Dicionario.aplicar(it, prefs.dicionario, prefs.corrigirPronuncia)
                    texto to Dicionario.chave("pt", texto)
                }
                aoProgredir(itens.count { cache.contem(it.second) }, itens.size)
                for ((texto, chave) in itens) {
                    if (!cache.contem(chave)) noMotor { gerarFrase(texto, chave, voz) }
                    aoProgredir(itens.count { cache.contem(it.second) }, itens.size)
                }
                todas = itens.all { cache.contem(it.second) }
            } catch (t: Throwable) {
                ultimoErro = t.message ?: t.javaClass.simpleName
                Log.w(TAG, "Preparo das frases comuns", t)
            } finally {
                preparoAtivo.set(false)
                aoTerminar(todas)
            }
        }, "vozes-tts-preparo").start()
    }

    /** Na thread do motor. Gera uma frase inteira para o cache, sem desistir por causa de pedidos de fala. */
    private fun gerarFrase(texto: String, chave: String, voz: String) {
        if (pedidosAtivos.get() == 0 && cancelado) cancelado = false
        val b = ShortBuilder()
        val r = falar(texto, "pt", voz) { pcm, n ->
            b.add(pcm, n)
            !cancelado
        }
        if (r.ok) cacheDa(voz).put(chave, b.toArray())
    }

    // ---- daqui para baixo: so na thread do motor ----

    fun garantir() {
        if (handle != null) return
        val pasta = ModelStore.garantir(app)
        val prefs = Prefs(app)
        val nucleos = if (prefs.nucleos > 0) prefs.nucleos else nucleosAutomatico()
        runCatching { Os.setenv("RAYON_NUM_THREADS", nucleos.toString(), true) }
        System.setProperty("jna.encoding", "UTF-8")

        val dir = File(app.applicationInfo.nativeLibraryDir)
        val nomes = if (temDotProd()) listOf("libptts_ffi.so", "libptts_ffi_compat.so")
        else listOf("libptts_ffi_compat.so", "libptts_ffi.so")
        var erro = "motor nao encontrado"
        for (nome in nomes) {
            val arquivo = File(dir, nome)
            if (!arquivo.exists()) continue
            try {
                val l = Native.load(arquivo.absolutePath, PttsLib::class.java)
                val h = l.ptts_new(pasta.absolutePath, 0, "pt")
                if (h == null) {
                    erro = "$nome: " + (l.ptts_last_error(null) ?: "nao carregou o modelo")
                    continue
                }
                val voz = Vozes.valida(prefs.voz)
                if (l.ptts_set_voice(h, voz).toInt() == 0) {
                    erro = "A voz $voz nao foi aceita: " + (l.ptts_last_error(h) ?: "")
                    l.ptts_free(h)
                    continue
                }
                vozAtiva.definir(voz)
                lib = l
                handle = h
                idiomaAtual = "pt"
                nucleosUsados = nucleos
                biblioteca = nome
                Log.i(TAG, "Motor carregado com $nome, $nucleos nucleos")
                // Aquece: a primeira fala depois de carregar le o modelo do armazenamento e e bem mais lenta.
                runCatching { falar("Olá.", "pt", voz) { _, _ -> true } }
                return
            } catch (t: Throwable) {
                erro = "$nome: ${t.message}"
                Log.e(TAG, "Falha com $nome", t)
            }
        }
        throw IllegalStateException(erro)
    }

    /** Poe a voz pedida no motor, se ainda nao for a que esta nele (leva cerca de 1 ms). */
    private fun aplicarVoz(voz: String) {
        val l = lib!!
        val h = handle!!
        val ok = vozAtiva.trocarSePreciso(voz) { nome ->
            val aceita = l.ptts_set_voice(h, nome).toInt() != 0
            if (!aceita) ultimoErro = "A voz $nome nao foi aceita: " + (l.ptts_last_error(h) ?: "")
            aceita
        }
        if (!ok) throw IllegalStateException(ultimoErro ?: "A voz $voz nao foi aceita")
        Log.i(TAG, "Voz em uso: $voz")
    }

    /**
     * Fala um texto curto com a voz [voz] e entrega o audio (float, 24 kHz) por [sink]; o sink devolve false
     * para parar. Se a voz pedida for outra, o motor troca de voz na hora, sem recarregar nada.
     */
    fun falar(texto: String, idioma: String, voz: String, sink: (FloatArray, Int) -> Boolean): ResultadoFala {
        garantir()
        aplicarVoz(voz)
        val l = lib!!
        val h = handle!!
        if (idioma != idiomaAtual) {
            if (l.ptts_set_language(h, idioma).toInt() == 0) {
                throw IllegalStateException("Idioma nao aceito: $idioma")
            }
            idiomaAtual = idioma
        }
        val res = PttsResult()
        var falhaNoSink: Throwable? = null
        val cb = object : FrameCb {
            override fun invoke(pcm: Pointer, count: Long, user: Pointer?): Byte {
                if (cancelado) return 0
                return try {
                    val n = count.toInt()
                    if (sink(pcm.getFloatArray(0, n), n) && !cancelado) 1 else 0
                } catch (t: Throwable) {
                    falhaNoSink = t
                    0
                }
            }
        }
        val ok = l.ptts_speak(h, texto, cb, null, res).toInt() != 0
        falhaNoSink?.let { throw it }
        if (!ok && !cancelado) ultimoErro = l.ptts_last_error(h)
        return ResultadoFala(ok, res.samples, res.totalMs, res.ttfaMs)
    }

    fun liberar() {
        val l = lib
        val h = handle
        if (l != null && h != null) l.ptts_free(h)
        handle = null
        lib = null
        vozAtiva.definirDesconhecida()
    }
}
