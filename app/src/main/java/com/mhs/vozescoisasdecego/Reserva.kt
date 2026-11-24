package com.mhs.vozescoisasdecego

import kotlin.math.max
import kotlin.math.min

/**
 * Quanto audio guardar antes de comecar a entregar uma frase gerada pelo motor (modo Automatico).
 *
 * O Android comeca a tocar assim que chega o primeiro bloco; se o bloco seguinte atrasar um pouco (o motor
 * acabou de comecar, o celular esta ocupado com o leitor de telas...), o som para por um instante. Guardar uns
 * 200 ms de audio antes de soltar o primeiro bloco evita isso. Como o motor gera bem mais rapido que a fala,
 * esses 200 ms de audio custam so uns 60 ms de espera de verdade.
 */
object Reserva {
    /** As opcoes da tela, em milissegundos de audio. */
    val OPCOES_MS = intArrayOf(0, 100, 200, 350)
    const val PADRAO = 2 // 200 ms

    fun ms(indice: Int): Int = OPCOES_MS[indice.coerceIn(0, OPCOES_MS.size - 1)]

    /**
     * Amostras (taxa [taxa]) a juntar antes de abrir a saida. [modo]: 0 inicio rapido (nao espera),
     * 1 automatico, 2 sem engasgos (espera a frase inteira).
     */
    fun limite(modo: Int, caracteres: Int, velocidade: Float, rtf: Double, reservaMs: Int, taxa: Int): Int =
        when (modo) {
            0 -> 0
            2 -> Int.MAX_VALUE
            else -> {
                val estimativaSeg = caracteres / 13.0 / velocidade
                val fracao = (1.0 - 1.0 / (velocidade * rtf * 1.15)).coerceIn(0.0, 1.0)
                // Se o motor for lento, espera mais (no maximo 0,25 s); sempre guarda ao menos a reserva.
                val calculado = min((estimativaSeg * taxa * fracao).toInt(), taxa / 4)
                max(calculado, reservaMs * taxa / 1000)
            }
        }
}
