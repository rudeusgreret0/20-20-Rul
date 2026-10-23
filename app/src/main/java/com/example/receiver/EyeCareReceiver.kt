package com.example.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.model.ReminderState
import com.example.service.EyeCareCoordinator

/**
 * Handles system broadcasts (BOOT_COMPLETED, package update) and actionable notification /
 * AlarmManager intents. Ensures background operation works without opening the app.
 */
class EyeCareReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        val coordinator = EyeCareCoordinator.getInstance(context.applicationContext)

        when (action) {
            Intent.ACTION_BOOT_COMPLETED,
            ACTION_QUICKBOOT_POWERON,
            Intent.ACTION_MY_PACKAGE_REPLACED -> {
                val snapshot = coordinator.repository.currentSnapshot
                if (snapshot.state != ReminderState.DISABLED) {
                    coordinator.reconcile(isDeviceBoot = true)
                }
            }

            ACTION_WORK_TIMER_EXPIRED -> {
                val version = if (intent.hasExtra(EXTRA_STATE_VERSION)) {
                    intent.getLongExtra(EXTRA_STATE_VERSION, -1L).takeIf { it >= 0L }
                } else {
                    null
                }
                coordinator.onWorkTimerExpired(version)
            }

            ACTION_REST_TIMER_EXPIRED -> {
                val version = if (intent.hasExtra(EXTRA_STATE_VERSION)) {
                    intent.getLongExtra(EXTRA_STATE_VERSION, -1L).takeIf { it >= 0L }
                } else {
                    null
                }
                coordinator.onRestTimerExpired(version)
            }

            ACTION_ACKNOWLEDGE_ALARM -> {
                coordinator.acknowledgeAlarmAndStartRest()
            }

            ACTION_ACKNOWLEDGE_REST_DONE -> {
                coordinator.acknowledgeRestCompleted()
            }

            ACTION_PAUSE_REMINDER -> {
                coordinator.pauseReminder()
            }

            ACTION_RESUME_REMINDER -> {
                coordinator.resumeReminder()
            }

            ACTION_DISABLE_REMINDER -> {
                coordinator.disableReminder()
            }
        }
    }

    companion object {
        const val ACTION_QUICKBOOT_POWERON = "android.intent.action.QUICKBOOT_POWERON"
        const val ACTION_WORK_TIMER_EXPIRED = "com.example.action.WORK_TIMER_EXPIRED"
        const val ACTION_REST_TIMER_EXPIRED = "com.example.action.REST_TIMER_EXPIRED"
        const val ACTION_ACKNOWLEDGE_ALARM = "com.example.action.ACKNOWLEDGE_ALARM"
        const val ACTION_ACKNOWLEDGE_REST_DONE = "com.example.action.ACKNOWLEDGE_REST_DONE"
        const val ACTION_PAUSE_REMINDER = "com.example.action.PAUSE_REMINDER"
        const val ACTION_RESUME_REMINDER = "com.example.action.RESUME_REMINDER"
        const val ACTION_DISABLE_REMINDER = "com.example.action.DISABLE_REMINDER"
        const val EXTRA_STATE_VERSION = "extra_state_version"
    }
}
