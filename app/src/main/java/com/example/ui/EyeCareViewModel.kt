package com.example.ui

import android.app.Application
import android.content.Context
import android.os.Build
import android.os.PowerManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.model.ReminderSnapshot
import com.example.model.ReminderState
import com.example.service.EyeCareCoordinator
import com.example.service.EyeCareNotificationHelper
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class EyeCareUiState(
    val snapshot: ReminderSnapshot = ReminderSnapshot(),
    val remainingMillis: Long = ReminderSnapshot.TWENTY_MINUTES_MILLIS,
    val formattedCountdown: String = "20:00",
    val progressFraction: Float = 0f,
    val hasNotificationPermission: Boolean = true,
    val canScheduleExactAlarms: Boolean = true,
    val isBatteryOptimizationIgnored: Boolean = true
)

class EyeCareViewModel @JvmOverloads constructor(
    application: Application,
    private val coordinator: EyeCareCoordinator = EyeCareCoordinator.getInstance(application)
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(buildUiState(coordinator.repository.currentSnapshot))
    val uiState: StateFlow<EyeCareUiState> = _uiState.asStateFlow()

    init {
        coordinator.reconcile(isDeviceBoot = false)

        viewModelScope.launch {
            coordinator.snapshotFlow.collect { snapshot ->
                _uiState.value = buildUiState(snapshot)
            }
        }

        // Lightweight in-memory UI ticker (200ms) that never blocks on IPC
        viewModelScope.launch {
            while (isActive) {
                val current = coordinator.repository.currentSnapshot
                if (current.state == ReminderState.COUNTING_20_MINUTES ||
                    current.state == ReminderState.COUNTING_REST
                ) {
                    coordinator.checkTimerExpirationOnTick()
                }
                _uiState.update { buildUiState(coordinator.repository.currentSnapshot) }
                delay(200L)
            }
        }
    }

    fun refreshPermissionsAndReconcile() {
        coordinator.reconcile(isDeviceBoot = false)
        _uiState.update { buildUiState(coordinator.repository.currentSnapshot) }
    }

    fun onStartReminderClicked() {
        coordinator.enableReminder()
        _uiState.update { buildUiState(coordinator.repository.currentSnapshot) }
    }

    fun onPauseReminderClicked() {
        coordinator.pauseReminder()
        _uiState.update { buildUiState(coordinator.repository.currentSnapshot) }
    }

    fun onResumeReminderClicked() {
        coordinator.resumeReminder()
        _uiState.update { buildUiState(coordinator.repository.currentSnapshot) }
    }

    fun onDisableReminderClicked() {
        coordinator.disableReminder()
        _uiState.update { buildUiState(coordinator.repository.currentSnapshot) }
    }

    fun onAcknowledgeAlarmClicked() {
        coordinator.acknowledgeAlarmAndStartRest()
        _uiState.update { buildUiState(coordinator.repository.currentSnapshot) }
    }

    fun onAcknowledgeRestCompletedClicked() {
        coordinator.acknowledgeRestCompleted()
        _uiState.update { buildUiState(coordinator.repository.currentSnapshot) }
    }

    fun onSelectRestDuration(seconds: Int) {
        coordinator.setRestDurationSeconds(seconds)
        _uiState.update { buildUiState(coordinator.repository.currentSnapshot) }
    }

    fun onToggleSoundEnabled(enabled: Boolean) {
        coordinator.setSoundEnabled(enabled)
        _uiState.update { buildUiState(coordinator.repository.currentSnapshot) }
    }

    fun onPreviewAlarmChimeClicked() {
        coordinator.soundPlayer.previewAlarmChime()
    }

    fun onPreviewRestCompleteChimeClicked() {
        coordinator.soundPlayer.previewRestCompleteChime()
    }

    private fun buildUiState(snapshot: ReminderSnapshot): EyeCareUiState {
        val now = System.currentTimeMillis()
        val remaining = snapshot.computeRemainingMillis(now)
        val progress = snapshot.computeProgressFraction(now)
        val appContext = getApplication<Application>()
        val powerManager = appContext.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val batteryIgnored = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            powerManager?.isIgnoringBatteryOptimizations(appContext.packageName) ?: true
        } else {
            true
        }

        return EyeCareUiState(
            snapshot = snapshot,
            remainingMillis = remaining,
            formattedCountdown = EyeCareNotificationHelper.formatDurationMillis(remaining),
            progressFraction = progress,
            hasNotificationPermission = coordinator.notificationHelper.hasNotificationPermission(),
            canScheduleExactAlarms = coordinator.alarmScheduler.canScheduleExactAlarms(),
            isBatteryOptimizationIgnored = batteryIgnored
        )
    }
}
