package com.mhs.vozescoisasdecego

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.speech.tts.TextToSpeech

/** O Android pergunta se a voz esta instalada. Ela vem dentro do app, entao sempre esta. */
class CheckVoiceDataActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val voices = arrayListOf("por-BRA", "por-PRT", "eng-USA", "spa-ESP", "fra-FRA", "deu-DEU")
        val data = Intent().apply {
            putStringArrayListExtra(TextToSpeech.Engine.EXTRA_AVAILABLE_VOICES, voices)
            putStringArrayListExtra(TextToSpeech.Engine.EXTRA_UNAVAILABLE_VOICES, arrayListOf())
        }
        setResult(TextToSpeech.Engine.CHECK_VOICE_DATA_PASS, data)
        finish()
    }
}

/** Frase de exemplo que o Android fala ao testar a voz. */
class GetSampleTextActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val lang = intent?.getStringExtra("language") ?: "por"
        val frase = Vozes.apresentacao(lang, Prefs(this).voz)
        setResult(TextToSpeech.LANG_AVAILABLE, Intent().putExtra("sampleText", frase))
        finish()
    }
}

/** Nada para instalar: a voz ja vem no app. */
class InstallVoiceDataActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        finish()
    }
}
