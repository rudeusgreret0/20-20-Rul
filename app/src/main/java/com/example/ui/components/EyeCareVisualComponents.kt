package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PauseCircleFilled
import androidx.compose.material.icons.filled.RemoveRedEye
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.model.PausedPhase
import com.example.model.ReminderSnapshot
import com.example.model.ReminderState
import com.example.ui.theme.StateActiveEmerald
import com.example.ui.theme.StateAlarmAmber
import com.example.ui.theme.StateCompletedMint
import com.example.ui.theme.StatePausedSlate
import com.example.ui.theme.StateRestSkyTeal
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Central circular progress timer dial with calm wellness micro-interactions,
 * precision tick marks, and state-responsive colors.
 */
@Composable
fun EyeCareTimerDial(
    snapshot: ReminderSnapshot,
    formattedCountdown: String,
    progressFraction: Float,
    modifier: Modifier = Modifier
) {
    val accentColor by animateColorAsState(
        targetValue = when (snapshot.state) {
            ReminderState.DISABLED -> MaterialTheme.colorScheme.outline
            ReminderState.COUNTING_20_MINUTES -> StateActiveEmerald
            ReminderState.ALARM_RINGING -> StateAlarmAmber
            ReminderState.COUNTING_REST -> StateRestSkyTeal
            ReminderState.REST_COMPLETED -> StateCompletedMint
            ReminderState.PAUSED -> StatePausedSlate
        },
        animationSpec = tween(durationMillis = 350),
        label = "dialAccentColor"
    )

    val animatedProgress by animateFloatAsState(
        targetValue = progressFraction.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
        label = "dialProgress"
    )

    val infiniteTransition = rememberInfiniteTransition(label = "wellnessPulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.96f,
        targetValue = 1.04f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    val statusText = when (snapshot.state) {
        ReminderState.DISABLED -> stringResource(R.string.status_disabled)
        ReminderState.COUNTING_20_MINUTES -> stringResource(R.string.status_next_eye_break)
        ReminderState.ALARM_RINGING -> stringResource(R.string.status_time_to_rest)
        ReminderState.COUNTING_REST -> stringResource(R.string.status_resting_now)
        ReminderState.REST_COMPLETED -> stringResource(R.string.status_rest_completed)
        ReminderState.PAUSED -> stringResource(R.string.status_reminder_paused)
    }

    val statusIcon = when (snapshot.state) {
        ReminderState.DISABLED -> Icons.Outlined.VisibilityOff
        ReminderState.COUNTING_20_MINUTES -> Icons.Filled.Schedule
        ReminderState.ALARM_RINGING -> Icons.Filled.NotificationsActive
        ReminderState.COUNTING_REST -> Icons.Filled.RemoveRedEye
        ReminderState.REST_COMPLETED -> Icons.Filled.CheckCircle
        ReminderState.PAUSED -> Icons.Filled.PauseCircleFilled
    }

    val phaseSubtitle = when (snapshot.state) {
        ReminderState.DISABLED -> "20m Work  •  ${snapshot.restDurationSeconds}s Rest"
        ReminderState.COUNTING_20_MINUTES -> "20-Minute Screen Cycle"
        ReminderState.ALARM_RINGING -> "Acknowledge to start ${snapshot.restDurationSeconds}s rest"
        ReminderState.COUNTING_REST -> "Look 20 feet away (${snapshot.restDurationSeconds}s break)"
        ReminderState.REST_COMPLETED -> "Ready for next 20-minute cycle"
        ReminderState.PAUSED -> {
            if (snapshot.pausedPhase == PausedPhase.WORK_20_MINUTES) {
                "Paused • 20-Minute Cycle"
            } else {
                "Paused • ${snapshot.restDurationSeconds}s Eye Rest"
            }
        }
    }

    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    val tickColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)

    Box(
        modifier = modifier
            .size(276.dp)
            .testTag("central_timer_dial"),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val canvasMin = min(size.width, size.height)
            val strokeWidth = 14.dp.toPx()
            val radius = (canvasMin / 2f) - strokeWidth * 1.4f
            val centerOffset = Offset(size.width / 2f, size.height / 2f)

            // Soft ambient aura glow when active or ringing
            if (snapshot.state != ReminderState.DISABLED) {
                val auraRadius = radius * (if (snapshot.state == ReminderState.COUNTING_REST || snapshot.state == ReminderState.ALARM_RINGING) pulseScale else 1.0f)
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            accentColor.copy(alpha = 0.16f),
                            accentColor.copy(alpha = 0.04f),
                            Color.Transparent
                        ),
                        center = centerOffset,
                        radius = auraRadius * 1.15f
                    ),
                    radius = auraRadius * 1.15f,
                    center = centerOffset
                )
            }

            // 60 subtle radial tick marks around the perimeter
            val outerTickRadius = radius + strokeWidth * 0.95f
            val innerTickRadiusMajor = radius + strokeWidth * 0.55f
            val innerTickRadiusMinor = radius + strokeWidth * 0.70f
            for (i in 0 until 60) {
                val angleRad = Math.toRadians((i * 6.0) - 90.0)
                val isMajor = i % 5 == 0
                val startR = if (isMajor) innerTickRadiusMajor else innerTickRadiusMinor
                val startX = centerOffset.x + (startR * cos(angleRad)).toFloat()
                val startY = centerOffset.y + (startR * sin(angleRad)).toFloat()
                val endX = centerOffset.x + (outerTickRadius * cos(angleRad)).toFloat()
                val endY = centerOffset.y + (outerTickRadius * sin(angleRad)).toFloat()
                drawLine(
                    color = if (isMajor) accentColor.copy(alpha = 0.55f) else tickColor,
                    start = Offset(startX, startY),
                    end = Offset(endX, endY),
                    strokeWidth = if (isMajor) 2.2.dp.toPx() else 1.2.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }

            // Background circular track
            drawCircle(
                color = trackColor,
                radius = radius,
                center = centerOffset,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )

            // Foreground progress arc
            val sweepAngle = 360f * animatedProgress
            if (sweepAngle > 0.5f) {
                drawArc(
                    color = accentColor,
                    startAngle = -90f,
                    sweepAngle = sweepAngle,
                    useCenter = false,
                    topLeft = Offset(centerOffset.x - radius, centerOffset.y - radius),
                    size = Size(radius * 2f, radius * 2f),
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                )
            }
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(horizontal = 28.dp)
        ) {
            Surface(
                color = accentColor.copy(alpha = 0.14f),
                shape = CircleShape
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                ) {
                    Icon(
                        imageVector = statusIcon,
                        contentDescription = statusText,
                        tint = accentColor,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.labelLarge,
                        color = accentColor,
                        modifier = Modifier.testTag("status_label_text")
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = formattedCountdown,
                style = MaterialTheme.typography.displayLarge,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                modifier = Modifier.testTag("countdown_timer_text")
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = phaseSubtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.testTag("phase_subtitle_text")
            )
        }
    }
}

/**
 * Calm botanical horizon illustration card that gives the user a soothing visual cue
 * for the 20-20-20 rule (every 20 minutes, look 20 feet away for 20–30 seconds).
 */
@Composable
fun BotanicalHorizonBanner(
    state: ReminderState,
    completedCyclesCount: Int,
    modifier: Modifier = Modifier
) {
    val guidanceText = when (state) {
        ReminderState.DISABLED -> stringResource(R.string.guidance_disabled)
        ReminderState.COUNTING_20_MINUTES -> stringResource(R.string.guidance_counting_20_min)
        ReminderState.ALARM_RINGING -> stringResource(R.string.guidance_alarm_ringing)
        ReminderState.COUNTING_REST -> stringResource(R.string.guidance_counting_rest)
        ReminderState.REST_COMPLETED -> stringResource(R.string.guidance_rest_completed)
        ReminderState.PAUSED -> stringResource(R.string.guidance_paused)
    }

    val skyTop = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.75f)
    val skyBottom = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f)
    val ridgeFar = MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)
    val ridgeNear = MaterialTheme.colorScheme.primary.copy(alpha = 0.38f)
    val sunColor = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.65f)

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .testTag("wellness_horizon_card"),
        shape = RoundedCornerShape(22.dp),
        tonalElevation = 2.dp
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(skyTop, skyBottom)
                    )
                )
        ) {
            // Subtle custom Canvas landscape of distant misty hills (20-foot visual horizon)
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(108.dp)
            ) {
                val w = size.width
                val h = size.height

                // Warm distant sun
                drawCircle(
                    color = sunColor,
                    radius = h * 0.22f,
                    center = Offset(w * 0.82f, h * 0.36f)
                )

                // Far misty mountain ridge
                val farPath = Path().apply {
                    moveTo(0f, h)
                    lineTo(0f, h * 0.62f)
                    cubicTo(
                        w * 0.25f, h * 0.42f,
                        w * 0.55f, h * 0.72f,
                        w, h * 0.48f
                    )
                    lineTo(w, h)
                    close()
                }
                drawPath(path = farPath, color = ridgeFar)

                // Near botanical hill silhouette
                val nearPath = Path().apply {
                    moveTo(0f, h)
                    lineTo(0f, h * 0.78f)
                    cubicTo(
                        w * 0.35f, h * 0.60f,
                        w * 0.70f, h * 0.88f,
                        w, h * 0.68f
                    )
                    lineTo(w, h)
                    close()
                }
                drawPath(path = nearPath, color = ridgeNear)
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "20 • 20 • 20 RULE",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Surface(
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.82f),
                        shape = RoundedCornerShape(50)
                    ) {
                        Text(
                            text = "Completed Rests: $completedCyclesCount",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                                .testTag("completed_cycles_badge")
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = guidanceText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.testTag("guidance_banner_text")
                )
            }
        }
    }
}
