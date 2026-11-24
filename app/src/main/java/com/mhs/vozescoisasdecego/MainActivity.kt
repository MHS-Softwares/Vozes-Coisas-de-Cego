package com.mhs.vozescoisasdecego

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.speech.tts.TextToSpeech
import android.text.InputType
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Spinner
import android.widget.TextView
import androidx.core.view.ViewCompat
import java.util.Locale

/** Tela de ajustes: escolha da voz, velocidade, tom, volume, modo de fala e teste da voz. */
class MainActivity : Activity() {
    companion object {
        /** A notificacao de "versao nova" abre a tela com isto, para ja verificar de novo. */
        const val EXTRA_ATUALIZAR = "atualizar"
    }

    private lateinit var prefs: Prefs
    private lateinit var coluna: LinearLayout
    private lateinit var status: TextView
    private lateinit var campoTeste: EditText
    private lateinit var diagnostico: TextView
    private lateinit var estadoPreparo: TextView
    private lateinit var resumoDicionario: TextView
    private lateinit var estadoAtualizacao: TextView
    private lateinit var notasAtualizacao: TextView
    private lateinit var botaoBaixar: Button
    private var pararDeObservar: (() -> Unit)? = null
    private var tts: TextToSpeech? = null
    private var ttsPronto = false
    private var falarAoIniciar = false
    private var apresentacaoAtual = ""

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        title = "Vozes Coisas de Cego"

        coluna = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(24))
        }
        setContentView(ScrollView(this).apply {
            isFillViewport = true
            addView(coluna, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        })

        texto("Vozes Coisas de Cego", 24f, titulo = true)
        status = texto("Preparando a voz...", 16f)
        texto(
            "Para usar como voz do celular: Ajustes do Android, Texto para fala, Mecanismo preferido, " +
                "Vozes Coisas de Cego. Os ajustes abaixo valem junto com os do Android.",
            14f,
        )

        texto("Voz", 16f, titulo = true)
        escolha(Vozes.LISTA, Vozes.LISTA.indexOf(prefs.voz), "Voz") { pos -> trocarVoz(Vozes.LISTA[pos]) }
        texto(
            "A troca vale na hora, para o TalkBack e para os outros leitores de tela que estiverem usando esta voz. " +
                "Não precisa reiniciar nada.",
            13f,
        )

        controle("Velocidade", 0.25f, 3.0f, 0.05f, prefs.velocidade, { "%.2fx".format(Locale("pt", "BR"), it) }) { prefs.velocidade = it }
        controle("Tom", 0.5f, 2.0f, 0.05f, prefs.tom, { "%.2fx".format(Locale("pt", "BR"), it) }) { prefs.tom = it }
        controle("Volume", 0.0f, 2.0f, 0.05f, prefs.volume, { "${(it * 100).toInt()} por cento" }) { prefs.volume = it }

        interruptor("Seguir a velocidade do Android e do TalkBack", prefs.seguirVelocidadeAndroid) { prefs.seguirVelocidadeAndroid = it }
        interruptor("Seguir o tom do Android e do TalkBack", prefs.seguirTomAndroid) { prefs.seguirTomAndroid = it }
        texto(
            "Ligado: o ajuste de velocidade e tom do Android/TalkBack multiplica o do app (100 é normal). " +
                "Desligado: só valem os controles acima, e a velocidade do TalkBack deixa de mudar a voz.",
            13f,
        )
        interruptor("Cortar o silêncio do começo da fala (responde mais rápido)", prefs.cortarInicio) { prefs.cortarInicio = it }
        diagnostico = texto("", 13f)

        texto("Emojis", 16f, titulo = true)
        interruptor("Ler os emojis pelo nome", prefs.lerEmojis) { prefs.lerEmojis = it }
        interruptor("Emojis repetidos: dizer \"nome, 3 vezes\"", prefs.emojisRepetidos) { prefs.emojisRepetidos = it }
        texto(
            "Os nomes vêm das anotações em português do Unicode (CLDR). Se um emoji não tiver nome, é pulado.",
            13f,
        )

        texto("Pronúncia", 16f, titulo = true)
        interruptor("Corrigir pronúncia (letras soltas, palavras com gem e gens, RHVoice)", prefs.corrigirPronuncia) { prefs.corrigirPronuncia = it }
        resumoDicionario = texto("", 13f)
        botao("Dicionário de pronúncias") { startActivity(Intent(this, DicionarioActivity::class.java)) }
        interruptor("Preparar frases comuns em segundo plano (responde mais rápido)", prefs.preGerar) { prefs.preGerar = it }
        texto(
            "Com o celular parado, o app vai gerando frases que o leitor de telas fala toda hora, como Botão e Voltar, " +
                "e guarda o áudio. Frases repetidas tocam na hora, sem esperar o motor.",
            13f,
        )

        estadoPreparo = texto("", 13f)
        botao("Preparar as frases comuns agora") { prepararAgora() }

        texto("Modo de fala", 16f, titulo = true)
        escolha(
            listOf("Início rápido", "Automático (recomendado)", "Sem engasgos: espera a frase pronta"),
            prefs.modo,
        ) { prefs.modo = it }
        texto(
            "Início rápido começa a falar na hora, mas pode engasgar se o celular for lento. " +
                "Automático espera só o necessário. Sem engasgos espera a frase inteira.",
            13f,
        )

        texto("Reserva de áudio no começo da fala", 16f, titulo = true)
        escolha(
            listOf("Nenhuma", "Pequena (100 ms)", "Média (200 ms, recomendada)", "Grande (350 ms)"),
            prefs.reserva, "Reserva de áudio",
        ) { prefs.reserva = it }
        texto(
            "Guarda um pouco de áudio antes de começar a falar, para o som não dar pausinhas no começo da frase, " +
                "depois de uma vírgula ou quando você varre a tela. Só vale no modo Automático. " +
                "Mais reserva dá menos pausas, mas a voz demora um pouquinho mais para começar.",
            13f,
        )

        texto("Núcleos de processamento", 16f, titulo = true)
        escolha(listOf("Automático", "2", "3", "4", "5", "6"), if (prefs.nucleos == 0) 0 else prefs.nucleos - 1) {
            prefs.nucleos = if (it == 0) 0 else it + 1
        }
        texto("Só vale depois de fechar o app e o serviço de voz (Forçar parada) e usar de novo.", 13f)

        texto("Teste", 16f, titulo = true)
        apresentacaoAtual = Vozes.apresentacao("por", prefs.voz)
        campoTeste = EditText(this).apply {
            setText(apresentacaoAtual)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            contentDescription = "Texto para o teste"
            minLines = 2
        }
        coluna.addView(campoTeste)
        botao("Falar teste") { falarTeste() }
        botao("Parar") { tts?.stop() }
        botao("Restaurar ajustes padrão") {
            prefs.restaurar()
            recreate()
        }
        botao("Abrir ajustes de texto para fala do Android") {
            val intents = listOf(Intent("com.android.settings.TTS_SETTINGS"), Intent(Settings.ACTION_SETTINGS))
            for (i in intents) {
                try {
                    startActivity(i)
                    break
                } catch (_: Exception) {
                }
            }
        }

        texto("Atualizações", 16f, titulo = true)
        estadoAtualizacao = texto("", 14f).apply { accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE }
        notasAtualizacao = texto("", 13f)
        botao("Verificar atualizações") { Atualizador.verificar(this) }
        botaoBaixar = Button(this).apply {
            text = "Baixar e instalar"
            isAllCaps = false
            visibility = View.GONE
            setOnClickListener { Atualizador.baixarEInstalar(this@MainActivity) }
        }
        coluna.addView(botaoBaixar)
        interruptor("Procurar atualizações sozinho, uma vez por dia", prefs.verificarAtualizacoes) {
            prefs.verificarAtualizacoes = it
            if (it) pedirPermissaoDeAvisos()
        }
        texto(
            "As atualizações vêm da página de versões do projeto no GitHub. Só essa verificação usa a internet: " +
                "a voz continua funcionando 100% offline. O app baixa a versão nova, confere que é dele e abre a " +
                "instalação do Android, onde você confirma.",
            13f,
        )

        Thread {
            val msg = try {
                ModelStore.garantir(this)
                "Voz pronta."
            } catch (t: Throwable) {
                "Não consegui preparar a voz: ${t.message}"
            }
            runOnUiThread {
                status.text = msg
                status.announceForAccessibility(msg)
            }
        }.start()
    }

    override fun onResume() {
        super.onResume()
        atualizarDiagnostico()
        pararDeObservar = Atualizador.observar { e -> runOnUiThread { mostrarAtualizacao(e) } }
        if (intent?.getBooleanExtra(EXTRA_ATUALIZAR, false) == true) {
            intent.removeExtra(EXTRA_ATUALIZAR)
            Atualizador.verificar(this)
        } else {
            Atualizador.checagemAutomatica(this)
        }
        if (prefs.verificarAtualizacoes) pedirPermissaoDeAvisos()
    }

    override fun onPause() {
        pararDeObservar?.invoke()
        pararDeObservar = null
        super.onPause()
    }

    /** Mostra o estado da atualizacao; a area e uma "regiao viva", entao o leitor de telas fala as mudancas. */
    private fun mostrarAtualizacao(e: Atualizador.Estado) {
        estadoAtualizacao.text = Atualizador.descrever(e, Atualizador.versaoInstalada(this))
        val oferecida = Atualizador.oferecida()
        val ocupado = e is Atualizador.Estado.Verificando || e is Atualizador.Estado.Baixando || e is Atualizador.Estado.Instalando
        if (oferecida != null && !ocupado) {
            val t = Atualizacao.tamanhoLegivel(oferecida.tamanho)
            botaoBaixar.text = "Baixar e instalar a versão ${oferecida.versao}" + if (t.isNotEmpty()) " ($t)" else ""
            botaoBaixar.visibility = View.VISIBLE
        } else {
            botaoBaixar.visibility = View.GONE
        }
        notasAtualizacao.text = if (e is Atualizador.Estado.Disponivel && e.lancamento.notas.isNotEmpty()) "O que mudou: " + e.lancamento.notas else ""
    }

    /** Android 13 ou mais novo: sem esta permissao o aviso de versao nova nao aparece. Pede so uma vez. */
    private fun pedirPermissaoDeAvisos() {
        if (android.os.Build.VERSION.SDK_INT < 33 || prefs.pediuPermissaoAvisos) return
        if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED) return
        prefs.pediuPermissaoAvisos = true
        requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 7)
    }

    private fun atualizarDiagnostico() {
        val p = prefs.ultimoPedido
        val voz = prefs.voz
        val prontas = runCatching {
            val c = PhononEngine.get(this).cacheDa(voz)
            FrasesComuns.LISTA.count { c.contem(Dicionario.chave("pt", Dicionario.aplicar(it, prefs.dicionario, prefs.corrigirPronuncia))) }
        }.getOrDefault(0)
        val preparo = "Frases comuns prontas na voz $voz: $prontas de ${FrasesComuns.LISTA.size}."
        val n = DicionarioUsuario.listar(prefs.dicionario).size
        resumoDicionario.text = when (n) {
            0 -> "Nenhuma palavra no dicionário ainda."
            1 -> "1 palavra no dicionário."
            else -> "$n palavras no dicionário."
        }
        diagnostico.text = (if (p.isEmpty()) "Ainda não falei nada." else "Última fala: $p") + " " + preparo
    }

    override fun onDestroy() {
        tts?.shutdown()
        super.onDestroy()
    }

    /** O teste usa velocidade e tom "normais" do Android (100), sem depender dos ajustes do sistema. */
    private fun fixarNormal(t: TextToSpeech?) {
        t?.setSpeechRate(1.0f)
        t?.setPitch(1.0f)
    }

    private fun falarTeste() {
        val frase = campoTeste.text.toString().ifBlank { Vozes.apresentacao("por", prefs.voz) }
        val atual = tts
        if (atual == null) {
            falarAoIniciar = true
            tts = TextToSpeech(this, { estado ->
                ttsPronto = estado == TextToSpeech.SUCCESS
                if (ttsPronto) {
                    tts?.setLanguage(Locale("pt", "BR"))
                    fixarNormal(tts)
                    if (falarAoIniciar) tts?.speak(campoTeste.text.toString().ifBlank { frase }, TextToSpeech.QUEUE_FLUSH, null, "teste")
                } else {
                    runOnUiThread { status.text = "O Android não conseguiu iniciar a voz." }
                }
                falarAoIniciar = false
            }, packageName)
        } else if (ttsPronto) {
            atual.speak(frase, TextToSpeech.QUEUE_FLUSH, null, "teste")
        }
    }

    /** Gera as frases comuns da voz escolhida de uma vez (leva alguns minutos) e mostra o andamento. */
    private fun prepararAgora() {
        val engine = PhononEngine.get(this)
        if (engine.preparando) {
            estadoPreparo.text = "Já estou preparando as frases. Aguarde."
            return
        }
        val voz = prefs.voz
        estadoPreparo.text = "Preparando as frases comuns da voz $voz..."
        estadoPreparo.announceForAccessibility("Preparando as frases comuns da voz $voz. Pode levar alguns minutos.")
        engine.prepararFrasesAgora(voz, { prontas, total ->
            runOnUiThread { estadoPreparo.text = "Preparando as frases comuns da voz $voz: $prontas de $total." }
        }, { todas ->
            runOnUiThread {
                val msg = if (todas) "Pronto: as frases comuns da voz $voz estão preparadas."
                else "O preparo parou antes do fim. Toque no botão de novo para continuar."
                estadoPreparo.text = msg
                estadoPreparo.announceForAccessibility(msg)
                atualizarDiagnostico()
            }
        })
    }

    /** O usuario escolheu outra voz: grava, deixa o motor pronto e fala uma frase com a voz nova. */
    private fun trocarVoz(nome: String) {
        if (nome == prefs.voz) return
        prefs.voz = nome
        PhononEngine.get(this).aplicarVozAgora(nome)
        // Se o campo de teste ainda tem a frase de apresentacao da voz anterior, troca pela da nova.
        val nova = Vozes.apresentacao("por", nome)
        if (campoTeste.text.toString() == apresentacaoAtual) campoTeste.setText(nova)
        apresentacaoAtual = nova
        falarTeste()
    }

    // ---- pecas de tela ----

    private fun texto(t: String, tamanho: Float, titulo: Boolean = false): TextView {
        val v = TextView(this).apply {
            text = t
            textSize = tamanho
            setPadding(0, dp(if (titulo) 14 else 6), 0, dp(4))
        }
        if (titulo) ViewCompat.setAccessibilityHeading(v, true)
        coluna.addView(v)
        return v
    }

    private fun interruptor(rotulo: String, ligado: Boolean, aoMudar: (Boolean) -> Unit) {
        coluna.addView(android.widget.Switch(this).apply {
            text = rotulo
            isChecked = ligado
            setPadding(0, dp(8), 0, dp(8))
            setOnCheckedChangeListener { _, marcado -> aoMudar(marcado) }
        })
    }

    private fun botao(rotulo: String, acao: () -> Unit) {
        coluna.addView(Button(this).apply {
            text = rotulo
            isAllCaps = false
            setOnClickListener { acao() }
        })
    }

    private fun escolha(opcoes: List<String>, atual: Int, descricao: String? = null, aoMudar: (Int) -> Unit) {
        val s = Spinner(this)
        if (descricao != null) s.contentDescription = descricao
        s.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, opcoes)
        s.setSelection(atual.coerceIn(0, opcoes.size - 1))
        s.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            private var primeira = true
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                if (primeira) {
                    primeira = false
                    return
                }
                aoMudar(pos)
            }

            override fun onNothingSelected(p: AdapterView<*>?) {}
        }
        coluna.addView(s)
    }

    private fun controle(
        nome: String, minimo: Float, maximo: Float, passo: Float, inicial: Float,
        formato: (Float) -> String, aoMudar: (Float) -> Unit,
    ) {
        val rotulo = texto("$nome: ${formato(inicial)}", 16f, titulo = true)
        rotulo.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        val passos = Math.round((maximo - minimo) / passo)
        val barra = SeekBar(this).apply {
            max = passos
            progress = Math.round((inicial - minimo) / passo).coerceIn(0, passos)
            contentDescription = nome
        }
        ViewCompat.setStateDescription(barra, formato(inicial))
        barra.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar, progresso: Int, doUsuario: Boolean) {
                val valor = minimo + progresso * passo
                rotulo.text = "$nome: ${formato(valor)}"
                ViewCompat.setStateDescription(s, formato(valor))
                if (doUsuario) aoMudar(valor)
            }

            override fun onStartTrackingTouch(s: SeekBar) {}
            override fun onStopTrackingTouch(s: SeekBar) {}
        })
        coluna.addView(barra)
    }
}
