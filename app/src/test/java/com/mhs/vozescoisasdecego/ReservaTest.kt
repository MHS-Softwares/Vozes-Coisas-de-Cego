package com.mhs.vozescoisasdecego

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReservaTest {
    private val taxa = 24000

    @Test
    fun inicioRapidoNaoEspera() {
        assertEquals(0, Reserva.limite(0, 80, 1f, 0.3, 200, taxa))
    }

    @Test
    fun semEngasgosEsperaAFraseInteira() {
        assertEquals(Int.MAX_VALUE, Reserva.limite(2, 80, 1f, 0.3, 200, taxa))
    }

    @Test
    fun automaticoComMotorRapidoGuardaSoAReserva() {
        // rtf 0,3: o calculo antigo daria 0; agora guarda 200 ms de audio
        assertEquals(4800, Reserva.limite(1, 80, 1f, 0.3, 200, taxa))
        assertEquals(2400, Reserva.limite(1, 80, 0.9f, 0.28, 100, taxa))
        assertEquals(8400, Reserva.limite(1, 200, 1f, 0.3, 350, taxa))
    }

    @Test
    fun reservaZeroVoltaAoComportamentoAntigoComMotorRapido() {
        assertEquals(0, Reserva.limite(1, 80, 1f, 0.3, 0, taxa))
    }

    @Test
    fun motorLentoEsperaMaisMasNoMaximoUmQuartoDeSegundo() {
        val l = Reserva.limite(1, 400, 1f, 1.5, 200, taxa)
        assertEquals(taxa / 4, l)
        assertTrue(l >= 200 * taxa / 1000)
    }

    @Test
    fun opcoesDaTela() {
        assertEquals(listOf(0, 100, 200, 350), Reserva.OPCOES_MS.toList())
        assertEquals(200, Reserva.ms(Reserva.PADRAO))
        assertEquals(0, Reserva.ms(-5))
        assertEquals(350, Reserva.ms(99))
    }
}
