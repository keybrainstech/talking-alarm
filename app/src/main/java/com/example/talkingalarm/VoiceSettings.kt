package com.example.talkingalarm

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import java.util.Locale

/** Which voice speaks, and how fast and how high. Applies to alarms and the timer. */
data class VoiceSettings(
    val voiceName: String = "",   // empty = whatever the engine picks by default
    val rate: Float = 0.95f,
    val pitch: Float = 1.0f
)

object VoicePrefs {
    private const val PREFS = "talking_alarm_prefs"

    fun load(context: Context): VoiceSettings {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return VoiceSettings(
            voiceName = p.getString("voice_name", "") ?: "",
            rate = p.getFloat("voice_rate", 0.95f),
            pitch = p.getFloat("voice_pitch", 1.0f)
        )
    }

    fun save(context: Context, settings: VoiceSettings) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("voice_name", settings.voiceName)
            .putFloat("voice_rate", settings.rate)
            .putFloat("voice_pitch", settings.pitch)
            .apply()
    }
}

/** A voice as shown in the settings list. */
data class VoiceOption(val name: String, val label: String)

/** Applies the saved voice to a TTS engine that has finished initialising. */
fun TextToSpeech.applyVoiceSettings(settings: VoiceSettings) {
    val result = setLanguage(Locale.getDefault())
    if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
        setLanguage(Locale.US)
    }
    if (settings.voiceName.isNotBlank()) {
        runCatching {
            voices?.firstOrNull { it.name == settings.voiceName }?.let { voice = it }
        }
    }
    setSpeechRate(settings.rate.coerceIn(0.4f, 2.0f))
    setPitch(settings.pitch.coerceIn(0.5f, 2.0f))
}

/** Voices installed on this phone for the current language, nicely labelled. */
fun TextToSpeech.listUsableVoices(): List<VoiceOption> {
    val language = Locale.getDefault().language
    val available = runCatching { voices?.toList() ?: emptyList() }.getOrDefault(emptyList())

    val matching = available
        .filter { !it.isNetworkConnectionRequired }
        .filter { it.locale.language == language }
        .ifEmpty { available.filter { !it.isNetworkConnectionRequired } }
        .sortedWith(compareByDescending<Voice> { it.quality }.thenBy { it.name })
        .take(8)

    return matching.mapIndexed { index, voice ->
        val hints = mutableListOf<String>()
        val lower = voice.name.lowercase()
        if (lower.contains("female")) hints.add("female")
        if (lower.contains("male") && !lower.contains("female")) hints.add("male")
        if (voice.quality >= Voice.QUALITY_VERY_HIGH) hints.add("high quality")
        hints.add(voice.locale.toLanguageTag())

        VoiceOption(
            name = voice.name,
            label = "Voice ${index + 1} (" + hints.joinToString(", ") + ")"
        )
    }
}
