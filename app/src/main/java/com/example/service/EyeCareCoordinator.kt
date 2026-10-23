package com.example.service

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.ContextCompat
import com.example.data.EyeCareRepository
import com.example.model.EyeCareClock
import com.example.model.ReminderSnapshot
import com.example.model.ReminderState
import com.example.model.SystemEyeCareClock
import com.example.model.TransitionResult
import kotlinx.coroutines.flow.StateFlow

/**
 * Coordinates the state machine (EyeCareRepository), system alarms (EyeCareAlarmScheduler),
 * sound/vibration playback (EyeCareSoundPlayer), notifications (EyeCareNotificationHelper),
 * and the foreground service (EyeCareForegroundService).
 *
 * Ensures that every transition happens atomically and exactly once, whether triggered from
 * the UI, a notification action, a background service tick, an AlarmManager broadcast, or device reboot.
 */
class EyeCareCoordinator(
    private val appContext: Context,
    val repository: EyeCareRepository = EyeCareRepository.getInstance(appContext),
    val alarmScheduler: EyeCareAlarmScheduler = EyeCareAlarmScheduler(appContext),
    val soundPlayer: EyeCareSoundPlayer = EyeCareSoundPlayer(appContext),
    val notificationHelper: EyeCareNotificationHelper = EyeCareNotificationHelper(appContext),
    private val clock: EyeCareClock = SystemEyeCareClock,
    private val startServiceHooksEnabled: Boolean = true
) {
    val snapshotFlow: StateFlow<ReminderSnapshot> = repository.snapshotFlow

    fun enableReminder(): ReminderSnapshot {
        val result = repository.enableReminder(forceRestart = false)
        applySideEffects(result, isSoundTriggerAllowed = true)
        return result.current
    }

    fun pauseReminder(): ReminderSnapshot {
        val result = repository.pauseReminder()
        applySideEffects(result, isSoundTriggerAllowed = true)
        return result.current
    }

    fun resumeReminder(): ReminderSnapshot {
        val result = repository.resumeReminder()
        applySideEffects(result, isSoundTriggerAllowed = true)
        return result.current
    }

    fun disableReminder(): ReminderSnapshot {
        val result = repository.disableReminder()
        applySideEffects(result, isSoundTriggerAllowed = true)
        return result.current
    }

    fun acknowledgeAlarmAndStartRest(): ReminderSnapshot {
        val result = repository.acknowledgeAlarmAndStartRest()
        applySideEffects(result, isSoundTriggerAllowed = true)
        return result.current
    }

    fun acknowledgeRestCompleted(): ReminderSnapshot {
        val result = repository.acknowledgeRestCompleted()
        applySideEffects(result, isSoundTriggerAllowed = true)
        return result.current
    }

    fun onWorkTimerExpired(expectedVersion: Long? = null): ReminderSnapshot {
        val result = repository.onWorkTimerExpired(expectedVersion)
        applySideEffects(result, isSoundTriggerAllowed = true)
        return result.current
    }

    fun onRestTimerExpired(expectedVersion: Long? = null): ReminderSnapshot {
        val result = repository.onRestTimerExpired(expectedVersion)
        applySideEffects(result, isSoundTriggerAllowed = true)
        return result.current
    }

    fun setRestDurationSeconds(seconds: Int): ReminderSnapshot {
        val result = repository.setRestDurationSeconds(seconds)
        if (result.changed) {
            notificationHelper.updateOngoingNotification(result.current, clock.nowMillis())
        }
        return result.current
    }

    fun setSoundEnabled(enabled: Boolean): ReminderSnapshot {
        val result = repository.setSoundEnabled(enabled)
        if (result.changed) {
            if (!enabled) {
                soundPlayer.stopAlarmLoop()
            } else if (result.current.state == ReminderState.ALARM_RINGING) {
                soundPlayer.startAlarmLoop(soundEnabled = true)
            }
        }
        return result.current
    }

    /**
     * Reconciles timer state after process recreation, screen unlock, service tick, or device reboot.
     */
    fun reconcile(isDeviceBoot: Boolean = false): ReminderSnapshot {
        val result = repository.reconcileState(isDeviceBoot = isDeviceBoot)
        if (result.changed) {
            applySideEffects(result, isSoundTriggerAllowed = !isDeviceBoot)
        } else {
            val current = result.current
            if (current.state != ReminderState.DISABLED) {
                alarmScheduler.syncAlarmForSnapshot(current)
                notificationHelper.updateOngoingNotification(current, clock.nowMillis())
                notificationHelper.syncAlertNotification(current)
                ensureForegroundServiceRunning()
            }
        }
        return result.current
    }

    private fun applySideEffects(
        result: TransitionResult,
        isSoundTriggerAllowed: Boolean
    ) {
        val current = result.current
        if (!result.changed && current.state == ReminderState.DISABLED) {
            return
        }

        // 1. Schedule or cancel exact/inexact system wake-up alarms
        alarmScheduler.syncAlarmForSnapshot(current)

        // 2. Manage sound playback transitions
        if (result.changed) {
            when (current.state) {
                ReminderState.ALARM_RINGING -> {
                    if (isSoundTriggerAllowed) {
                        soundPlayer.startAlarmLoop(current.soundEnabled)
                    }
                }
                ReminderState.REST_COMPLETED -> {
                    if (isSoundTriggerAllowed) {
                        soundPlayer.playRestCompletionSound(current.soundEnabled)
                    }
                }
                else -> {
                    soundPlayer.stopAlarmLoop()
                }
            }
        }

        // 3. Synchronize notifications and Foreground Service
        if (current.state == ReminderState.DISABLED) {
            soundPlayer.stopAlarmLoop()
            notificationHelper.cancelAllNotifications()
            stopForegroundService()
        } else {
            notificationHelper.updateOngoingNotification(current, clock.nowMillis())
            notificationHelper.syncAlertNotification(current)
            ensureForegroundServiceRunning()
        }
    }

    private fun ensureForegroundServiceRunning() {
        if (!startServiceHooksEnabled) return
        runCatching {
            val intent = Intent(appContext, EyeCareForegroundService::class.java).apply {
                action = EyeCareForegroundService.ACTION_START_OR_SYNC
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                ContextCompat.startForegroundService(appContext, intent)
            } else {
                appContext.startService(intent)
            }
        }
    }

    private fun stopForegroundService() {
        if (!startServiceHooksEnabled) return
        runCatching {
            val intent = Intent(appContext, EyeCareForegroundService::class.java)
            appContext.stopService(intent)
        }
    }

    companion object {
        @Volatile
        private var instance: EyeCareCoordinator? = null

        fun getInstance(context: Context): EyeCareCoordinator {
            return instance ?: synchronized(this) {
                instance ?: EyeCareCoordinator(context.applicationContext).also { instance = it }
            }
        }
    }
}
