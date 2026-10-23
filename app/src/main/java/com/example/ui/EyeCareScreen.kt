package com.example.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.VolumeOff
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.filled.AlarmOn
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.RemoveRedEye
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.model.ReminderSnapshot
import com.example.model.ReminderState
import com.example.ui.components.BotanicalHorizonBanner
import com.example.ui.components.EyeCareTimerDial
import com.example.ui.theme.StateAlarmAmber
import com.example.ui.theme.StateCompletedMint

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EyeCareScreen(
    uiState: EyeCareUiState,
    isDarkTheme: Boolean,
    onToggleDarkTheme: () -> Unit,
    onStartReminder: () -> Unit,
    onPauseReminder: () -> Unit,
    onResumeReminder: () -> Unit,
    onDisableReminder: () -> Unit,
    onAcknowledgeAlarm: () -> Unit,
    onAcknowledgeRestCompleted: () -> Unit,
    onSelectRestDuration: (Int) -> Unit,
    onToggleSoundEnabled: (Boolean) -> Unit,
    onRequestNotificationPermission: () -> Unit,
    onOpenExactAlarmSettings: () -> Unit,
    onOpenBatteryOptimizationSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.size(38.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.RemoveRedEye,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(8.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = stringResource(R.string.app_name),
                                style = MaterialTheme.typography.titleLarge
                            )
                            Text(
                                text = stringResource(R.string.app_subtitle),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                actions = {
                    IconButton(
                        onClick = onToggleDarkTheme,
                        modifier = Modifier.testTag("theme_toggle_button")
                    ) {
                        Icon(
                            imageVector = if (isDarkTheme) Icons.Filled.LightMode else Icons.Filled.DarkMode,
                            contentDescription = if (isDarkTheme) "Switch to Light Theme" else "Switch to Dark Theme"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        }
    ) { innerPadding ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            val isExpandedScreen = maxWidth >= 720.dp
            val scrollState = rememberScrollState()

            if (isExpandedScreen) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(scrollState)
                        .padding(horizontal = 24.dp, vertical = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(24.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(18.dp)
                    ) {
                        EyeCareTimerDial(
                            snapshot = uiState.snapshot,
                            formattedCountdown = uiState.formattedCountdown,
                            progressFraction = uiState.progressFraction
                        )
                        EyeCarePrimaryControls(
                            state = uiState.snapshot.state,
                            restDurationSeconds = uiState.snapshot.restDurationSeconds,
                            onStartReminder = onStartReminder,
                            onPauseReminder = onPauseReminder,
                            onResumeReminder = onResumeReminder,
                            onDisableReminder = onDisableReminder,
                            onAcknowledgeAlarm = onAcknowledgeAlarm,
                            onAcknowledgeRestCompleted = onAcknowledgeRestCompleted
                        )
                        BotanicalHorizonBanner(
                            state = uiState.snapshot.state,
                            completedCyclesCount = uiState.snapshot.completedCyclesCount
                        )
                    }

                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        EyeCareMinimalSettingsCard(
                            restDurationSeconds = uiState.snapshot.restDurationSeconds,
                            soundEnabled = uiState.snapshot.soundEnabled,
                            onSelectRestDuration = onSelectRestDuration,
                            onToggleSoundEnabled = onToggleSoundEnabled
                        )
                        EyeCarePermissionsAndReliabilityCard(
                            hasNotificationPermission = uiState.hasNotificationPermission,
                            canScheduleExactAlarms = uiState.canScheduleExactAlarms,
                            isBatteryOptimizationIgnored = uiState.isBatteryOptimizationIgnored,
                            onRequestNotificationPermission = onRequestNotificationPermission,
                            onOpenExactAlarmSettings = onOpenExactAlarmSettings,
                            onOpenBatteryOptimizationSettings = onOpenBatteryOptimizationSettings
                        )
                    }
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(scrollState)
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(18.dp)
                ) {
                    Column(
                        modifier = Modifier.widthIn(max = 560.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(18.dp)
                    ) {
                        EyeCareTimerDial(
                            snapshot = uiState.snapshot,
                            formattedCountdown = uiState.formattedCountdown,
                            progressFraction = uiState.progressFraction
                        )

                        EyeCarePrimaryControls(
                            state = uiState.snapshot.state,
                            restDurationSeconds = uiState.snapshot.restDurationSeconds,
                            onStartReminder = onStartReminder,
                            onPauseReminder = onPauseReminder,
                            onResumeReminder = onResumeReminder,
                            onDisableReminder = onDisableReminder,
                            onAcknowledgeAlarm = onAcknowledgeAlarm,
                            onAcknowledgeRestCompleted = onAcknowledgeRestCompleted
                        )

                        BotanicalHorizonBanner(
                            state = uiState.snapshot.state,
                            completedCyclesCount = uiState.snapshot.completedCyclesCount
                        )

                        EyeCareMinimalSettingsCard(
                            restDurationSeconds = uiState.snapshot.restDurationSeconds,
                            soundEnabled = uiState.snapshot.soundEnabled,
                            onSelectRestDuration = onSelectRestDuration,
                            onToggleSoundEnabled = onToggleSoundEnabled
                        )

                        EyeCarePermissionsAndReliabilityCard(
                            hasNotificationPermission = uiState.hasNotificationPermission,
                            canScheduleExactAlarms = uiState.canScheduleExactAlarms,
                            isBatteryOptimizationIgnored = uiState.isBatteryOptimizationIgnored,
                            onRequestNotificationPermission = onRequestNotificationPermission,
                            onOpenExactAlarmSettings = onOpenExactAlarmSettings,
                            onOpenBatteryOptimizationSettings = onOpenBatteryOptimizationSettings
                        )

                        Spacer(modifier = Modifier.height(12.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun EyeCarePrimaryControls(
    state: ReminderState,
    restDurationSeconds: Int,
    onStartReminder: () -> Unit,
    onPauseReminder: () -> Unit,
    onResumeReminder: () -> Unit,
    onDisableReminder: () -> Unit,
    onAcknowledgeAlarm: () -> Unit,
    onAcknowledgeRestCompleted: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        when (state) {
            ReminderState.DISABLED -> {
                Button(
                    onClick = onStartReminder,
                    shape = RoundedCornerShape(18.dp),
                    contentPadding = PaddingValues(vertical = 16.dp, horizontal = 24.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp)
                        .testTag("start_reminder_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = stringResource(R.string.action_enable_reminder),
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }

            ReminderState.ALARM_RINGING -> {
                Button(
                    onClick = onAcknowledgeAlarm,
                    shape = RoundedCornerShape(18.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = StateAlarmAmber,
                        contentColor = MaterialTheme.colorScheme.surface
                    ),
                    contentPadding = PaddingValues(vertical = 16.dp, horizontal = 24.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 58.dp)
                        .testTag("acknowledge_alarm_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Stop Alarm & Start ${restDurationSeconds}s Rest",
                        style = MaterialTheme.typography.titleMedium
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    FilledTonalButton(
                        onClick = onPauseReminder,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 50.dp)
                            .testTag("pause_reminder_button")
                    ) {
                        Icon(imageVector = Icons.Filled.Pause, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = stringResource(R.string.action_pause_reminder))
                    }

                    OutlinedButton(
                        onClick = onDisableReminder,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 50.dp)
                            .testTag("disable_reminder_button")
                    ) {
                        Icon(imageVector = Icons.Filled.Stop, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = stringResource(R.string.action_disable_reminder))
                    }
                }
            }

            ReminderState.REST_COMPLETED -> {
                Button(
                    onClick = onAcknowledgeRestCompleted,
                    shape = RoundedCornerShape(18.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = StateCompletedMint,
                        contentColor = MaterialTheme.colorScheme.surface
                    ),
                    contentPadding = PaddingValues(vertical = 16.dp, horizontal = 24.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 58.dp)
                        .testTag("acknowledge_rest_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.CheckCircle,
                        contentDescription = null,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = stringResource(R.string.action_complete_rest_continue),
                        style = MaterialTheme.typography.titleMedium
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    FilledTonalButton(
                        onClick = onPauseReminder,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 50.dp)
                            .testTag("pause_reminder_button")
                    ) {
                        Icon(imageVector = Icons.Filled.Pause, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = stringResource(R.string.action_pause_reminder))
                    }

                    OutlinedButton(
                        onClick = onDisableReminder,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 50.dp)
                            .testTag("disable_reminder_button")
                    ) {
                        Icon(imageVector = Icons.Filled.Stop, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = stringResource(R.string.action_disable_reminder))
                    }
                }
            }

            ReminderState.COUNTING_20_MINUTES,
            ReminderState.COUNTING_REST -> {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    FilledTonalButton(
                        onClick = onPauseReminder,
                        shape = RoundedCornerShape(18.dp),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 54.dp)
                            .testTag("pause_reminder_button")
                    ) {
                        Icon(imageVector = Icons.Filled.Pause, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.action_pause_reminder),
                            style = MaterialTheme.typography.titleMedium
                        )
                    }

                    OutlinedButton(
                        onClick = onDisableReminder,
                        shape = RoundedCornerShape(18.dp),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 54.dp)
                            .testTag("disable_reminder_button")
                    ) {
                        Icon(imageVector = Icons.Filled.Stop, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.action_disable_reminder),
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                }
            }

            ReminderState.PAUSED -> {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = onResumeReminder,
                        shape = RoundedCornerShape(18.dp),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 54.dp)
                            .testTag("resume_reminder_button")
                    ) {
                        Icon(imageVector = Icons.Filled.PlayArrow, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.action_resume_reminder),
                            style = MaterialTheme.typography.titleMedium
                        )
                    }

                    OutlinedButton(
                        onClick = onDisableReminder,
                        shape = RoundedCornerShape(18.dp),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 54.dp)
                            .testTag("disable_reminder_button")
                    ) {
                        Icon(imageVector = Icons.Filled.Stop, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.action_disable_reminder),
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun EyeCareMinimalSettingsCard(
    restDurationSeconds: Int,
    soundEnabled: Boolean,
    onSelectRestDuration: (Int) -> Unit,
    onToggleSoundEnabled: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("minimal_settings_card"),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = stringResource(R.string.settings_section_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )

            // 1. Eye-Rest Duration Selector (20s default vs 30s)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Timer,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = stringResource(R.string.settings_rest_duration_label),
                        style = MaterialTheme.typography.titleMedium
                    )
                }
                Text(
                    text = stringResource(R.string.settings_rest_duration_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    RestDurationChip(
                        label = stringResource(R.string.settings_rest_20s),
                        selected = restDurationSeconds == ReminderSnapshot.DEFAULT_REST_SECONDS,
                        onClick = { onSelectRestDuration(ReminderSnapshot.DEFAULT_REST_SECONDS) },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("rest_duration_20_button")
                    )
                    RestDurationChip(
                        label = stringResource(R.string.settings_rest_30s),
                        selected = restDurationSeconds == ReminderSnapshot.ALT_REST_SECONDS,
                        onClick = { onSelectRestDuration(ReminderSnapshot.ALT_REST_SECONDS) },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("rest_duration_30_button")
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)

            // 2. Alarm & Completion Sound Toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = if (soundEnabled) {
                            Icons.AutoMirrored.Outlined.VolumeUp
                        } else {
                            Icons.AutoMirrored.Outlined.VolumeOff
                        },
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                    Column {
                        Text(
                            text = stringResource(R.string.settings_sound_label),
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = stringResource(R.string.settings_sound_desc),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Switch(
                    checked = soundEnabled,
                    onCheckedChange = onToggleSoundEnabled,
                    modifier = Modifier.testTag("sound_toggle_switch")
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)

            // 3. Fixed 20-Minute Interval Info
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.settings_primary_interval_label),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "20:00 min (Fixed)",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun RestDurationChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        modifier = modifier.heightIn(min = 48.dp),
        shape = RoundedCornerShape(14.dp),
        color = if (selected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        },
        border = BorderStroke(
            width = if (selected) 2.dp else 1.dp,
            color = if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                color = if (selected) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
        }
    }
}

@Composable
fun EyeCarePermissionsAndReliabilityCard(
    hasNotificationPermission: Boolean,
    canScheduleExactAlarms: Boolean,
    isBatteryOptimizationIgnored: Boolean,
    onRequestNotificationPermission: () -> Unit,
    onOpenExactAlarmSettings: () -> Unit,
    onOpenBatteryOptimizationSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showOsDetails by rememberSaveable { mutableStateOf(false) }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("permissions_reliability_card"),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = stringResource(R.string.permissions_section_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )

            PermissionStatusRow(
                icon = Icons.Filled.Notifications,
                title = stringResource(R.string.permission_notifications_title),
                description = if (hasNotificationPermission) {
                    stringResource(R.string.permission_notifications_granted)
                } else {
                    stringResource(R.string.permission_notifications_missing)
                },
                isGranted = hasNotificationPermission,
                actionLabel = stringResource(R.string.action_grant),
                onActionClick = onRequestNotificationPermission,
                actionTestTag = "grant_notification_permission_button"
            )

            PermissionStatusRow(
                icon = Icons.Filled.AlarmOn,
                title = stringResource(R.string.permission_exact_alarm_title),
                description = if (canScheduleExactAlarms) {
                    stringResource(R.string.permission_exact_alarm_granted)
                } else {
                    stringResource(R.string.permission_exact_alarm_missing)
                },
                isGranted = canScheduleExactAlarms,
                actionLabel = stringResource(R.string.action_configure),
                onActionClick = onOpenExactAlarmSettings,
                actionTestTag = "configure_exact_alarm_button"
            )

            PermissionStatusRow(
                icon = Icons.Filled.BatteryChargingFull,
                title = stringResource(R.string.permission_battery_title),
                description = if (isBatteryOptimizationIgnored) {
                    stringResource(R.string.permission_battery_granted)
                } else {
                    stringResource(R.string.permission_battery_missing)
                },
                isGranted = isBatteryOptimizationIgnored,
                actionLabel = stringResource(R.string.action_configure),
                onActionClick = onOpenBatteryOptimizationSettings,
                actionTestTag = "configure_battery_button"
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)

            // Expandable OS limitations & background behavior explanation
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .clickable { showOsDetails = !showOsDetails }
                    .padding(vertical = 4.dp)
                    .testTag("os_limitations_toggle_row"),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = stringResource(R.string.os_limitations_title),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Icon(
                    imageVector = if (showOsDetails) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = if (showOsDetails) "Collapse" else "Expand",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            AnimatedVisibility(
                visible = showOsDetails,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Text(
                    text = stringResource(R.string.os_limitations_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag("os_limitations_body_text")
                )
            }
        }
    }
}

@Composable
private fun PermissionStatusRow(
    icon: ImageVector,
    title: String,
    description: String,
    isGranted: Boolean,
    actionLabel: String,
    onActionClick: () -> Unit,
    actionTestTag: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isGranted) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.tertiary
                },
                modifier = Modifier
                    .padding(top = 2.dp)
                    .size(20.dp)
            )
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (!isGranted) {
            Spacer(modifier = Modifier.width(10.dp))
            FilledTonalButton(
                onClick = onActionClick,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag(actionTestTag)
            ) {
                Text(text = actionLabel)
            }
        }
    }
}
