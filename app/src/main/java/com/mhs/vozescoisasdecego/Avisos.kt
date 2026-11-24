package com.mhs.vozescoisasdecego

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build

/** As notificacoes do app: so as da atualizacao. */
object Avisos {
    const val CANAL = "atualizacoes"
    const val ID_DISPONIVEL = 101
    const val ID_INSTALAR = 102

    /** Mostra o aviso. Devolve false se o Android nao deixar (permissao de notificacoes negada). */
    fun mostrar(ctx: Context, id: Int, titulo: String, texto: String, aoTocar: PendingIntent): Boolean {
        if (Build.VERSION.SDK_INT >= 33 &&
            ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return false
        val gerente = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) {
            gerente.createNotificationChannel(
                NotificationChannel(CANAL, "Atualizações do app", NotificationManager.IMPORTANCE_DEFAULT),
            )
        }
        val aviso = (if (Build.VERSION.SDK_INT >= 26) Notification.Builder(ctx, CANAL) else @Suppress("DEPRECATION") Notification.Builder(ctx))
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle(titulo)
            .setContentText(texto)
            .setStyle(Notification.BigTextStyle().bigText(texto))
            .setContentIntent(aoTocar)
            .setAutoCancel(true)
            .build()
        gerente.notify(id, aviso)
        return true
    }
}
