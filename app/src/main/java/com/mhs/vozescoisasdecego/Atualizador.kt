package com.mhs.vozescoisasdecego

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors

/**
 * Atualiza o app pelas versoes do GitHub, no jeito do SandFly: verifica, mostra o que mudou, baixa continuando de
 * onde parou quando a conexao cai, confere que o arquivo e deste app (mesmo pacote, versao mais nova, mesmo
 * certificado) e entrega para o instalador do Android, que pede a confirmacao do usuario.
 *
 * Fica em memoria enquanto o app vive, entao a tela pode fechar e abrir no meio do download sem perder o andamento.
 */
object Atualizador {
    private const val TAG = "Atualizador"
    private const val UM_DIA_MS = 24L * 60 * 60 * 1000

    sealed class Estado {
        object Ocioso : Estado()
        object Verificando : Estado()
        object Atualizado : Estado()
        data class Disponivel(val lancamento: Lancamento) : Estado()
        data class Baixando(val lancamento: Lancamento, val andamento: String) : Estado()
        data class PrecisaPermissao(val lancamento: Lancamento) : Estado()
        object Instalando : Estado()
        data class Erro(val mensagem: String, val lancamento: Lancamento? = null) : Estado()
    }

    /** O arquivo baixado nao e uma versao deste app: nao se instala. */
    private class Recusado(mensagem: String) : Exception(mensagem)

    @Volatile
    var estado: Estado = Estado.Ocioso
        private set

    private val ouvintes = CopyOnWriteArrayList<(Estado) -> Unit>()
    private val executor = Executors.newSingleThreadExecutor { r -> Thread(r, "atualizador").apply { isDaemon = true } }

    /** Avisa a [f] a cada mudanca (de qualquer thread) e ja a chama com o estado de agora. Devolve quem remove. */
    fun observar(f: (Estado) -> Unit): () -> Unit {
        ouvintes.add(f)
        f(estado)
        return { ouvintes.remove(f) }
    }

    /** A versao que o app esta oferecendo, lembrada para o botao continuar valendo depois de um erro ou de um cancelamento. */
    @Volatile
    private var ultimo: Lancamento? = null

    private fun mudar(novo: Estado) {
        when (novo) {
            is Estado.Disponivel -> ultimo = novo.lancamento
            is Estado.Baixando -> ultimo = novo.lancamento
            is Estado.PrecisaPermissao -> ultimo = novo.lancamento
            is Estado.Erro -> novo.lancamento?.let { ultimo = it }
            is Estado.Atualizado -> ultimo = null
            else -> Unit
        }
        estado = novo
        for (o in ouvintes) runCatching { o(novo) }
    }

    fun versaoInstalada(ctx: Context): String =
        runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }.getOrNull() ?: "0"

    private fun ocupado() = estado is Estado.Verificando || estado is Estado.Baixando || estado is Estado.Instalando

    // ------------------------------------------------------------------ verificar

    /** Pergunta ao GitHub se ha versao nova. [silencioso]: so avisa por notificacao, uma vez por versao. */
    fun verificar(ctx: Context, silencioso: Boolean = false) {
        if (ocupado()) return
        val app = ctx.applicationContext
        mudar(Estado.Verificando)
        executor.execute {
            try {
                val l = buscarUltima()
                Prefs(app).ultimaChecagem = System.currentTimeMillis()
                if (l != null && Atualizacao.haNova(versaoInstalada(app), l.versao)) {
                    mudar(Estado.Disponivel(l))
                    if (silencioso) avisarNovaVersao(app, l)
                } else {
                    mudar(Estado.Atualizado)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Nao consegui verificar", e)
                mudar(
                    if (silencioso) Estado.Ocioso
                    else Estado.Erro("Não consegui verificar as atualizações. Confira a internet e tente de novo."),
                )
            }
        }
    }

    /** Uma vez por dia, sem ninguem pedir (se o usuario nao desligou). Chamada ao abrir o app e ao iniciar a voz. */
    fun checagemAutomatica(ctx: Context) {
        val app = ctx.applicationContext
        val prefs = Prefs(app)
        if (!prefs.verificarAtualizacoes || ocupado()) return
        if (System.currentTimeMillis() - prefs.ultimaChecagem < UM_DIA_MS) return
        verificar(app, silencioso = true)
    }

    private fun avisarNovaVersao(app: Context, l: Lancamento) {
        val prefs = Prefs(app)
        if (prefs.versaoAnunciada == l.versao) return
        val abrir = Intent(app, MainActivity::class.java)
            .putExtra(MainActivity.EXTRA_ATUALIZAR, true)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val pendente = PendingIntent.getActivity(app, 1, abrir, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        if (Avisos.mostrar(app, Avisos.ID_DISPONIVEL, "Atualização das Vozes Coisas de Cego",
                "A versão ${l.versao} está disponível. Toque para abrir e instalar.", pendente)) {
            prefs.versaoAnunciada = l.versao
        }
    }

    /** Devolve a ultima versao publicada, ou nulo se ainda nao ha nenhuma (o GitHub responde 404). */
    private fun buscarUltima(): Lancamento? {
        val c = URL(Atualizacao.URL_ULTIMA).openConnection() as HttpURLConnection
        try {
            c.connectTimeout = 15_000
            c.readTimeout = 20_000
            c.setRequestProperty("Accept", "application/vnd.github+json")
            c.setRequestProperty("User-Agent", "VozesCoisasDeCego")
            return when (val codigo = c.responseCode) {
                200 -> Atualizacao.interpretar(c.inputStream.bufferedReader().use { it.readText() })
                404 -> null
                else -> throw IOException("O GitHub respondeu $codigo.")
            }
        } finally {
            c.disconnect()
        }
    }

    // ------------------------------------------------------------------ baixar e instalar

    /** A versao que o botao Baixar e instalar vai baixar: so enquanto ha uma oferecida. */
    fun oferecida(): Lancamento? = when (estado) {
        is Estado.Disponivel, is Estado.PrecisaPermissao, is Estado.Erro -> ultimo
        else -> null
    }

    /** Baixa a versao oferecida e abre o instalador do Android. A primeira vez pede a permissao de instalar apps. */
    fun baixarEInstalar(ctx: Context) {
        val l = oferecida() ?: return
        if (ocupado()) return
        val app = ctx.applicationContext
        if (!app.packageManager.canRequestPackageInstalls()) {
            runCatching {
                app.startActivity(
                    Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${app.packageName}"))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }
            mudar(Estado.PrecisaPermissao(l))
            return
        }
        mudar(Estado.Baixando(l, "0 por cento"))
        executor.execute {
            val trava = (app.getSystemService(Context.POWER_SERVICE) as PowerManager)
                .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "VozesCoisasDeCego:atualizacao")
                .apply { setReferenceCounted(false); acquire(20L * 60 * 1000) }
            try {
                val pasta = File(app.cacheDir, "atualizacao").apply { mkdirs() }
                val parcial = File(pasta, l.nomeApk + ".parcial")
                val pronto = File(pasta, l.nomeApk)
                pasta.listFiles()?.filter { it.name != parcial.name && it.name != pronto.name }?.forEach { it.delete() }
                if (!pronto.exists()) {
                    val inicio = System.currentTimeMillis()
                    var ultimaFaixa = -1
                    Baixador().baixar(l.urlApk, parcial, l.tamanho) { baixado, total ->
                        // so muda de 10 em 10 por cento: o leitor de telas nao precisa ouvir cada passo
                        val faixa = if (total > 0) (baixado * 10 / total).toInt() else 0
                        if (faixa != ultimaFaixa) {
                            ultimaFaixa = faixa
                            mudar(Estado.Baixando(l, Atualizacao.andamento(baixado, total, System.currentTimeMillis() - inicio)))
                        }
                    }
                    if (!parcial.renameTo(pronto)) throw IOException("Não consegui guardar o arquivo baixado.")
                }
                conferir(app, pronto)
                mudar(Estado.Instalando)
                instalar(app, pronto)
            } catch (e: Recusado) {
                File(app.cacheDir, "atualizacao").listFiles()?.forEach { it.delete() }
                mudar(Estado.Erro(e.message ?: "O arquivo baixado não foi aceito.", l))
            } catch (e: Baixador.Falha) {
                Log.w(TAG, "Download falhou", e)
                mudar(Estado.Erro("Não consegui baixar a atualização. Toque em Baixar e instalar para continuar de onde parou.", l))
            } catch (e: Exception) {
                Log.w(TAG, "Instalacao falhou", e)
                mudar(Estado.Erro("Não consegui instalar: ${e.message ?: e.javaClass.simpleName}", l))
            } finally {
                runCatching { if (trava.isHeld) trava.release() }
            }
        }
    }

    // ------------------------------------------------------------------ conferir o arquivo

    private fun versaoDoPacote(info: PackageInfo): Long =
        if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else @Suppress("DEPRECATION") info.versionCode.toLong()

    @Suppress("DEPRECATION")
    private fun assinaturas(info: PackageInfo): Set<String> {
        val bytes = if (Build.VERSION.SDK_INT >= 28) {
            val si = info.signingInfo
            if (si == null) emptyList()
            else (if (si.hasMultipleSigners()) si.apkContentsSigners else si.signingCertificateHistory).map { it.toByteArray() }
        } else {
            info.signatures?.map { it.toByteArray() } ?: emptyList()
        }
        return bytes.map { b -> MessageDigest.getInstance("SHA-256").digest(b).joinToString("") { "%02x".format(it) } }.toSet()
    }

    /** O arquivo tem de ser deste app, mais novo e assinado com o mesmo certificado, como o SandFly exige. */
    private fun conferir(app: Context, arquivo: File) {
        val pm = app.packageManager
        val flags = if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else @Suppress("DEPRECATION") PackageManager.GET_SIGNATURES
        val novo = pm.getPackageArchiveInfo(arquivo.path, flags)
            ?: throw Recusado("O arquivo baixado não é um app. Não foi instalado.")
        val atual = pm.getPackageInfo(app.packageName, flags)
        val naoEDoApp = "O arquivo baixado não é uma versão deste app. Não foi instalado."
        if (novo.packageName != app.packageName) throw Recusado(naoEDoApp)
        if (versaoDoPacote(novo) <= versaoDoPacote(atual)) throw Recusado("A versão baixada não é mais nova que a instalada.")
        val assinNova = assinaturas(novo)
        // Alguns celulares nao entregam o certificado de um arquivo ainda nao instalado; o Android confere de novo ao instalar.
        if (assinNova.isNotEmpty() && assinNova.intersect(assinaturas(atual)).isEmpty()) throw Recusado(naoEDoApp)
    }

    // ------------------------------------------------------------------ instalar

    private fun instalar(app: Context, arquivo: File) {
        val instalador = app.packageManager.packageInstaller
        instalador.mySessions.forEach { runCatching { instalador.abandonSession(it.sessionId) } }
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(app.packageName)
            if (Build.VERSION.SDK_INT >= 31) setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
        }
        val id = instalador.createSession(params)
        instalador.openSession(id).use { sessao ->
            arquivo.inputStream().use { entrada ->
                sessao.openWrite("vozes.apk", 0, arquivo.length()).use { saida ->
                    entrada.copyTo(saida, 1 shl 16)
                    sessao.fsync(saida)
                }
            }
            val resposta = Intent(app, InstalacaoReceiver::class.java).setPackage(app.packageName)
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)
            sessao.commit(PendingIntent.getBroadcast(app, id, resposta, flags).intentSender)
        }
    }

    /** O instalador do Android respondeu (chamado pelo [InstalacaoReceiver]). */
    fun resultadoDaInstalacao(app: Context, status: Int, mensagem: String?, confirmar: Intent?) {
        when (status) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                if (confirmar == null) return
                confirmar.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                val aberta = runCatching { app.startActivity(confirmar) }.isSuccess
                // Se o Android nao deixar abrir de segundo plano, o aviso leva ao mesmo lugar.
                val pendente = PendingIntent.getActivity(app, 2, confirmar, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                Avisos.mostrar(app, Avisos.ID_INSTALAR, "Atualização baixada",
                    "Toque para confirmar a instalação da nova versão.", pendente)
                if (aberta) mudar(Estado.Instalando)
            }
            PackageInstaller.STATUS_SUCCESS -> {
                File(app.cacheDir, "atualizacao").deleteRecursively()
                mudar(Estado.Atualizado)
            }
            else -> {
                Log.w(TAG, "Instalacao nao concluida: $status $mensagem")
                val l = ultimo
                mudar(
                    Estado.Erro(
                        if (status == PackageInstaller.STATUS_FAILURE_ABORTED) "A instalação foi cancelada. Toque em Baixar e instalar para tentar de novo."
                        else "O Android não instalou a atualização: ${mensagem ?: "erro $status"}.",
                        l,
                    ),
                )
            }
        }
    }

    /** O texto que a tela mostra (e o leitor de telas fala) para cada estado. */
    fun descrever(e: Estado, instalada: String): String = when (e) {
        is Estado.Ocioso -> "Versão instalada: $instalada."
        is Estado.Verificando -> "Verificando as atualizações..."
        is Estado.Atualizado -> "Você está com a versão mais nova ($instalada)."
        is Estado.Disponivel -> {
            val t = Atualizacao.tamanhoLegivel(e.lancamento.tamanho)
            "Versão ${e.lancamento.versao} disponível" + (if (t.isNotEmpty()) " ($t)" else "") + ". Você está com a $instalada."
        }
        is Estado.Baixando -> "Baixando a versão ${e.lancamento.versao}: ${e.andamento}."
        is Estado.PrecisaPermissao -> "Permita que o app instale outros apps, volte e toque em Baixar e instalar de novo."
        is Estado.Instalando -> "Pronto. Confirme a instalação na tela do Android."
        is Estado.Erro -> e.mensagem
    }
}
