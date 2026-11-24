package com.mhs.vozescoisasdecego

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt
import kotlin.math.tanh

/** Quem recebe um bloco de amostras deve consumi-lo na hora (o vetor e reaproveitado). */
typealias FloatSink = (FloatArray, Int) -> Unit

/**
 * Muda a velocidade sem mudar o tom (WSOLA: sobreposicao de trechos parecidos), em fluxo.
 * ratio > 1 fala mais rapido, ratio < 1 mais devagar.
 */
internal class Wsola(private val ratio: Double) {
    private val n = 576          // janela de 24 ms a 24 kHz
    private val hs = n / 2
    private val tol = 288        // procura o melhor encaixe ate 12 ms para cada lado
    private val window = FloatArray(n) { (0.5 - 0.5 * cos(2.0 * PI * (it + 0.5) / n)).toFloat() }
    private var buf = FloatArray(1 shl 15)
    private var len = 0
    private var base = 0L        // indice absoluto da amostra buf[0]
    private var nominal = 0.0    // posicao absoluta ideal do proximo trecho
    private var prevStart = -1L
    private val tail = FloatArray(hs)
    private val out = FloatArray(hs)
    private var haveTail = false

    fun push(src: FloatArray, count: Int, sink: FloatSink) {
        ensure(len + count)
        System.arraycopy(src, 0, buf, len, count)
        len += count
        process(false, Long.MAX_VALUE, sink)
    }

    fun finish(sink: FloatSink) {
        val realEnd = base + len
        val pad = tol + n + hs
        ensure(len + pad)
        java.util.Arrays.fill(buf, len, len + pad, 0f)
        len += pad
        process(true, realEnd, sink)
        if (haveTail) {
            sink(tail, hs)
            haveTail = false
        }
    }

    private fun ensure(cap: Int) {
        if (cap > buf.size) buf = buf.copyOf(max(cap, buf.size * 2))
    }

    private fun process(flush: Boolean, realEnd: Long, sink: FloatSink) {
        val ha = ratio * hs
        while (true) {
            if (flush && nominal >= realEnd) break
            val avail = base + len
            val need = if (prevStart < 0) n.toLong() else nominal.toLong() + tol + n
            if (avail < need) break
            val start = if (prevStart < 0) 0L else best()
            val o = (start - base).toInt()
            if (!haveTail) {
                for (i in 0 until hs) out[i] = buf[o + i]
            } else {
                for (i in 0 until hs) out[i] = tail[i] + window[i] * buf[o + i]
            }
            for (i in 0 until hs) tail[i] = window[hs + i] * buf[o + hs + i]
            haveTail = true
            sink(out, hs)
            prevStart = start
            nominal += ha
        }
        compact()
    }

    /** Escolhe o ponto de partida do proximo trecho que melhor continua o anterior. */
    private fun best(): Long {
        val center = nominal.toLong()
        val lo = max(center - tol, base)
        val hi = center + tol
        val t = (prevStart + hs - base).toInt()
        var bestStart = center
        var bestScore = -Double.MAX_VALUE
        var s = lo
        while (s <= hi) {
            val o = (s - base).toInt()
            var dot = 0.0
            var energy = 1e-3
            var i = 0
            while (i < hs) {
                val x = buf[o + i]
                dot += x * buf[t + i]
                energy += x * x
                i += 2
            }
            val score = dot / sqrt(energy) - 1e-6 * abs(s - center)
            if (score > bestScore) {
                bestScore = score
                bestStart = s
            }
            s++
        }
        return bestStart
    }

    private fun compact() {
        val keep = min(if (prevStart < 0) base else prevStart + hs, nominal.toLong() - tol)
        val drop = (keep - base).toInt()
        if (drop > 16384) {
            System.arraycopy(buf, drop, buf, 0, len - drop)
            len -= drop
            base += drop
        }
    }
}

/** Muda o tom lendo as amostras em outro passo (interpolacao linear), em fluxo. */
internal class Resampler(private val step: Double) {
    private var buf = FloatArray(8192)
    private var len = 0
    private var pos = 0.0
    private val tmp = FloatArray(4096)

    fun push(src: FloatArray, count: Int, sink: FloatSink) {
        if (len + count > buf.size) buf = buf.copyOf(max(len + count, buf.size * 2))
        System.arraycopy(src, 0, buf, len, count)
        len += count
        var outN = 0
        while (true) {
            val i = pos.toInt()
            if (i + 1 >= len) break
            val f = (pos - i).toFloat()
            tmp[outN++] = buf[i] * (1f - f) + buf[i + 1] * f
            if (outN == tmp.size) {
                sink(tmp, outN)
                outN = 0
            }
            pos += step
        }
        if (outN > 0) sink(tmp, outN)
        val drop = min(pos.toInt(), len)
        if (drop > 0) {
            System.arraycopy(buf, drop, buf, 0, len - drop)
            len -= drop
            pos -= drop
        }
    }
}

/**
 * O motor comeca cada frase com ~110 ms de ruido quase inaudivel antes da voz. Descarta esse trecho,
 * mantendo [preRoll] amostras antes do primeiro som (com entrada suave) para nao comer o inicio das palavras.
 * Nunca descarta mais de [maximo] amostras (padrao 250 ms).
 */
class CortaInicio(
    private val preRoll: Int,
    private val limiar: Float = 0.02f,
    private val maximo: Int = 6000,
) {
    private val tamanho = preRoll.coerceAtLeast(1)
    private val hist = FloatArray(tamanho)
    private var pos = 0
    private var vistos = 0
    private var ativo = true

    fun push(pcm: FloatArray, count: Int, sink: FloatSink) {
        if (!ativo) {
            sink(pcm, count)
            return
        }
        for (i in 0 until count) {
            if (abs(pcm[i]) >= limiar || vistos >= maximo) {
                ativo = false
                val k = min(vistos, preRoll)
                if (k > 0) {
                    val tmp = FloatArray(k)
                    for (j in 0 until k) {
                        val idx = ((pos - k + j) % tamanho + tamanho) % tamanho
                        tmp[j] = hist[idx] * ((j + 1f) / (k + 1f))
                    }
                    sink(tmp, k)
                }
                sink(pcm.copyOfRange(i, count), count - i)
                return
            }
            hist[pos] = pcm[i]
            pos = (pos + 1) % tamanho
            vistos++
        }
    }
}

/**
 * Junta o ajuste do app com o que o Android pede (100 = normal). Se [seguirAndroid] for falso, so vale o app.
 * 1,0 = ritmo/tom natural da voz, sem processamento.
 */
object Velocidade {
    const val MIN = 0.25f
    const val MAX = 4f

    fun bruta(ajuste: Float, taxaAndroid: Int, seguirAndroid: Boolean = true): Float =
        (ajuste * (if (seguirAndroid) taxaAndroid / 100f else 1f)).coerceIn(MIN, MAX)

    fun tom(ajuste: Float, tomAndroid: Int, seguirAndroid: Boolean = true): Float =
        (ajuste * (if (seguirAndroid) tomAndroid / 100f else 1f)).coerceIn(0.5f, 2.5f)
}

/**
 * Velocidade, tom e volume da fala, em fluxo (mono, float -> PCM 16 bits).
 * Para o tom subir sem mudar a duracao: estica o tempo por (velocidade / tom) e depois le mais rapido.
 */
class AudioProcessor(
    speed: Float,
    pitch: Float,
    volume: Float,
    private val out: (ShortArray, Int) -> Unit,
) {
    private val ratio = (speed / pitch).toDouble().coerceIn(0.1, 4.0) // a razao velocidade/tom pode passar de 0,25 quando o tom sobe
    private val stretch = if (abs(ratio - 1.0) < 0.02) null else Wsola(ratio)
    private val resampler = if (abs(pitch - 1f) < 0.01f) null else Resampler(pitch.toDouble())
    private val gain = volume
    private val shorts = ShortArray(4096)

    private val toGain: FloatSink = { a, n -> emit(a, n) }
    private val toResampler: FloatSink = { a, n ->
        val r = resampler
        if (r != null) r.push(a, n, toGain) else emit(a, n)
    }

    fun push(pcm: FloatArray, count: Int) {
        val s = stretch
        if (s != null) s.push(pcm, count, toResampler) else toResampler(pcm, count)
    }

    fun finish() {
        stretch?.finish(toResampler)
    }

    private fun emit(a: FloatArray, n: Int) {
        var k = 0
        for (i in 0 until n) {
            var y = a[i] * gain
            val m = abs(y)
            if (m > 0.95f) {
                val c = 0.95f + 0.05f * tanh((m - 0.95f) / 0.05f)
                y = if (y < 0) -c else c
            }
            shorts[k++] = (y * 32767f).toInt().coerceIn(-32768, 32767).toShort()
            if (k == shorts.size) {
                out(shorts, k)
                k = 0
            }
        }
        if (k > 0) out(shorts, k)
    }
}
