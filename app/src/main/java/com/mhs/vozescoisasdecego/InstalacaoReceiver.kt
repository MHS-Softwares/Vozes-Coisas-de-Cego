package com.mhs.vozescoisasdecego

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller

/** Recebe a resposta do instalador do Android depois que o app entrega a atualizacao. */
class InstalacaoReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        val mensagem = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
        @Suppress("DEPRECATION")
        val confirmar = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
        Atualizador.resultadoDaInstalacao(context.applicationContext, status, mensagem, confirmar)
    }
}
