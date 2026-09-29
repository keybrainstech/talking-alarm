package com.example.talkingalarm

import android.content.Context

/** Settings for one interval-timer session. */
data class IntervalConfig(
    val intervalSeconds: Int = 20,
    val text: String = "",
    val rounds: Int = 0,          // 0 means keep going until stopped
    val announceRound: Boolean = true
) {
    val safeIntervalSeconds: Int get() = intervalSeconds.coerceAtLeast(3)

    fun intervalText(): String {
        val s = safeIntervalSeconds
        return when {
            s < 60 -> "${s}s"
            s % 60 == 0 -> "${s / 60}m"
            else -> "${s / 60}m ${s % 60}s"
        }
    }
}

/** Remembers the last session you set up, so you don't retype it every workout. */
object IntervalStore {

    private const val PREFS = "talking_alarm_prefs"

    fun load(context: Context): IntervalConfig {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return IntervalConfig(
            intervalSeconds = p.getInt("iv_seconds", 20),
            text = p.getString("iv_text", "") ?: "",
            rounds = p.getInt("iv_rounds", 0),
            announceRound = p.getBoolean("iv_announce", true)
        )
    }

    fun save(context: Context, config: IntervalConfig) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putInt("iv_seconds", config.intervalSeconds)
            .putString("iv_text", config.text)
            .putInt("iv_rounds", config.rounds)
            .putBoolean("iv_announce", config.announceRound)
            .apply()
    }
}

/** Live state of a running session, watched by the timer screen. */
data class IntervalState(
    val running: Boolean = false,
    val paused: Boolean = false,
    val round: Int = 0,
    val secondsLeft: Int = 0,
    val config: IntervalConfig = IntervalConfig()
)
