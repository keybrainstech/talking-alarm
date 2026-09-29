package com.example.talkingalarm

import android.content.Context
import android.media.AudioAttributes
import android.speech.tts.TextToSpeech
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Shared preview engine used by the editor and the settings screen, so you can hear a
 * voice before committing to it. The alarms themselves each build their own engine.
 */
object Speaker {

    private val _voices = MutableStateFlow<List<VoiceOption>>(emptyList())
    val voices: StateFlow<List<VoiceOption>> = _voices.asStateFlow()

    private var tts: TextToSpeech? = null
    private var ready = false
    private var pending: (() -> Unit)? = null

    /** Safe to call repeatedly; sets up the engine the first time. */
    fun warmUp(context: Context) {
        if (tts != null) return
        tts = TextToSpeech(context.applicationContext) { status ->
            if (status != TextToSpeech.SUCCESS) return@TextToSpeech
            tts?.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            ready = true
            _voices.value = tts?.listUsableVoices() ?: emptyList()
            pending?.invoke()
            pending = null
        }
    }

    /** Speaks using whatever voice is saved in settings. */
    fun preview(context: Context, text: String) {
        preview(context, text, VoicePrefs.load(context))
    }

    /** Speaks using the given settings, without saving them. */
    fun preview(context: Context, text: String, settings: VoiceSettings) {
        val sentence = if (text.isBlank()) "This is how your alarm will sound" else text
        val action = {
            tts?.applyVoiceSettings(settings)
            tts?.speak(sentence, TextToSpeech.QUEUE_FLUSH, null, "preview")
            Unit
        }
        if (ready) action() else {
            pending = action
            warmUp(context)
        }
    }

    fun stop() {
        runCatching { tts?.stop() }
    }

    fun release() {
        runCatching {
            tts?.stop()
            tts?.shutdown()
        }
        tts = null
        ready = false
    }
}
