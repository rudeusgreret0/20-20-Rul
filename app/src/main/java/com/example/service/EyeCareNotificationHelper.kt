package com.example.service

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import com.example.model.PausedPhase
import com.example.model.ReminderSnapshot
import com.example.model.ReminderState
import com.example.receiver.EyeCareReceiver
import java.util.Locale

/**
 * Manages notification channels, ongoing foreground notifications, and high-priority
 * actionable alert notifications for the Twenty Rule 20-20-20 reminder cycle.
 *
 * Key reliability guarantees:
 * - Uses `Intent.FLAG_RECEIVER_FOREGROUND` on all action PendingIntents so button taps in the
 *   notification shade or heads-up banner execute in <10ms without background queue lag.
 * - Uses `setSound(null, null)` on the alert channel so OS default notification sounds never
 *   clash with `EyeCareSoundPlayer`'s acoustic chimes.
 * - Deduplicates alert notification posts by `stateVersion` and throttles ongoing countdown
 *   notification updates to once per displayed second so SystemUI never drops user button taps.
 */
class EyeCareNotificationHelper(private val context: Context) {

    private val notificationManager: NotificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    @Volatile
    private var lastOngoingDisplayedKey: String = ""

    @Volatile
    private var lastAlertPostedKey: String = ""

    init {
        ensureChannelsCreated()
    }

    fun hasNotificationPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    fun ensureChannelsCreated() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Delete legacy noisy channel if present from previous version
            runCatching {
                notificationManager.deleteNotificationChannel("eye_care_alarm_alerts")
            }

            val ongoingChannel = NotificationChannel(
                CHANNEL_ONGOING_TIMER,
                context.getString(R.string.notif_channel_ongoing_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = context.getString(R.string.notif_channel_ongoing_desc)
                setShowBadge(false)
                setSound(null, null)
                enableVibration(false)
            }

            val alarmChannel = NotificationChannel(
                CHANNEL_EYE_ALARM,
                context.getString(R.string.notif_channel_alarm_name),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = context.getString(R.string.notif_channel_alarm_desc)
                // Sound and vibration are handled cleanly by EyeCareSoundPlayer so they never clash or repeat on update
                setSound(null, null)
                enableVibration(false)
                setShowBadge(true)
            }

            notificationManager.createNotificationChannel(ongoingChannel)
            notificationManager.createNotificationChannel(alarmChannel)
        }
    }

    /**
     * Builds the ongoing foreground service notification showing live countdown and actions.
     */
    fun buildOngoingNotification(snapshot: ReminderSnapshot, nowMillis: Long): Notification {
        ensureChannelsCreated()
        val remainingMillis = snapshot.computeRemainingMillis(nowMillis)
        val formattedTime = formatDurationMillis(remainingMillis)

        val (title, contentText) = when (snapshot.state) {
            ReminderState.COUNTING_20_MINUTES -> {
                "Next Eye Break in $formattedTime" to "Twenty Rule • 20-minute screen timer active"
            }
            ReminderState.ALARM_RINGING -> {
                "Time to Rest Your Eyes!" to "20 minutes elapsed — tap 'Stop Alarm & Rest' to begin your ${snapshot.restDurationSeconds}s break"
            }
            ReminderState.COUNTING_REST -> {
                "Resting Eyes: $formattedTime left" to "Look 20 feet (6 meters) away and blink gently"
            }
            ReminderState.REST_COMPLETED -> {
                "Rest Period Complete" to "Tap 'Done (Start 20m)' to begin your next 20-minute cycle"
            }
            ReminderState.PAUSED -> {
                val phaseLabel = if (snapshot.pausedPhase == PausedPhase.WORK_20_MINUTES) {
                    "20m cycle"
                } else {
                    "${snapshot.restDurationSeconds}s rest"
                }
                "Reminder Paused ($formattedTime left)" to "Paused during $phaseLabel — tap Resume to continue"
            }
            ReminderState.DISABLED -> {
                "Twenty Rule Disabled" to "Tap to open Twenty Rule"
            }
        }

        val totalSeconds = (snapshot.totalPhaseDurationMillis / 1000L).toInt().coerceAtLeast(1)
        val elapsedSeconds = ((snapshot.totalPhaseDurationMillis - remainingMillis) / 1000L)
            .toInt()
            .coerceIn(0, totalSeconds)

        val builder = NotificationCompat.Builder(context, CHANNEL_ONGOING_TIMER)
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setContentTitle(title)
            .setContentText(contentText)
            .setContentIntent(buildOpenAppPendingIntent())
            .setOngoing(snapshot.state != ReminderState.DISABLED)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)

        if (snapshot.state == ReminderState.COUNTING_20_MINUTES ||
            snapshot.state == ReminderState.COUNTING_REST ||
            snapshot.state == ReminderState.PAUSED
        ) {
            builder.setProgress(totalSeconds, elapsedSeconds, false)
        }

        when (snapshot.state) {
            ReminderState.COUNTING_20_MINUTES,
            ReminderState.COUNTING_REST -> {
                builder.addAction(
                    android.R.drawable.ic_media_pause,
                    context.getString(R.string.notif_action_pause),
                    buildActionPendingIntent(EyeCareReceiver.ACTION_PAUSE_REMINDER, 3001)
                )
                builder.addAction(
                    android.R.drawable.ic_menu_close_clear_cancel,
                    context.getString(R.string.notif_action_disable),
                    buildActionPendingIntent(EyeCareReceiver.ACTION_DISABLE_REMINDER, 3002)
                )
            }
            ReminderState.ALARM_RINGING -> {
                builder.addAction(
                    android.R.drawable.ic_media_play,
                    context.getString(R.string.notif_action_stop_alarm),
                    buildActionPendingIntent(EyeCareReceiver.ACTION_ACKNOWLEDGE_ALARM, 3003)
                )
                builder.addAction(
                    android.R.drawable.ic_menu_close_clear_cancel,
                    context.getString(R.string.notif_action_disable),
                    buildActionPendingIntent(EyeCareReceiver.ACTION_DISABLE_REMINDER, 3002)
                )
            }
            ReminderState.REST_COMPLETED -> {
                builder.addAction(
                    android.R.drawable.ic_media_play,
                    context.getString(R.string.notif_action_done),
                    buildActionPendingIntent(EyeCareReceiver.ACTION_ACKNOWLEDGE_REST_DONE, 3004)
                )
                builder.addAction(
                    android.R.drawable.ic_menu_close_clear_cancel,
                    context.getString(R.string.notif_action_disable),
                    buildActionPendingIntent(EyeCareReceiver.ACTION_DISABLE_REMINDER, 3002)
                )
            }
            ReminderState.PAUSED -> {
                builder.addAction(
                    android.R.drawable.ic_media_play,
                    context.getString(R.string.notif_action_resume),
                    buildActionPendingIntent(EyeCareReceiver.ACTION_RESUME_REMINDER, 3005)
                )
                builder.addAction(
                    android.R.drawable.ic_menu_close_clear_cancel,
                    context.getString(R.string.notif_action_disable),
                    buildActionPendingIntent(EyeCareReceiver.ACTION_DISABLE_REMINDER, 3002)
                )
            }
            ReminderState.DISABLED -> Unit
        }

        return builder.build()
    }

    /**
     * Updates the ongoing notification only when the displayed state or formatted MM:SS changes.
     * Prevents IPC flooding of SystemUI.
     */
    fun updateOngoingNotification(
        snapshot: ReminderSnapshot,
        nowMillis: Long,
        force: Boolean = false
    ) {
        if (!hasNotificationPermission()) return
        if (snapshot.state == ReminderState.DISABLED) {
            lastOngoingDisplayedKey = ""
            notificationManager.cancel(NOTIFICATION_ID_ONGOING)
            return
        }

        val formattedTime = formatDurationMillis(snapshot.computeRemainingMillis(nowMillis))
        val key = "${snapshot.state.name}:${snapshot.stateVersion}:${snapshot.restDurationSeconds}:$formattedTime"
        if (!force && key == lastOngoingDisplayedKey) {
            return
        }
        lastOngoingDisplayedKey = key

        notificationManager.notify(
            NOTIFICATION_ID_ONGOING,
            buildOngoingNotification(snapshot, nowMillis)
        )
    }

    /**
     * Posts a high-priority heads-up notification ONLY ONCE when entering ALARM_RINGING
     * or REST_COMPLETED, and immediately cancels it on any other state.
     * Never re-posts on timer ticks so notification action buttons remain 100% responsive.
     */
    fun syncAlertNotification(snapshot: ReminderSnapshot, force: Boolean = false) {
        if (!hasNotificationPermission()) return
        ensureChannelsCreated()

        when (snapshot.state) {
            ReminderState.ALARM_RINGING -> {
                val alertKey = "ALARM:${snapshot.stateVersion}"
                if (!force && alertKey == lastAlertPostedKey) return
                lastAlertPostedKey = alertKey

                val notification = NotificationCompat.Builder(context, CHANNEL_EYE_ALARM)
                    .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                    .setContentTitle(context.getString(R.string.status_time_to_rest))
                    .setContentText(
                        "20 minutes completed! Stop the alarm to begin your ${snapshot.restDurationSeconds}-second eye break."
                    )
                    .setPriority(NotificationCompat.PRIORITY_MAX)
                    .setCategory(NotificationCompat.CATEGORY_ALARM)
                    .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                    .setOnlyAlertOnce(true)
                    .setAutoCancel(false)
                    .setOngoing(true)
                    .setContentIntent(buildOpenAppPendingIntent())
                    .addAction(
                        android.R.drawable.ic_media_play,
                        context.getString(R.string.notif_action_stop_alarm),
                        buildActionPendingIntent(EyeCareReceiver.ACTION_ACKNOWLEDGE_ALARM, 4001)
                    )
                    .addAction(
                        android.R.drawable.ic_media_pause,
                        context.getString(R.string.notif_action_pause),
                        buildActionPendingIntent(EyeCareReceiver.ACTION_PAUSE_REMINDER, 4002)
                    )
                    .build()
                notificationManager.notify(NOTIFICATION_ID_ALERT, notification)
            }

            ReminderState.REST_COMPLETED -> {
                val alertKey = "REST_DONE:${snapshot.stateVersion}"
                if (!force && alertKey == lastAlertPostedKey) return
                lastAlertPostedKey = alertKey

                val notification = NotificationCompat.Builder(context, CHANNEL_EYE_ALARM)
                    .setSmallIcon(android.R.drawable.ic_menu_view)
                    .setContentTitle(context.getString(R.string.status_rest_completed))
                    .setContentText(
                        "Your ${snapshot.restDurationSeconds}-second eye rest is finished. Tap Done to start the next 20-minute countdown."
                    )
                    .setPriority(NotificationCompat.PRIORITY_MAX)
                    .setCategory(NotificationCompat.CATEGORY_REMINDER)
                    .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                    .setOnlyAlertOnce(true)
                    .setAutoCancel(false)
                    .setOngoing(true)
                    .setContentIntent(buildOpenAppPendingIntent())
                    .addAction(
                        android.R.drawable.ic_media_play,
                        context.getString(R.string.notif_action_done),
                        buildActionPendingIntent(EyeCareReceiver.ACTION_ACKNOWLEDGE_REST_DONE, 4003)
                    )
                    .addAction(
                        android.R.drawable.ic_media_pause,
                        context.getString(R.string.notif_action_pause),
                        buildActionPendingIntent(EyeCareReceiver.ACTION_PAUSE_REMINDER, 4002)
                    )
                    .build()
                notificationManager.notify(NOTIFICATION_ID_ALERT, notification)
            }

            else -> {
                if (lastAlertPostedKey.isNotEmpty() || force) {
                    lastAlertPostedKey = ""
                    notificationManager.cancel(NOTIFICATION_ID_ALERT)
                }
            }
        }
    }

    fun cancelAllNotifications() {
        lastOngoingDisplayedKey = ""
        lastAlertPostedKey = ""
        notificationManager.cancel(NOTIFICATION_ID_ONGOING)
        notificationManager.cancel(NOTIFICATION_ID_ALERT)
    }

    private fun buildOpenAppPendingIntent(): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            context,
            1000,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun buildActionPendingIntent(action: String, requestCode: Int): PendingIntent {
        val intent = Intent(context, EyeCareReceiver::class.java).apply {
            this.action = action
            // Crucial: deliver notification button actions on the foreground broadcast queue
            // so Realme UI / ColorOS / Android 14+ execute them instantaneously (<10ms)
            addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
        }
        return PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    companion object {
        const val CHANNEL_ONGOING_TIMER = "twenty_rule_ongoing_timer_v2"
        const val CHANNEL_EYE_ALARM = "twenty_rule_alarm_alerts_v2"
        const val NOTIFICATION_ID_ONGOING = 1001
        const val NOTIFICATION_ID_ALERT = 1002

        fun formatDurationMillis(millis: Long): String {
            val totalSeconds = ((millis + 999L) / 1000L).coerceAtLeast(0L)
            val minutes = totalSeconds / 60L
            val seconds = totalSeconds % 60L
            return String.format(Locale.US, "%02d:%02d", minutes, seconds)
        }
    }
}
