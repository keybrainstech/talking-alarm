package com.example.talkingalarm

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private const val SAMPLE = "This is how your alarms will sound."

/** Voice picker and speech tuning, shared by alarms and the interval timer. */
@Composable
fun SettingsScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val voices by Speaker.voices.collectAsState()
    var settings by remember { mutableStateOf(VoicePrefs.load(context)) }

    LaunchedEffect(Unit) { Speaker.warmUp(context) }

    fun update(new: VoiceSettings) {
        settings = new
        VoicePrefs.save(context, new)
    }

    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text("Voice", fontWeight = FontWeight.SemiBold)
        Text(
            "These are the voices installed on your phone for your language. Tap one to use it, or the ▶ to hear it.",
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
        )

        if (voices.isEmpty()) {
            Text(
                "Still loading the list, or your phone only has one voice installed. You can add more below.",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        VoiceRow(
            label = "Default voice",
            selected = settings.voiceName.isBlank(),
            onSelect = { update(settings.copy(voiceName = "")) },
            onPlay = { Speaker.preview(context, SAMPLE, settings.copy(voiceName = "")) }
        )

        voices.forEach { option ->
            VoiceRow(
                label = option.label,
                selected = settings.voiceName == option.name,
                onSelect = { update(settings.copy(voiceName = option.name)) },
                onPlay = { Speaker.preview(context, SAMPLE, settings.copy(voiceName = option.name)) }
            )
        }

        Spacer(Modifier.height(24.dp))
        Text("Speed", fontWeight = FontWeight.SemiBold)
        Text(
            speedLabel(settings.rate),
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Slider(
            value = settings.rate,
            onValueChange = { settings = settings.copy(rate = it) },
            onValueChangeFinished = { update(settings) },
            valueRange = 0.5f..1.6f
        )

        Spacer(Modifier.height(12.dp))
        Text("Pitch", fontWeight = FontWeight.SemiBold)
        Text(
            pitchLabel(settings.pitch),
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Slider(
            value = settings.pitch,
            onValueChange = { settings = settings.copy(pitch = it) },
            onValueChangeFinished = { update(settings) },
            valueRange = 0.6f..1.5f
        )

        Spacer(Modifier.height(16.dp))
        OutlinedButton(onClick = { Speaker.preview(context, SAMPLE, settings) }) {
            Icon(Icons.Default.PlayArrow, contentDescription = null)
            Text("  Hear the result")
        }

        Spacer(Modifier.height(28.dp))
        Text("Want more voices?", fontWeight = FontWeight.SemiBold)
        Text(
            "Voices come from your phone's speech engine, not from this app. Open the system settings below, pick your engine, and install more voice data or a different language.",
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp, bottom = 10.dp)
        )
        OutlinedButton(onClick = { openTtsSettings(context) }) {
            Text("Open speech settings")
        }

        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun VoiceRow(
    label: String,
    selected: Boolean,
    onSelect: () -> Unit,
    onPlay: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
            .clickable { onSelect() },
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            Modifier.padding(start = 16.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, modifier = Modifier.weight(1f), fontSize = 15.sp)
            if (selected) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = "In use",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            IconButton(onClick = onPlay) {
                Icon(
                    Icons.Default.PlayArrow,
                    contentDescription = "Hear this voice",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

private fun speedLabel(rate: Float): String = when {
    rate < 0.7f -> "Slow"
    rate < 0.9f -> "Relaxed"
    rate < 1.15f -> "Normal"
    rate < 1.4f -> "Brisk"
    else -> "Fast"
}

private fun pitchLabel(pitch: Float): String = when {
    pitch < 0.8f -> "Deep"
    pitch < 1.1f -> "Normal"
    pitch < 1.3f -> "Bright"
    else -> "High"
}

private fun openTtsSettings(context: android.content.Context) {
    runCatching {
        context.startActivity(
            Intent("com.android.settings.TTS_SETTINGS")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }.onFailure {
        runCatching {
            context.startActivity(
                Intent(android.provider.Settings.ACTION_SETTINGS)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }
}
