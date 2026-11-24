package com.mhs.vozescoisasdecego

import android.app.Activity
import android.app.AlertDialog
import android.content.DialogInterface
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import androidx.core.view.ViewCompat
import java.util.Locale

/**
 * Tela "Dicionario de pronuncias": botao para adicionar palavra e a lista de todas as palavras.
 * Tocar numa palavra abre as opcoes Editar e Remover. As mudancas valem na proxima fala.
 */
class DicionarioActivity : Activity() {
    private lateinit var prefs: Prefs
    private lateinit var vazio: TextView
    private lateinit var adaptador: ArrayAdapter<String>
    private var entradas: List<EntradaDicionario> = emptyList()

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        title = "Dicionário de pronúncias"

        val coluna = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(8))
        }
        coluna.addView(TextView(this).apply {
            text = "Dicionário de pronúncias"
            textSize = 24f
            ViewCompat.setAccessibilityHeading(this, true)
        })
        coluna.addView(TextView(this).apply {
            text = "Escreva a palavra do jeito certo e, ao lado, como a voz deve falar. " +
                "Vale para a palavra inteira, sem diferença de maiúsculas, e a mudança vale na próxima fala."
            textSize = 14f
            setPadding(0, dp(6), 0, dp(6))
        })
        coluna.addView(Button(this).apply {
            text = "Adicionar palavra"
            isAllCaps = false
            setOnClickListener { dialogoPalavra(null) }
        })
        vazio = TextView(this).apply {
            text = "Nenhuma palavra adicionada ainda."
            textSize = 16f
            setPadding(0, dp(12), 0, dp(12))
        }
        coluna.addView(vazio)
        adaptador = ArrayAdapter(this, android.R.layout.simple_list_item_1, mutableListOf())
        coluna.addView(ListView(this).apply {
            adapter = adaptador
            setOnItemClickListener { _, _, posicao, _ -> opcoes(entradas[posicao]) }
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(coluna)
    }

    override fun onResume() {
        super.onResume()
        atualizar()
    }

    private fun atualizar() {
        entradas = DicionarioUsuario.listar(prefs.dicionario)
            .sortedBy { it.palavra.lowercase(Locale("pt", "BR")) }
        adaptador.clear()
        adaptador.addAll(entradas.map { "${it.palavra}, fala como: ${it.pronuncia}" })
        vazio.visibility = if (entradas.isEmpty()) View.VISIBLE else View.GONE
    }

    /** Ao tocar numa palavra da lista: Editar ou Remover. */
    private fun opcoes(e: EntradaDicionario) {
        AlertDialog.Builder(this)
            .setTitle(e.palavra)
            .setItems(arrayOf("Editar", "Remover")) { _, qual ->
                if (qual == 0) dialogoPalavra(e) else remover(e)
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun remover(e: EntradaDicionario) {
        // A lista mostrada pode estar atras do texto salvo; confere que a linha ainda e a mesma palavra.
        val atual = DicionarioUsuario.listar(prefs.dicionario).firstOrNull { it.linha == e.linha && it.palavra == e.palavra }
        if (atual != null) prefs.dicionario = DicionarioUsuario.remover(prefs.dicionario, atual.linha)
        atualizar()
        window.decorView.announceForAccessibility("Palavra ${e.palavra} removida.")
    }

    /** Adicionar (editando == null) ou editar uma palavra: dois campos e os botoes Cancelar e OK. */
    private fun dialogoPalavra(editando: EntradaDicionario?) {
        val coluna = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(8), dp(20), 0)
        }

        fun campo(rotulo: String, valor: String): EditText {
            coluna.addView(TextView(this).apply {
                text = rotulo
                textSize = 14f
                setPadding(0, dp(8), 0, 0)
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            })
            val c = EditText(this).apply {
                setText(valor)
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
                setSingleLine()
                contentDescription = rotulo
            }
            coluna.addView(c)
            return c
        }

        val campoPalavra = campo("Palavra correta", editando?.palavra ?: "")
        val campoPronuncia = campo("Como a voz deve falar", editando?.pronuncia ?: "")

        val dialogo = AlertDialog.Builder(this)
            .setTitle(if (editando == null) "Adicionar palavra" else "Editar palavra")
            .setView(coluna)
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("OK", null)
            .create()
        dialogo.setOnShowListener {
            // OK so fecha o dialogo se os dois campos estiverem certos (senao mostra o motivo e fica aberto).
            dialogo.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener {
                val palavra = campoPalavra.text.toString()
                val pronuncia = campoPronuncia.text.toString()
                val erro = DicionarioUsuario.validar(palavra, pronuncia)
                if (erro != null) {
                    val alvo = if (palavra.isBlank() || palavra.contains('=') || palavra.trim().startsWith("#")) campoPalavra else campoPronuncia
                    alvo.error = erro
                    alvo.requestFocus()
                    alvo.announceForAccessibility(erro)
                    return@setOnClickListener
                }
                val texto = prefs.dicionario
                val salvo = if (editando == null) {
                    DicionarioUsuario.adicionar(texto, palavra, pronuncia)
                } else {
                    // a lista pode ter mudado desde que o dialogo abriu; edita a linha so se ainda e a mesma palavra
                    val atual = DicionarioUsuario.listar(texto).firstOrNull { it.linha == editando.linha && it.palavra == editando.palavra }
                    if (atual != null) DicionarioUsuario.editar(texto, atual.linha, palavra, pronuncia)
                    else DicionarioUsuario.adicionar(texto, palavra, pronuncia)
                }
                prefs.dicionario = salvo
                dialogo.dismiss()
                atualizar()
                window.decorView.announceForAccessibility(
                    if (editando == null) "Palavra ${palavra.trim()} adicionada." else "Palavra ${palavra.trim()} salva.",
                )
            }
            campoPalavra.requestFocus()
        }
        dialogo.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
        dialogo.show()
    }
}
