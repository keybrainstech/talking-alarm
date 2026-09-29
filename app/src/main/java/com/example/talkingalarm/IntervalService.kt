package com.example.talkingalarm

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.speech.tts.TextToSpeech
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import kotlinx.coroutines.flow.MutableStateFlow
import java.util.Locale
import kotlin.math.ceil

/**
 * Repeating interval timer: beeps and speaks every N seconds until you stop it.
 * Runs as a foreground service so it keeps going with the screen off.
 */
class IntervalService : Service(), TextToSpeech.OnInitListener {

    companion object {
        val state = MutableStateFlow(IntervalState())

        const val ACTION_START = "com.example.talkingalarm.interval.START"
        const val ACTION_STOP = "com.example.talkingalarm.interval.STOP"
        const val ACTION_PAUSE = "com.example.talkingalarm.interval.PAUSE"
        const val ACTION_RESUME = "com.example.talkingalarm.interval.RESUME"

        private const val EXTRA_SECONDS = "seconds"
        private const val EXTRA_TEXT = "text"
        private const val EXTRA_ROUNDS = "rounds"
        private const val EXTRA_ANNOUNCE = "announce"

        private const val CHANNEL_ID = "interval_timer"
        private const val NOTIFICATION_ID = 7302
        private const val MAX_SESSION_MS = 6 * 60 * 60 * 1000L

        fun startIntent(context: Context, config: IntervalConfig): Intent =
            Intent(context, IntervalService::class.java)
                .setAction(ACTION_START)
                .putExtra(EXTRA_SECONDS, config.safeIntervalSeconds)
                .putExtra(EXTRA_TEXT, config.text)
                .putExtra(EXTRA_ROUNDS, config.rounds)
                .putExtra(EXTRA_ANNOUNCE, config.announceRound)
    }

    private val handler = Handler(Looper.getMainLooper())
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var tone: ToneGenerator? = null
    private var wakeLock: PowerManager.WakeLock? = null

    private var config = IntervalConfig()
    private var round = 0
    private var nextFireAt = 0L
    private var pausedRemainingMs = 0L

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        tts = TextToSpeech(this, this)
        runCatching { tone = ToneGenerator(AudioManager.STREAM_ALARM, 90) }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                finish(speakDone = false)
                return START_NOT_STICKY
            }
            ACTION_PAUSE -> {
                pause()
                return START_STICKY
            }
            ACTION_RESUME -> {
                resume()
                return START_STICKY
            }
            ACTION_START -> {
                config = IntervalConfig(
                    intervalSeconds = intent.getIntExtra(EXTRA_SECONDS, 20),
                    text = intent.getStringExtra(EXTRA_TEXT) ?: "",
                    rounds = intent.getIntExtra(EXTRA_ROUNDS, 0),
                    announceRound = intent.getBooleanExtra(EXTRA_ANNOUNCE, true)
                )
                begin()
                return START_STICKY
            }
            else -> {
                stopSelf()
                return START_NOT_STICKY
            }
        }
    }

    private fun begin() {
        round = 0
        nextFireAt = SystemClock.elapsedRealtime() + config.safeIntervalSeconds * 1000L
        state.value = IntervalState(
            running = true,
            paused = false,
            round = 0,
            secondsLeft = config.safeIntervalSeconds,
            config = config
        )

        createChannel()
        ServiceCompat.startForeground(
            this, NOTIFICATION_ID, buildNotification(),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK else 0
        )

        wakeLock = getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "TalkingAlarm:interval")
            .also { it.acquire(MAX_SESSION_MS) }

        speak("Starting")
        handler.removeCallbacks(ticker)
        handler.postDelayed(ticker, 200)
    }

    private val ticker = object : Runnable {
        override fun run() {
            val snapshot = state.value
            if (!snapshot.running || snapshot.paused) return

            val now = SystemClock.elapsedRealtime()
            if (now >= nextFireAt) {
                round++
                fireRound()

                if (config.rounds > 0 && round >= config.rounds) {
                    finish(speakDone = true)
                    return
                }
                // Anchor on the previous target so the beeps don't drift.
                nextFireAt += config.safeIntervalSeconds * 1000L
                if (nextFireAt <= now) nextFireAt = now + config.safeIntervalSeconds * 1000L
                updateNotification()
            }

            val left = ceil((nextFireAt - SystemClock.elapsedRealtime()) / 1000.0).toInt()
            state.value = state.value.copy(round = round, secondsLeft = left.coerceAtLeast(0))
            handler.postDelayed(this, 200)
        }
    }

    private fun fireRound() {
        runCatching { tone?.startTone(ToneGenerator.TONE_PROP_BEEP, 200) }
        val parts = mutableListOf<String>()
        if (config.announceRound) parts.add("Round $round")
        if (config.text.isNotBlank()) parts.add(config.text)
        if (parts.isNotEmpty()) {
            handler.postDelayed({ speak(parts.joinToString(". ")) }, 250)
        }
    }

    private fun pause() {
        if (!state.value.running || state.value.paused) return
        pausedRemainingMs = (nextFireAt - SystemClock.elapsedRealtime()).coerceAtLeast(0)
        handler.removeCallbacks(ticker)
        state.value = state.value.copy(paused = true)
        updateNotification()
    }

    private fun resume() {
        if (!state.value.running || !state.value.paused) return
        nextFireAt = SystemClock.elapsedRealtime() + pausedRemainingMs
        state.value = state.value.copy(paused = false)
        handler.postDelayed(ticker, 200)
        updateNotification()
    }

    private fun finish(speakDone: Boolean) {
        handler.removeCallbacks(ticker)
        if (speakDone) speak("Session complete. Well done.")
        state.value = IntervalState(running = false, config = config)
        runCatching { if (wakeLock?.isHeld == true) wakeLock?.release() }
        wakeLock = null
        // Give the closing line time to play before the engine shuts down.
        handler.postDelayed({
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            stopSelf()
        }, if (speakDone) 2600L else 0L)
    }

    // ---------- speech ----------

    override fun onInit(status: Int) {
        if (status != TextToSpeech.SUCCESS) return
        val engine = tts ?: return
        engine.setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
        )
        val result = engine.setLanguage(Locale.getDefault())
        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
            engine.setLanguage(Locale.US)
        }
        engine.setSpeechRate(1.0f)
        ttsReady = true
    }

    private fun speak(sentence: String) {
        if (!ttsReady) return
        val params = Bundle().apply {
            putInt(TextToSpeech.Engine.KEY_PARAM_STREAM, AudioManager.STREAM_ALARM)
        }
        tts?.speak(sentence, TextToSpeech.QUEUE_FLUSH, params, "interval")
    }

    // ---------- notification ----------

    private fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID, "Interval timer", NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Shown while an interval session is running"
            setSound(null, null)
            enableVibration(false)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun action(name: String, label: String): NotificationCompat.Action {
        val pi = PendingIntent.getService(
            this, name.hashCode(),
            Intent(this, IntervalService::class.java).setAction(name),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Action(0, label, pi)
    }

    private fun buildNotification(): Notification {
        val open = PendingIntent.getActivity(
            this, 2, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val paused = state.value.paused
        val target = if (config.rounds > 0) " of ${config.rounds}" else ""
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_alarm)
            .setContentTitle(if (paused) "Paused" else "Every ${config.intervalText()}")
            .setContentText("Round $round$target")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(open)
            .addAction(
                if (paused) action(ACTION_RESUME, "Resume") else action(ACTION_PAUSE, "Pause")
            )
            .addAction(action(ACTION_STOP, "Stop"))
            .build()
    }

    private fun updateNotification() {
        runCatching {
            getSystemService(NotificationManager::class.java)
                .notify(NOTIFICATION_ID, buildNotification())
        }
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        runCatching {
            tts?.stop()
            tts?.shutdown()
        }
        tts = null
        runCatching { tone?.release() }
        tone = null
        runCatching { if (wakeLock?.isHeld == true) wakeLock?.release() }
        state.value = IntervalState(running = false, config = config)
        super.onDestroy()
    }
}
