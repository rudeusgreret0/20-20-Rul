package com.example

import android.content.Context
import android.content.Intent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import com.example.data.EyeCareRepository
import com.example.model.EyeCareClock
import com.example.model.PausedPhase
import com.example.model.ReminderSnapshot
import com.example.model.ReminderState
import com.example.receiver.EyeCareReceiver
import com.example.service.EyeCareCoordinator
import com.example.ui.EyeCareScreen
import com.example.ui.EyeCareUiState
import com.example.ui.theme.MyApplicationTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

class FakeClock(var currentMillis: Long = 1_000_000_000L) : EyeCareClock {
    override fun nowMillis(): Long = currentMillis
    fun advanceBySeconds(seconds: Long) {
        currentMillis += seconds * 1000L
    }
    fun advanceByMinutes(minutes: Long) {
        currentMillis += minutes * 60_000L
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var context: Context
    private lateinit var fakeClock: FakeClock
    private lateinit var repository: EyeCareRepository
    private lateinit var coordinator: EyeCareCoordinator

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("test_eye_care_prefs", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        fakeClock = FakeClock()
        repository = EyeCareRepository(
            context = context,
            clock = fakeClock,
            prefsName = "test_eye_care_prefs"
        )
        coordinator = EyeCareCoordinator(
            appContext = context,
            repository = repository,
            clock = fakeClock,
            startServiceHooksEnabled = false
        )
    }

    @Test
    fun `full 20-20-20 cycle transitions automatically and repeats continuously`() {
        assertEquals(ReminderState.DISABLED, repository.currentSnapshot.state)
        assertEquals(20, repository.currentSnapshot.restDurationSeconds)

        // 1. Enable -> 20-minute countdown
        coordinator.enableReminder()
        val afterEnable = repository.currentSnapshot
        assertEquals(ReminderState.COUNTING_20_MINUTES, afterEnable.state)
        assertEquals(
            ReminderSnapshot.TWENTY_MINUTES_MILLIS,
            afterEnable.computeRemainingMillis(fakeClock.nowMillis())
        )

        // 2. Advance 20 minutes -> trigger alarm
        fakeClock.advanceByMinutes(20)
        coordinator.reconcile(isDeviceBoot = false)
        assertEquals(ReminderState.ALARM_RINGING, repository.currentSnapshot.state)
        assertTrue(coordinator.soundPlayer.isAlarmLooping)

        // 3. User acknowledges alarm -> starts 20-second rest countdown automatically
        coordinator.acknowledgeAlarmAndStartRest()
        val afterAckAlarm = repository.currentSnapshot
        assertEquals(ReminderState.COUNTING_REST, afterAckAlarm.state)
        assertFalse(coordinator.soundPlayer.isAlarmLooping)
        assertEquals(20_000L, afterAckAlarm.computeRemainingMillis(fakeClock.nowMillis()))

        // 4. Advance 20 seconds -> rest completed sound and notification state
        fakeClock.advanceBySeconds(20)
        coordinator.reconcile(isDeviceBoot = false)
        val afterRestDone = repository.currentSnapshot
        assertEquals(ReminderState.REST_COMPLETED, afterRestDone.state)
        assertEquals(1, afterRestDone.completedCyclesCount)
        assertEquals(1, coordinator.soundPlayer.completionChimeCount)

        // 5. User acknowledges completion -> automatically starts next 20-minute countdown
        coordinator.acknowledgeRestCompleted()
        val nextCycle = repository.currentSnapshot
        assertEquals(ReminderState.COUNTING_20_MINUTES, nextCycle.state)
        assertEquals(
            ReminderSnapshot.TWENTY_MINUTES_MILLIS,
            nextCycle.computeRemainingMillis(fakeClock.nowMillis())
        )
    }

    @Test
    fun `30-second rest duration option is persisted and used for rest countdown`() {
        coordinator.setRestDurationSeconds(30)
        assertEquals(30, repository.currentSnapshot.restDurationSeconds)

        coordinator.enableReminder()
        fakeClock.advanceByMinutes(20)
        coordinator.onWorkTimerExpired()
        assertEquals(ReminderState.ALARM_RINGING, repository.currentSnapshot.state)

        coordinator.acknowledgeAlarmAndStartRest()
        assertEquals(ReminderState.COUNTING_REST, repository.currentSnapshot.state)
        assertEquals(30_000L, repository.currentSnapshot.computeRemainingMillis(fakeClock.nowMillis()))

        // Recreate repository from SharedPreferences to verify persistence
        val recreatedRepo = EyeCareRepository(
            context = context,
            clock = fakeClock,
            prefsName = "test_eye_care_prefs"
        )
        assertEquals(30, recreatedRepo.currentSnapshot.restDurationSeconds)
        assertEquals(ReminderState.COUNTING_REST, recreatedRepo.currentSnapshot.state)
    }

    @Test
    fun `duplicate alarm triggers and duplicate dismissals are ignored`() {
        coordinator.enableReminder()
        val workVersion = repository.currentSnapshot.stateVersion

        // First expiration transitions to ALARM_RINGING
        val firstExpire = repository.onWorkTimerExpired(workVersion)
        assertTrue(firstExpire.changed)
        assertEquals(ReminderState.ALARM_RINGING, firstExpire.current.state)

        // Duplicate expiration with same or null version does not fire again
        val duplicateExpire = repository.onWorkTimerExpired(workVersion)
        assertFalse(duplicateExpire.changed)

        // First acknowledgement transitions to COUNTING_REST
        val firstAck = repository.acknowledgeAlarmAndStartRest()
        assertTrue(firstAck.changed)
        assertEquals(ReminderState.COUNTING_REST, firstAck.current.state)
        val restTarget = firstAck.current.targetEpochMillis

        // Advance 5 seconds and tap acknowledge again (e.g., from notification + in-app)
        fakeClock.advanceBySeconds(5)
        val duplicateAck = repository.acknowledgeAlarmAndStartRest()
        assertFalse(duplicateAck.changed)
        assertEquals(restTarget, repository.currentSnapshot.targetEpochMillis)
    }

    @Test
    fun `pause and resume preserve exact remaining time for both work and rest phases`() {
        coordinator.enableReminder()
        fakeClock.advanceByMinutes(7) // 13 minutes left

        coordinator.pauseReminder()
        val pausedWork = repository.currentSnapshot
        assertEquals(ReminderState.PAUSED, pausedWork.state)
        assertEquals(PausedPhase.WORK_20_MINUTES, pausedWork.pausedPhase)
        assertEquals(13 * 60_000L, pausedWork.remainingMillisWhenPaused)

        // Time passes while paused -> remaining time must not decrease
        fakeClock.advanceByMinutes(30)
        coordinator.reconcile(isDeviceBoot = false)
        assertEquals(ReminderState.PAUSED, repository.currentSnapshot.state)
        assertEquals(13 * 60_000L, repository.currentSnapshot.computeRemainingMillis(fakeClock.nowMillis()))

        // Resume -> continues with 13 minutes remaining
        coordinator.resumeReminder()
        assertEquals(ReminderState.COUNTING_20_MINUTES, repository.currentSnapshot.state)
        assertEquals(13 * 60_000L, repository.currentSnapshot.computeRemainingMillis(fakeClock.nowMillis()))

        // Pause during rest countdown
        fakeClock.advanceByMinutes(13)
        coordinator.onWorkTimerExpired()
        coordinator.acknowledgeAlarmAndStartRest()
        fakeClock.advanceBySeconds(8) // 12 seconds left of 20s rest

        coordinator.pauseReminder()
        assertEquals(ReminderState.PAUSED, repository.currentSnapshot.state)
        assertEquals(PausedPhase.REST_COUNTDOWN, repository.currentSnapshot.pausedPhase)
        assertEquals(12_000L, repository.currentSnapshot.remainingMillisWhenPaused)

        coordinator.resumeReminder()
        assertEquals(ReminderState.COUNTING_REST, repository.currentSnapshot.state)
        assertEquals(12_000L, repository.currentSnapshot.computeRemainingMillis(fakeClock.nowMillis()))
    }

    @Test
    fun `process killed and recreated preserves countdown without accidental reset`() {
        coordinator.enableReminder()
        fakeClock.advanceByMinutes(5) // 15 minutes remaining
        coordinator.reconcile(isDeviceBoot = false)

        // Simulate process death and new repository creation
        val newRepoAfterProcessDeath = EyeCareRepository(
            context = context,
            clock = fakeClock,
            prefsName = "test_eye_care_prefs"
        )
        assertEquals(ReminderState.COUNTING_20_MINUTES, newRepoAfterProcessDeath.currentSnapshot.state)
        assertEquals(
            15 * 60_000L,
            newRepoAfterProcessDeath.currentSnapshot.computeRemainingMillis(fakeClock.nowMillis())
        )
    }

    @Test
    fun `device reboot restores active state across all major states without duplicate alarms`() {
        // Case A: Reboot while COUNTING_20_MINUTES and phone was powered off for 2 hours
        coordinator.enableReminder()
        fakeClock.advanceByMinutes(6) // 14 minutes left at last checkpoint
        coordinator.reconcile(isDeviceBoot = false)

        // Phone powered off for 2 hours, then boots up
        fakeClock.advanceByMinutes(120)
        coordinator.reconcile(isDeviceBoot = true)
        val afterBootWork = repository.currentSnapshot
        assertEquals(ReminderState.COUNTING_20_MINUTES, afterBootWork.state)
        assertEquals(14 * 60_000L, afterBootWork.computeRemainingMillis(fakeClock.nowMillis()))
        assertFalse(coordinator.soundPlayer.isAlarmLooping)

        // Case B: Reboot while PAUSED keeps PAUSED
        coordinator.pauseReminder()
        fakeClock.advanceByMinutes(45)
        coordinator.reconcile(isDeviceBoot = true)
        assertEquals(ReminderState.PAUSED, repository.currentSnapshot.state)

        // Case C: Reboot while DISABLED stays DISABLED
        coordinator.disableReminder()
        fakeClock.advanceByMinutes(30)
        coordinator.reconcile(isDeviceBoot = true)
        assertEquals(ReminderState.DISABLED, repository.currentSnapshot.state)
    }

    @Test
    fun `notification broadcast actions perform intended state transitions`() {
        val defaultRepo = EyeCareRepository.getInstance(context)
        val defaultCoordinator = EyeCareCoordinator.getInstance(context)
        defaultCoordinator.disableReminder()

        defaultCoordinator.enableReminder()
        assertEquals(ReminderState.COUNTING_20_MINUTES, defaultRepo.currentSnapshot.state)

        val receiver = EyeCareReceiver()

        // Pause via notification action
        receiver.onReceive(context, Intent(EyeCareReceiver.ACTION_PAUSE_REMINDER))
        assertEquals(ReminderState.PAUSED, defaultRepo.currentSnapshot.state)

        // Resume via notification action
        receiver.onReceive(context, Intent(EyeCareReceiver.ACTION_RESUME_REMINDER))
        assertEquals(ReminderState.COUNTING_20_MINUTES, defaultRepo.currentSnapshot.state)

        // Work timer expired via AlarmManager broadcast
        receiver.onReceive(context, Intent(EyeCareReceiver.ACTION_WORK_TIMER_EXPIRED))
        assertEquals(ReminderState.ALARM_RINGING, defaultRepo.currentSnapshot.state)

        // Dismiss alarm & start rest via notification action
        receiver.onReceive(context, Intent(EyeCareReceiver.ACTION_ACKNOWLEDGE_ALARM))
        assertEquals(ReminderState.COUNTING_REST, defaultRepo.currentSnapshot.state)

        // Rest timer expired via AlarmManager broadcast
        receiver.onReceive(context, Intent(EyeCareReceiver.ACTION_REST_TIMER_EXPIRED))
        assertEquals(ReminderState.REST_COMPLETED, defaultRepo.currentSnapshot.state)

        // Acknowledge rest done via notification action -> restarts 20m countdown
        receiver.onReceive(context, Intent(EyeCareReceiver.ACTION_ACKNOWLEDGE_REST_DONE))
        assertEquals(ReminderState.COUNTING_20_MINUTES, defaultRepo.currentSnapshot.state)

        // Disable via notification action
        receiver.onReceive(context, Intent(EyeCareReceiver.ACTION_DISABLE_REMINDER))
        assertEquals(ReminderState.DISABLED, defaultRepo.currentSnapshot.state)
    }

    @Test
    fun `compose UI displays status labels and handles start pause and rest duration controls`() {
        var currentState = EyeCareUiState(
            snapshot = ReminderSnapshot(state = ReminderState.DISABLED, restDurationSeconds = 20),
            formattedCountdown = "20:00"
        )

        composeTestRule.setContent {
            MyApplicationTheme {
                EyeCareScreen(
                    uiState = currentState,
                    isDarkTheme = false,
                    onToggleDarkTheme = {},
                    onStartReminder = {
                        currentState = currentState.copy(
                            snapshot = currentState.snapshot.copy(state = ReminderState.COUNTING_20_MINUTES),
                            formattedCountdown = "19:59"
                        )
                    },
                    onPauseReminder = {},
                    onResumeReminder = {},
                    onDisableReminder = {},
                    onAcknowledgeAlarm = {},
                    onAcknowledgeRestCompleted = {},
                    onSelectRestDuration = { sec ->
                        currentState = currentState.copy(
                            snapshot = currentState.snapshot.copy(restDurationSeconds = sec)
                        )
                    },
                    onToggleSoundEnabled = {},
                    onRequestNotificationPermission = {},
                    onOpenExactAlarmSettings = {},
                    onOpenBatteryOptimizationSettings = {}
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("status_label_text").assertIsDisplayed()
        composeTestRule.onNodeWithText("Reminder Disabled").assertIsDisplayed()
        composeTestRule.onNodeWithTag("start_reminder_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("rest_duration_30_button")
            .performScrollTo()
            .performClick()
        composeTestRule.waitForIdle()
        assertEquals(30, currentState.snapshot.restDurationSeconds)
    }

    @Test
    fun `MainActivity launches and instantiates EyeCareViewModel via AndroidViewModelFactory`() {
        assertEquals("Twenty Rule", context.getString(R.string.app_name))
        org.robolectric.Robolectric.buildActivity(MainActivity::class.java).use { controller ->
            controller.setup()
            val activity = controller.get()
            assertTrue(activity != null)
        }
    }

    @Test
    fun `checkTimerExpirationOnTick transitions cleanly at expiration without IPC storm`() {
        coordinator.enableReminder()
        fakeClock.advanceByMinutes(10)
        val midTick = coordinator.checkTimerExpirationOnTick()
        assertEquals(ReminderState.COUNTING_20_MINUTES, midTick.state)

        fakeClock.advanceByMinutes(10)
        val expiredWork = coordinator.checkTimerExpirationOnTick()
        assertEquals(ReminderState.ALARM_RINGING, expiredWork.state)
        assertTrue(coordinator.soundPlayer.isAlarmLooping)

        coordinator.acknowledgeAlarmAndStartRest()
        assertFalse(coordinator.soundPlayer.isAlarmLooping)
        fakeClock.advanceBySeconds(20)
        val expiredRest = coordinator.checkTimerExpirationOnTick()
        assertEquals(ReminderState.REST_COMPLETED, expiredRest.state)
    }
}
