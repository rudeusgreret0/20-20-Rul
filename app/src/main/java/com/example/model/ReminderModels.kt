package com.example.model

/**
 * Strictly defined state machine states for the 20-20-20 Eye Care Reminder cycle.
 */
enum class ReminderState {
    DISABLED,
    COUNTING_20_MINUTES,
    ALARM_RINGING,
    COUNTING_REST,
    REST_COMPLETED,
    PAUSED
}

/**
 * Tracks which active countdown was running when the user paused the reminder.
 */
enum class PausedPhase {
    WORK_20_MINUTES,
    REST_COUNTDOWN
}

/**
 * Abstraction over time source for deterministic unit and Robolectric testing.
 */
interface EyeCareClock {
    fun nowMillis(): Long
}

object SystemEyeCareClock : EyeCareClock {
    override fun nowMillis(): Long = System.currentTimeMillis()
}

/**
 * Immutable snapshot of the persistent reminder cycle state and user settings.
 */
data class ReminderSnapshot(
    val state: ReminderState = ReminderState.DISABLED,
    val restDurationSeconds: Int = DEFAULT_REST_SECONDS,
    val soundEnabled: Boolean = true,
    val targetEpochMillis: Long = 0L,
    val totalPhaseDurationMillis: Long = TWENTY_MINUTES_MILLIS,
    val remainingMillisWhenPaused: Long = TWENTY_MINUTES_MILLIS,
    val pausedPhase: PausedPhase = PausedPhase.WORK_20_MINUTES,
    val lastCheckpointRemainingMillis: Long = TWENTY_MINUTES_MILLIS,
    val completedCyclesCount: Int = 0,
    val stateVersion: Long = 0L
) {
    val isEnabled: Boolean
        get() = state != ReminderState.DISABLED

    fun computeRemainingMillis(nowMillis: Long): Long {
        return when (state) {
            ReminderState.DISABLED -> TWENTY_MINUTES_MILLIS
            ReminderState.COUNTING_20_MINUTES,
            ReminderState.COUNTING_REST -> (targetEpochMillis - nowMillis).coerceAtLeast(0L)
            ReminderState.ALARM_RINGING -> 0L
            ReminderState.REST_COMPLETED -> 0L
            ReminderState.PAUSED -> remainingMillisWhenPaused.coerceAtLeast(0L)
        }
    }

    fun computeProgressFraction(nowMillis: Long): Float {
        return when (state) {
            ReminderState.DISABLED -> 0f
            ReminderState.COUNTING_20_MINUTES,
            ReminderState.COUNTING_REST -> {
                val total = totalPhaseDurationMillis.coerceAtLeast(1L).toFloat()
                val remaining = computeRemainingMillis(nowMillis).toFloat()
                ((total - remaining) / total).coerceIn(0f, 1f)
            }
            ReminderState.ALARM_RINGING -> 1f
            ReminderState.REST_COMPLETED -> 1f
            ReminderState.PAUSED -> {
                val total = totalPhaseDurationMillis.coerceAtLeast(1L).toFloat()
                val remaining = remainingMillisWhenPaused.toFloat()
                ((total - remaining) / total).coerceIn(0f, 1f)
            }
        }
    }

    companion object {
        const val TWENTY_MINUTES_MILLIS: Long = 20L * 60L * 1000L // 1,200,000 ms
        const val DEFAULT_REST_SECONDS: Int = 20
        const val ALT_REST_SECONDS: Int = 30

        fun normalizeRestSeconds(seconds: Int): Int {
            return if (seconds == ALT_REST_SECONDS) ALT_REST_SECONDS else DEFAULT_REST_SECONDS
        }
    }
}

data class TransitionResult(
    val previous: ReminderSnapshot,
    val current: ReminderSnapshot,
    val changed: Boolean
)
