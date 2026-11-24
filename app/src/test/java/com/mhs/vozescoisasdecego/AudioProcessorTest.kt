package com.mhs.vozescoisasdecego

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

class AudioProcessorTest {
    private val taxa = 24000

    private fun seno(freq: Double, segundos: Double): FloatArray =
        FloatArray((taxa * segundos).toInt()) { (0.5 * sin(2 * PI * freq * it / taxa)).toFloat() }

    /** Passa o sinal em blocos de tamanhos variados, como o motor faz, e junta a saida. */
    private fun processar(sinal: FloatArray, vel: Float, tom: Float, vol: Float): ShortArray {
        val saida = ArrayList<Short>()
        val p = AudioProcessor(vel, tom, vol) { s, n -> for (i in 0 until n) saida.add(s[i]) }
        var pos = 0
        val tamanhos = intArrayOf(1920, 640, 3000, 111, 2048)
        var k = 0
        while (pos < sinal.size) {
            val n = minOf(tamanhos[k++ % tamanhos.size], sinal.size - pos)
            p.push(sinal.copyOfRange(pos, pos + n), n)
            pos += n
        }
        p.finish()
        return saida.toShortArray()
    }

    /** Frequencia estimada pelos cruzamentos de zero (so para sinais limpos). */
    private fun frequencia(s: ShortArray): Double {
        var cruzamentos = 0
        for (i in 1 until s.size) if ((s[i - 1] < 0) != (s[i] < 0)) cruzamentos++
        return cruzamentos / 2.0 / (s.size.toDouble() / taxa)
    }

    @Test
    fun velocidadeNormalMantemDuracaoEVolume() {
        val r = processar(seno(200.0, 1.0), 1f, 1f, 1f)
        assertEquals(taxa.toDouble(), r.size.toDouble(), taxa * 0.01)
        assertEquals(16383.0, r.maxOf { it.toInt() }.toDouble(), 600.0)
    }

    @Test
    fun velocidadeDobradaDeixaMetadeDaDuracaoSemMudarOTom() {
        val r = processar(seno(200.0, 2.0), 2f, 1f, 1f)
        assertEquals(taxa * 1.0, r.size.toDouble(), taxa * 0.08)
        assertEquals(200.0, frequencia(r), 12.0)
    }

    @Test
    fun velocidadeMetadeDobraADuracaoSemMudarOTom() {
        val r = processar(seno(200.0, 1.0), 0.5f, 1f, 1f)
        assertEquals(taxa * 2.0, r.size.toDouble(), taxa * 0.12)
        assertEquals(200.0, frequencia(r), 12.0)
    }

    @Test
    fun velocidadeMuitoLentaQuadruplicaADuracao() {
        val r = processar(seno(200.0, 0.5), 0.25f, 1f, 1f)
        assertEquals(taxa * 2.0, r.size.toDouble(), taxa * 0.15)
        assertEquals(200.0, frequencia(r), 12.0)
    }

    /** 110 ms de ruido fraco e depois voz (seno forte), passados em blocos de 1920 como o motor faz. */
    private fun cortar(preRoll: Int, ruidoMs: Int): Pair<Int, FloatArray> {
        val ruido = FloatArray(taxa * ruidoMs / 1000) { if (it % 2 == 0) 0.005f else -0.005f }
        val sinal = ruido + seno(200.0, 0.5)
        val saida = ArrayList<Float>()
        val c = CortaInicio(preRoll)
        var pos = 0
        while (pos < sinal.size) {
            val n = minOf(1920, sinal.size - pos)
            c.push(sinal.copyOfRange(pos, pos + n), n) { a, k -> for (i in 0 until k) saida.add(a[i]) }
            pos += n
        }
        return Pair(sinal.size, saida.toFloatArray())
    }

    @Test
    fun cortaOSilencioDoComecoMantendoAFolga() {
        val (total, r) = cortar(360, 110)
        val ruido = taxa * 110 / 1000
        assertEquals((total - ruido + 360).toDouble(), r.size.toDouble(), 3.0) // so sobra a folga de 15 ms
        // o som forte nao foi tocado
        assertEquals(0.5, r.takeLast(2000).maxOf { kotlin.math.abs(it) }.toDouble(), 0.01)
        // a folga entra suave (comeca quase em zero)
        assertTrue(kotlin.math.abs(r[0]) < 0.001f)
    }

    @Test
    fun naoCortaQuandoAVozJaComecaNaHora() {
        val (total, r) = cortar(360, 0)
        assertEquals(total, r.size)
    }

    @Test
    fun nuncaDescartaMaisQueOLimite() {
        val ruido = FloatArray(taxa) { if (it % 2 == 0) 0.005f else -0.005f } // 1 s de ruido fraco, sem voz
        val saida = ArrayList<Float>()
        val c = CortaInicio(360, maximo = 6000)
        c.push(ruido, ruido.size) { a, k -> for (i in 0 until k) saida.add(a[i]) }
        assertTrue(saida.size >= taxa - 6000)               // depois de 250 ms passa tudo
    }

    @Test
    fun velocidadeSegueOAndroidQuandoLigado() {
        assertEquals(1.0f, Velocidade.bruta(1.0f, 100), 1e-6f)
        assertEquals(0.9f, Velocidade.bruta(1.0f, 90), 1e-6f)      // TalkBack padrao
        assertEquals(1.8f, Velocidade.bruta(2.0f, 90), 1e-6f)
        assertEquals(Velocidade.MAX, Velocidade.bruta(3.0f, 600), 1e-6f)
        assertEquals(Velocidade.MIN, Velocidade.bruta(0.25f, 10), 1e-6f)
    }

    @Test
    fun velocidadeIndependenteIgnoraOAndroid() {
        assertEquals(1.0f, Velocidade.bruta(1.0f, 90, false), 1e-6f)
        assertEquals(0.5f, Velocidade.bruta(0.5f, 300, false), 1e-6f)
        assertEquals(1.0f, Velocidade.tom(1.0f, 75, false), 1e-6f)
        assertEquals(0.75f, Velocidade.tom(1.0f, 75, true), 1e-6f)
    }

    @Test
    fun tomDobradoSobeUmaOitavaSemMudarADuracao() {
        val r = processar(seno(200.0, 1.0), 1f, 2f, 1f)
        assertEquals(taxa * 1.0, r.size.toDouble(), taxa * 0.08)
        assertEquals(400.0, frequencia(r), 20.0)
    }

    @Test
    fun tomEVelocidadeJuntos() {
        val r = processar(seno(200.0, 2.0), 1.5f, 1.25f, 1f)
        assertEquals(taxa * 2.0 / 1.5, r.size.toDouble(), taxa * 0.12)
        assertEquals(250.0, frequencia(r), 15.0)
    }

    @Test
    fun volumeZeroSilenciaEVolumeAltoNaoEstoura() {
        val mudo = processar(seno(200.0, 0.5), 1f, 1f, 0f)
        assertTrue(mudo.all { it.toInt() == 0 })
        val alto = processar(seno(200.0, 0.5), 1f, 1f, 2f)
        assertTrue(alto.all { abs(it.toInt()) <= 32767 })
        assertTrue(alto.maxOf { it.toInt() } > 28000)
    }

    @Test
    fun textoVazioOuMuitoCurtoNaoQuebra() {
        assertEquals(0, processar(FloatArray(0), 1.5f, 1.2f, 1f).size)
        assertTrue(processar(FloatArray(100) { 0.3f }, 2f, 1f, 1f).isNotEmpty())
    }
}
