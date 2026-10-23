package com.example.data

import android.content.Context
import android.content.SharedPreferences
import com.example.model.EyeCareClock
import com.example.model.PausedPhase
import com.example.model.ReminderSnapshot
import com.example.model.ReminderSnapshot.Companion.DEFAULT_REST_SECONDS
import com.example.model.ReminderSnapshot.Companion.TWENTY_MINUTES_MILLIS
import com.example.model.ReminderState
import com.example.model.SystemEyeCareClock
import com.example.model.TransitionResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Thread-safe persistent repository and state machine for the 20-20-20 Eye Care Reminder.
 * All state transitions are synchronized, idempotent, and immediately persisted to SharedPreferences
 * so process kills, activity recreations, and device reboots never lose the active cycle.
 */
class EyeCareRepository(
    context: Context,
    private val clock: EyeCareClock = SystemEyeCareClock,
    prefsName: String = PREFS_NAME
) {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(prefsName, Context.MODE_PRIVATE)

    private val _snapshotFlow = MutableStateFlow(loadFromPrefs())
    val snapshotFlow: StateFlow<ReminderSnapshot> = _snapshotFlow.asStateFlow()

    val currentSnapshot: ReminderSnapshot
        @Synchronized get() = _snapshotFlow.value

    /**
     * Enables the reminder system and immediately starts the 20-minute countdown.
     * If already active, this is a no-op unless forceRestart is true.
     */
    @Synchronized
    fun enableReminder(forceRestart: Boolean = false): TransitionResult {
        val current = _snapshotFlow.value
        if (current.state != ReminderState.DISABLED && !forceRestart) {
            return TransitionResult(current, current, changed = false)
        }
        val now = clock.nowMillis()
        val next = current.copy(
            state = ReminderState.COUNTING_20_MINUTES,
            targetEpochMillis = now + TWENTY_MINUTES_MILLIS,
            totalPhaseDurationMillis = TWENTY_MINUTES_MILLIS,
            remainingMillisWhenPaused = TWENTY_MINUTES_MILLIS,
            pausedPhase = PausedPhase.WORK_20_MINUTES,
            lastCheckpointRemainingMillis = TWENTY_MINUTES_MILLIS,
            stateVersion = current.stateVersion + 1L
        )
        persistAndEmit(next)
        return TransitionResult(current, next, changed = true)
    }

    /**
     * Triggered when the 20-minute countdown finishes.
     * Guards against duplicate alarm triggers by checking current state and optional stateVersion.
     */
    @Synchronized
    fun onWorkTimerExpired(expectedVersion: Long? = null): TransitionResult {
        val current = _snapshotFlow.value
        if (current.state != ReminderState.COUNTING_20_MINUTES) {
            return TransitionResult(current, current, changed = false)
        }
        if (expectedVersion != null && expectedVersion != current.stateVersion) {
            return TransitionResult(current, current, changed = false)
        }
        val next = current.copy(
            state = ReminderState.ALARM_RINGING,
            targetEpochMillis = 0L,
            lastCheckpointRemainingMillis = 0L,
            stateVersion = current.stateVersion + 1L
        )
        persistAndEmit(next)
        return TransitionResult(current, next, changed = true)
    }

    /**
     * Stops the 20-minute alarm and immediately begins the 20-second or 30-second rest countdown.
     * Handles alarm dismissal exactly once whether triggered from notification or in-app UI.
     */
    @Synchronized
    fun acknowledgeAlarmAndStartRest(): TransitionResult {
        val current = _snapshotFlow.value
        if (current.state != ReminderState.ALARM_RINGING) {
            return TransitionResult(current, current, changed = false)
        }
        val now = clock.nowMillis()
        val restMillis = current.restDurationSeconds * 1000L
        val next = current.copy(
            state = ReminderState.COUNTING_REST,
            targetEpochMillis = now + restMillis,
            totalPhaseDurationMillis = restMillis,
            remainingMillisWhenPaused = restMillis,
            pausedPhase = PausedPhase.REST_COUNTDOWN,
            lastCheckpointRemainingMillis = restMillis,
            stateVersion = current.stateVersion + 1L
        )
        persistAndEmit(next)
        return TransitionResult(current, next, changed = true)
    }

    /**
     * Triggered when the 20-second or 30-second eye-rest countdown finishes.
     * Transitions to REST_COMPLETED exactly once.
     */
    @Synchronized
    fun onRestTimerExpired(expectedVersion: Long? = null): TransitionResult {
        val current = _snapshotFlow.value
        if (current.state != ReminderState.COUNTING_REST) {
            return TransitionResult(current, current, changed = false)
        }
        if (expectedVersion != null && expectedVersion != current.stateVersion) {
            return TransitionResult(current, current, changed = false)
        }
        val next = current.copy(
            state = ReminderState.REST_COMPLETED,
            targetEpochMillis = 0L,
            lastCheckpointRemainingMillis = 0L,
            completedCyclesCount = current.completedCyclesCount + 1,
            stateVersion = current.stateVersion + 1L
        )
        persistAndEmit(next)
        return TransitionResult(current, next, changed = true)
    }

    /**
     * Acknowledges completion of the rest period and immediately starts a new 20-minute countdown.
     */
    @Synchronized
    fun acknowledgeRestCompleted(): TransitionResult {
        val current = _snapshotFlow.value
        if (current.state != ReminderState.REST_COMPLETED) {
            return TransitionResult(current, current, changed = false)
        }
        val now = clock.nowMillis()
        val next = current.copy(
            state = ReminderState.COUNTING_20_MINUTES,
            targetEpochMillis = now + TWENTY_MINUTES_MILLIS,
            totalPhaseDurationMillis = TWENTY_MINUTES_MILLIS,
            remainingMillisWhenPaused = TWENTY_MINUTES_MILLIS,
            pausedPhase = PausedPhase.WORK_20_MINUTES,
            lastCheckpointRemainingMillis = TWENTY_MINUTES_MILLIS,
            stateVersion = current.stateVersion + 1L
        )
        persistAndEmit(next)
        return TransitionResult(current, next, changed = true)
    }

    /**
     * Pauses the reminder cycle, preserving remaining time and phase so it can be resumed.
     */
    @Synchronized
    fun pauseReminder(): TransitionResult {
        val current = _snapshotFlow.value
        if (current.state == ReminderState.DISABLED || current.state == ReminderState.PAUSED) {
            return TransitionResult(current, current, changed = false)
        }
        val now = clock.nowMillis()
        val (phase, totalMillis, remainingMillis) = when (current.state) {
            ReminderState.COUNTING_20_MINUTES -> {
                val rem = (current.targetEpochMillis - now).coerceIn(1_000L, TWENTY_MINUTES_MILLIS)
                Triple(PausedPhase.WORK_20_MINUTES, TWENTY_MINUTES_MILLIS, rem)
            }
            ReminderState.COUNTING_REST -> {
                val restTotal = current.restDurationSeconds * 1000L
                val rem = (current.targetEpochMillis - now).coerceIn(1_000L, restTotal)
                Triple(PausedPhase.REST_COUNTDOWN, restTotal, rem)
            }
            ReminderState.ALARM_RINGING -> {
                val restTotal = current.restDurationSeconds * 1000L
                Triple(PausedPhase.REST_COUNTDOWN, restTotal, restTotal)
            }
            ReminderState.REST_COMPLETED -> {
                Triple(PausedPhase.WORK_20_MINUTES, TWENTY_MINUTES_MILLIS, TWENTY_MINUTES_MILLIS)
            }
            else -> Triple(current.pausedPhase, current.totalPhaseDurationMillis, current.remainingMillisWhenPaused)
        }

        val next = current.copy(
            state = ReminderState.PAUSED,
            targetEpochMillis = 0L,
            totalPhaseDurationMillis = totalMillis,
            remainingMillisWhenPaused = remainingMillis,
            pausedPhase = phase,
            lastCheckpointRemainingMillis = remainingMillis,
            stateVersion = current.stateVersion + 1L
        )
        persistAndEmit(next)
        return TransitionResult(current, next, changed = true)
    }

    /**
     * Resumes a paused reminder cycle from the exact remaining duration.
     */
    @Synchronized
    fun resumeReminder(): TransitionResult {
        val current = _snapshotFlow.value
        if (current.state != ReminderState.PAUSED) {
            return TransitionResult(current, current, changed = false)
        }
        val now = clock.nowMillis()
        val resumedState = when (current.pausedPhase) {
            PausedPhase.WORK_20_MINUTES -> ReminderState.COUNTING_20_MINUTES
            PausedPhase.REST_COUNTDOWN -> ReminderState.COUNTING_REST
        }
        val durationToRun = current.remainingMillisWhenPaused.coerceAtLeast(1_000L)
        val totalDuration = when (current.pausedPhase) {
            PausedPhase.WORK_20_MINUTES -> TWENTY_MINUTES_MILLIS
            PausedPhase.REST_COUNTDOWN -> current.restDurationSeconds * 1000L
        }
        val next = current.copy(
            state = resumedState,
            targetEpochMillis = now + durationToRun,
            totalPhaseDurationMillis = totalDuration,
            lastCheckpointRemainingMillis = durationToRun,
            stateVersion = current.stateVersion + 1L
        )
        persistAndEmit(next)
        return TransitionResult(current, next, changed = true)
    }

    /**
     * Stops and disables the reminder system completely.
     */
    @Synchronized
    fun disableReminder(): TransitionResult {
        val current = _snapshotFlow.value
        if (current.state == ReminderState.DISABLED) {
            return TransitionResult(current, current, changed = false)
        }
        val next = current.copy(
            state = ReminderState.DISABLED,
            targetEpochMillis = 0L,
            totalPhaseDurationMillis = TWENTY_MINUTES_MILLIS,
            remainingMillisWhenPaused = TWENTY_MINUTES_MILLIS,
            pausedPhase = PausedPhase.WORK_20_MINUTES,
            lastCheckpointRemainingMillis = TWENTY_MINUTES_MILLIS,
            stateVersion = current.stateVersion + 1L
        )
        persistAndEmit(next)
        return TransitionResult(current, next, changed = true)
    }

    /**
     * Updates the eye-rest duration (20s or 30s) and persists it immediately.
     */
    @Synchronized
    fun setRestDurationSeconds(seconds: Int): TransitionResult {
        val normalized = ReminderSnapshot.normalizeRestSeconds(seconds)
        val current = _snapshotFlow.value
        if (current.restDurationSeconds == normalized) {
            return TransitionResult(current, current, changed = false)
        }
        val newRestMillis = normalized * 1000L
        val updatedPausedRemaining = if (
            current.state == ReminderState.PAUSED &&
            current.pausedPhase == PausedPhase.REST_COUNTDOWN
        ) {
            current.remainingMillisWhenPaused.coerceAtMost(newRestMillis)
        } else {
            current.remainingMillisWhenPaused
        }
        val updatedTotalDuration = if (
            current.state == ReminderState.PAUSED &&
            current.pausedPhase == PausedPhase.REST_COUNTDOWN
        ) {
            newRestMillis
        } else {
            current.totalPhaseDurationMillis
        }

        val next = current.copy(
            restDurationSeconds = normalized,
            totalPhaseDurationMillis = updatedTotalDuration,
            remainingMillisWhenPaused = updatedPausedRemaining
        )
        persistAndEmit(next)
        return TransitionResult(current, next, changed = true)
    }

    /**
     * Enables or disables alarm and completion sound effects.
     */
    @Synchronized
    fun setSoundEnabled(enabled: Boolean): TransitionResult {
        val current = _snapshotFlow.value
        if (current.soundEnabled == enabled) {
            return TransitionResult(current, current, changed = false)
        }
        val next = current.copy(soundEnabled = enabled)
        persistAndEmit(next)
        return TransitionResult(current, next, changed = true)
    }

    /**
     * Reconciles the persisted state with the current clock after process recreation,
     * screen wake, periodic service tick, or device reboot.
     *
     * @param isDeviceBoot true when invoked from BOOT_COMPLETED after the phone was powered off.
     */
    @Synchronized
    fun reconcileState(isDeviceBoot: Boolean = false): TransitionResult {
        val current = _snapshotFlow.value
        val now = clock.nowMillis()

        return when (current.state) {
            ReminderState.DISABLED,
            ReminderState.PAUSED,
            ReminderState.ALARM_RINGING,
            ReminderState.REST_COMPLETED -> {
                TransitionResult(current, current, changed = false)
            }

            ReminderState.COUNTING_20_MINUTES -> {
                if (isDeviceBoot) {
                    // Phone was shut down and restarted. If targetEpochMillis is still in the future
                    // within 20 minutes, keep it; if it passed while the phone was powered off,
                    // resume the remaining countdown from the last checkpoint so the cycle continues
                    // seamlessly without firing a stale alarm from when the device was off.
                    val remaining = if (
                        current.targetEpochMillis > now &&
                        (current.targetEpochMillis - now) <= TWENTY_MINUTES_MILLIS
                    ) {
                        current.targetEpochMillis - now
                    } else {
                        current.lastCheckpointRemainingMillis.coerceIn(5_000L, TWENTY_MINUTES_MILLIS)
                    }
                    val next = current.copy(
                        targetEpochMillis = now + remaining,
                        totalPhaseDurationMillis = TWENTY_MINUTES_MILLIS,
                        lastCheckpointRemainingMillis = remaining,
                        stateVersion = current.stateVersion + 1L
                    )
                    persistAndEmit(next)
                    TransitionResult(current, next, changed = true)
                } else {
                    if (now >= current.targetEpochMillis) {
                        onWorkTimerExpired(current.stateVersion)
                    } else {
                        val remaining = (current.targetEpochMillis - now).coerceIn(1_000L, TWENTY_MINUTES_MILLIS)
                        if (kotlin.math.abs(current.lastCheckpointRemainingMillis - remaining) >= 5_000L) {
                            val checkpointUpdated = current.copy(lastCheckpointRemainingMillis = remaining)
                            persistAndEmit(checkpointUpdated)
                        }
                        TransitionResult(current, _snapshotFlow.value, changed = false)
                    }
                }
            }

            ReminderState.COUNTING_REST -> {
                val restTotal = current.restDurationSeconds * 1000L
                if (isDeviceBoot) {
                    val remaining = if (
                        current.targetEpochMillis > now &&
                        (current.targetEpochMillis - now) <= restTotal
                    ) {
                        current.targetEpochMillis - now
                    } else {
                        current.lastCheckpointRemainingMillis.coerceIn(5_000L, restTotal)
                    }
                    val next = current.copy(
                        targetEpochMillis = now + remaining,
                        totalPhaseDurationMillis = restTotal,
                        lastCheckpointRemainingMillis = remaining,
                        stateVersion = current.stateVersion + 1L
                    )
                    persistAndEmit(next)
                    TransitionResult(current, next, changed = true)
                } else {
                    if (now >= current.targetEpochMillis) {
                        onRestTimerExpired(current.stateVersion)
                    } else {
                        val remaining = (current.targetEpochMillis - now).coerceIn(1_000L, restTotal)
                        if (kotlin.math.abs(current.lastCheckpointRemainingMillis - remaining) >= 2_000L) {
                            val checkpointUpdated = current.copy(lastCheckpointRemainingMillis = remaining)
                            persistAndEmit(checkpointUpdated)
                        }
                        TransitionResult(current, _snapshotFlow.value, changed = false)
                    }
                }
            }
        }
    }

    private fun loadFromPrefs(): ReminderSnapshot {
        val stateName = prefs.getString(KEY_STATE, ReminderState.DISABLED.name) ?: ReminderState.DISABLED.name
        val state = runCatching { ReminderState.valueOf(stateName) }.getOrDefault(ReminderState.DISABLED)
        val pausedPhaseName = prefs.getString(KEY_PAUSED_PHASE, PausedPhase.WORK_20_MINUTES.name)
            ?: PausedPhase.WORK_20_MINUTES.name
        val pausedPhase = runCatching { PausedPhase.valueOf(pausedPhaseName) }
            .getOrDefault(PausedPhase.WORK_20_MINUTES)
        val restSeconds = ReminderSnapshot.normalizeRestSeconds(
            prefs.getInt(KEY_REST_DURATION_SECONDS, DEFAULT_REST_SECONDS)
        )

        return ReminderSnapshot(
            state = state,
            restDurationSeconds = restSeconds,
            soundEnabled = prefs.getBoolean(KEY_SOUND_ENABLED, true),
            targetEpochMillis = prefs.getLong(KEY_TARGET_EPOCH_MILLIS, 0L),
            totalPhaseDurationMillis = prefs.getLong(KEY_TOTAL_PHASE_MILLIS, TWENTY_MINUTES_MILLIS),
            remainingMillisWhenPaused = prefs.getLong(KEY_REMAINING_WHEN_PAUSED, TWENTY_MINUTES_MILLIS),
            pausedPhase = pausedPhase,
            lastCheckpointRemainingMillis = prefs.getLong(KEY_LAST_CHECKPOINT_REMAINING, TWENTY_MINUTES_MILLIS),
            completedCyclesCount = prefs.getInt(KEY_COMPLETED_CYCLES, 0),
            stateVersion = prefs.getLong(KEY_STATE_VERSION, 0L)
        )
    }

    private fun persistAndEmit(snapshot: ReminderSnapshot) {
        prefs.edit()
            .putString(KEY_STATE, snapshot.state.name)
            .putInt(KEY_REST_DURATION_SECONDS, snapshot.restDurationSeconds)
            .putBoolean(KEY_SOUND_ENABLED, snapshot.soundEnabled)
            .putLong(KEY_TARGET_EPOCH_MILLIS, snapshot.targetEpochMillis)
            .putLong(KEY_TOTAL_PHASE_MILLIS, snapshot.totalPhaseDurationMillis)
            .putLong(KEY_REMAINING_WHEN_PAUSED, snapshot.remainingMillisWhenPaused)
            .putString(KEY_PAUSED_PHASE, snapshot.pausedPhase.name)
            .putLong(KEY_LAST_CHECKPOINT_REMAINING, snapshot.lastCheckpointRemainingMillis)
            .putInt(KEY_COMPLETED_CYCLES, snapshot.completedCyclesCount)
            .putLong(KEY_STATE_VERSION, snapshot.stateVersion)
            .commit()
        _snapshotFlow.value = snapshot
    }

    companion object {
        const val PREFS_NAME = "eye_care_20_20_prefs"
        private const val KEY_STATE = "state"
        private const val KEY_REST_DURATION_SECONDS = "rest_duration_seconds"
        private const val KEY_SOUND_ENABLED = "sound_enabled"
        private const val KEY_TARGET_EPOCH_MILLIS = "target_epoch_millis"
        private const val KEY_TOTAL_PHASE_MILLIS = "total_phase_millis"
        private const val KEY_REMAINING_WHEN_PAUSED = "remaining_when_paused"
        private const val KEY_PAUSED_PHASE = "paused_phase"
        private const val KEY_LAST_CHECKPOINT_REMAINING = "last_checkpoint_remaining"
        private const val KEY_COMPLETED_CYCLES = "completed_cycles"
        private const val KEY_STATE_VERSION = "state_version"

        @Volatile
        private var instance: EyeCareRepository? = null

        fun getInstance(context: Context): EyeCareRepository {
            return instance ?: synchronized(this) {
                instance ?: EyeCareRepository(context.applicationContext).also { instance = it }
            }
        }
    }
}
