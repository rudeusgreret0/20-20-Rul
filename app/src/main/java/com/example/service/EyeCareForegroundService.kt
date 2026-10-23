package com.example.service

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import com.example.model.ReminderState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Foreground service that maintains reliable background operation for the Twenty Rule
 * 20-20-20 cycle when the app is minimized, removed from recent apps, or the screen is off.
 */
class EyeCareForegroundService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var tickerJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        startTickerLoop()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        isRunning = true
        val coordinator = EyeCareCoordinator.getInstance(applicationContext)
        val snapshot = coordinator.repository.currentSnapshot

        if (snapshot.state == ReminderState.DISABLED) {
            isRunning = false
            stopForegroundCompat()
            stopSelf()
            return START_NOT_STICKY
        }

        val notification = coordinator.notificationHelper.buildOngoingNotification(
            snapshot = snapshot,
            nowMillis = System.currentTimeMillis()
        )

        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(
                    EyeCareNotificationHelper.NOTIFICATION_ID_ONGOING,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                )
            } else {
                startForeground(
                    EyeCareNotificationHelper.NOTIFICATION_ID_ONGOING,
                    notification
                )
            }
        }

        coordinator.checkTimerExpirationOnTick()
        startTickerLoop()

        return START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        val coordinator = EyeCareCoordinator.getInstance(applicationContext)
        val snapshot = coordinator.repository.currentSnapshot
        if (snapshot.state != ReminderState.DISABLED) {
            coordinator.alarmScheduler.syncAlarmForSnapshot(snapshot)
            coordinator.notificationHelper.updateOngoingNotification(
                snapshot = snapshot,
                nowMillis = System.currentTimeMillis(),
                force = true
            )
        }
    }

    private fun startTickerLoop() {
        if (tickerJob?.isActive == true) return
        tickerJob = serviceScope.launch {
            val coordinator = EyeCareCoordinator.getInstance(applicationContext)
            while (isActive) {
                val current = coordinator.repository.currentSnapshot
                if (current.state == ReminderState.DISABLED) {
                    isRunning = false
                    stopForegroundCompat()
                    stopSelf()
                    break
                }
                val updated = coordinator.checkTimerExpirationOnTick()
                if (updated.state == ReminderState.COUNTING_20_MINUTES ||
                    updated.state == ReminderState.COUNTING_REST
                ) {
                    coordinator.notificationHelper.updateOngoingNotification(
                        snapshot = updated,
                        nowMillis = System.currentTimeMillis(),
                        force = false
                    )
                }
                delay(1000L)
            }
        }
    }

    private fun stopForegroundCompat() {
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(STOP_FOREGROUND_REMOVE)
            } else {
                @Suppress("DEPRECATION")
                stopForeground(true)
            }
        }
    }

    override fun onDestroy() {
        isRunning = false
        tickerJob?.cancel()
        serviceScope.cancel()
        super.onDestroy()
    }

    companion object {
        const val ACTION_START_OR_SYNC = "com.example.action.START_OR_SYNC_SERVICE"

        @Volatile
        var isRunning: Boolean = false
            private set
    }
}
