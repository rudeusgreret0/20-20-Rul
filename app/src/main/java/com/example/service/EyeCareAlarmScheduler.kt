package com.example.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.example.model.ReminderSnapshot
import com.example.model.ReminderState
import com.example.receiver.EyeCareReceiver

/**
 * Schedules and cancels time-critical wake-up alarms using Android's AlarmManager.
 * Ensures countdown transitions occur even if the device screen is off or in Doze mode.
 * Handles Android 12+ SCHEDULE_EXACT_ALARM restrictions gracefully with automatic fallback.
 */
class EyeCareAlarmScheduler(private val context: Context) {

    private val alarmManager: AlarmManager? =
        context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager

    fun canScheduleExactAlarms(): Boolean {
        val manager = alarmManager ?: return false
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            manager.canScheduleExactAlarms()
        } else {
            true
        }
    }

    /**
     * Synchronizes system AlarmManager wake-up alarms with the current ReminderSnapshot.
     */
    fun syncAlarmForSnapshot(snapshot: ReminderSnapshot) {
        cancelAllAlarms()
        val manager = alarmManager ?: return

        val action = when (snapshot.state) {
            ReminderState.COUNTING_20_MINUTES -> EyeCareReceiver.ACTION_WORK_TIMER_EXPIRED
            ReminderState.COUNTING_REST -> EyeCareReceiver.ACTION_REST_TIMER_EXPIRED
            else -> return
        }

        val triggerAtMillis = snapshot.targetEpochMillis
        if (triggerAtMillis <= 0L) return

        val pendingIntent = buildAlarmPendingIntent(action, snapshot.stateVersion)

        runCatching {
            if (canScheduleExactAlarms()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    manager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        triggerAtMillis,
                        pendingIntent
                    )
                } else {
                    manager.setExact(
                        AlarmManager.RTC_WAKEUP,
                        triggerAtMillis,
                        pendingIntent
                    )
                }
            } else {
                // Fallback when exact alarm permission is denied/revoked
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    manager.setAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        triggerAtMillis,
                        pendingIntent
                    )
                } else {
                    manager.set(
                        AlarmManager.RTC_WAKEUP,
                        triggerAtMillis,
                        pendingIntent
                    )
                }
            }
        }
    }

    fun cancelAllAlarms() {
        val manager = alarmManager ?: return
        runCatching {
            manager.cancel(buildAlarmPendingIntent(EyeCareReceiver.ACTION_WORK_TIMER_EXPIRED, 0L))
            manager.cancel(buildAlarmPendingIntent(EyeCareReceiver.ACTION_REST_TIMER_EXPIRED, 0L))
        }
    }

    private fun buildAlarmPendingIntent(action: String, stateVersion: Long): PendingIntent {
        val intent = Intent(context, EyeCareReceiver::class.java).apply {
            this.action = action
            putExtra(EyeCareReceiver.EXTRA_STATE_VERSION, stateVersion)
        }
        val requestCode = if (action == EyeCareReceiver.ACTION_WORK_TIMER_EXPIRED) {
            REQUEST_CODE_WORK_ALARM
        } else {
            REQUEST_CODE_REST_ALARM
        }
        return PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    companion object {
        private const val REQUEST_CODE_WORK_ALARM = 2001
        private const val REQUEST_CODE_REST_ALARM = 2002
    }
}
