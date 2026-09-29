package com.example.talkingalarm

import android.app.Activity
import android.view.WindowManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat

/**
 * Interval timer tab: "speak this every 20 seconds" for workouts, study blocks,
 * physiotherapy reps, anything on a repeating clock.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun IntervalScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val state by IntervalService.state.collectAsState()

    // Don't let the screen sleep mid-session.
    DisposableEffect(state.running) {
        val window = (context as? Activity)?.window
        if (state.running) window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose { window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }

    if (state.running) {
        RunningSession(state, modifier)
    } else {
        SetupForm(modifier)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SetupForm(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val saved = remember { IntervalStore.load(context) }
    var minutes by remember { mutableStateOf((saved.safeIntervalSeconds / 60).toString()) }
    var seconds by remember { mutableStateOf((saved.safeIntervalSeconds % 60).toString()) }
    var text by remember { mutableStateOf(saved.text) }
    var rounds by remember { mutableStateOf(if (saved.rounds > 0) saved.rounds.toString() else "") }
    var announce by remember { mutableStateOf(saved.announceRound) }

    val totalSeconds = ((minutes.toIntOrNull() ?: 0) * 60 + (seconds.toIntOrNull() ?: 0))

    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text(
            "Speaks your text on a loop at a fixed gap. Good for sets, reps and rest timers.",
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(18.dp))
        Text("Gap between each one", fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(10.dp))

        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(10, 15, 20, 30, 45, 60, 90, 120).forEach { preset ->
                FilterChip(
                    selected = totalSeconds == preset,
                    onClick = {
                        minutes = (preset / 60).toString()
                        seconds = (preset % 60).toString()
                    },
                    label = { Text(if (preset < 60) "${preset}s" else "${preset / 60}m") }
                )
            }
        }

        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = minutes,
                onValueChange = { minutes = it.filter { c -> c.isDigit() }.take(3) },
                label = { Text("Minutes") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(12.dp))
            OutlinedTextField(
                value = seconds,
                onValueChange = { seconds = it.filter { c -> c.isDigit() }.take(2) },
                label = { Text("Seconds") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f)
            )
        }
        if (totalSeconds in 1..2) {
            Text(
                "Minimum gap is 3 seconds, otherwise it can't finish speaking.",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 6.dp)
            )
        }

        Spacer(Modifier.height(18.dp))
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            label = { Text("What should it say each time?") },
            placeholder = { Text("Switch sides") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 2
        )
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = { Speaker.preview(context, text) }) { Text("Hear it") }

        Spacer(Modifier.height(18.dp))
        OutlinedTextField(
            value = rounds,
            onValueChange = { rounds = it.filter { c -> c.isDigit() }.take(3) },
            label = { Text("Stop after how many rounds?") },
            placeholder = { Text("Leave empty to keep going") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Count the rounds out loud")
                Text(
                    "Says \"Round 4\" before your text",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(checked = announce, onCheckedChange = { announce = it })
        }

        Spacer(Modifier.height(24.dp))
        Button(
            onClick = {
                val config = IntervalConfig(
                    intervalSeconds = totalSeconds,
                    text = text.trim(),
                    rounds = rounds.toIntOrNull() ?: 0,
                    announceRound = announce
                )
                IntervalStore.save(context, config)
                ContextCompat.startForegroundService(
                    context, IntervalService.startIntent(context, config)
                )
            },
            enabled = totalSeconds >= 3,
            modifier = Modifier
                .fillMaxWidth()
                .height(58.dp),
            shape = RoundedCornerShape(18.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            )
        ) { Text("Start", fontSize = 18.sp) }

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun RunningSession(state: IntervalState, modifier: Modifier = Modifier) {
    val context = LocalContext.current

    fun send(action: String) {
        context.startService(
            android.content.Intent(context, IntervalService::class.java).setAction(action)
        )
    }

    Column(
        modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            if (state.paused) "Paused" else "Next in",
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            "${state.secondsLeft}",
            fontSize = 96.sp,
            fontWeight = FontWeight.Light,
            color = MaterialTheme.colorScheme.primary
        )

        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(
                Modifier.padding(horizontal = 24.dp, vertical = 14.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "Round ${state.round}" +
                        if (state.config.rounds > 0) " of ${state.config.rounds}" else "",
                    fontSize = 20.sp
                )
                Text(
                    "every ${state.config.intervalText()}",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (state.config.text.isNotBlank()) {
            Spacer(Modifier.height(20.dp))
            Text(
                state.config.text,
                fontSize = 18.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        Spacer(Modifier.height(40.dp))
        Row {
            OutlinedButton(
                onClick = {
                    send(
                        if (state.paused) IntervalService.ACTION_RESUME
                        else IntervalService.ACTION_PAUSE
                    )
                },
                modifier = Modifier.height(52.dp)
            ) { Text(if (state.paused) "Resume" else "Pause") }

            Spacer(Modifier.width(12.dp))

            Button(
                onClick = { send(IntervalService.ACTION_STOP) },
                modifier = Modifier.height(52.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) { Text("Stop") }
        }
    }
}
