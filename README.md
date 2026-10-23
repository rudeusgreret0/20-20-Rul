# 20-20 Eye Care — Automatic 20-20-20 Android Reminder

A native Android wellness utility built with **Kotlin** and **Jetpack Compose (Material 3)** that automatically reminds you to rest your eyes following the **20-20-20 rule**: every **20 minutes** of active phone use, look at something **20 feet (6 meters)** away for **20 seconds** (or **30 seconds**).

---

## Direct APK Download & Project Export

### Option 1: Download Directly from AI Studio (Immediate)
You can download the compiled APK or full source ZIP directly from the AI Studio interface right now:
1. Click the **Settings / Export (Gear or Download)** menu in the top-right corner of Google AI Studio.
2. Select **Download APK** to get the ready-to-install Android package, or **Export to ZIP** to download the full Android Studio project.

### Option 2: Push to GitHub & Automatic GitHub Releases
This repository includes a pre-configured **GitHub Actions CI/CD workflow** ([`.github/workflows/android-release.yml`](.github/workflows/android-release.yml)) that automatically builds `20-20-Eye-Care-v1.0.apk`, uploads it as a build artifact, and publishes a **GitHub Release (`v1.0.0`)** on every push to `main`/`master`:
- **Latest Release Page:** `https://github.com/<YOUR_GITHUB_USERNAME>/<YOUR_REPO_NAME>/releases/latest`
- **Direct APK Link (once pushed):** `https://github.com/<YOUR_GITHUB_USERNAME>/<YOUR_REPO_NAME>/releases/latest/download/20-20-Eye-Care-v1.0.apk`

---

## Core Cycle & State Machine

The reminder system implements a strict, persistent 6-state machine (`ReminderState`):

```text
DISABLED
   │ (User taps Start Reminder)
   ▼
COUNTING_20_MINUTES ◄──────────────────────────────────────┐
   │ (20:00 elapses → audible bell chime + notification)   │
   ▼                                                       │
ALARM_RINGING                                              │
   │ (User taps "Stop Alarm & Rest" in app or shade)       │
   ▼                                                       │
COUNTING_REST (20s default or 30s option)                  │
   │ (Rest timer finishes → completion chime + alert)      │
   ▼                                                       │
REST_COMPLETED ────────────────────────────────────────────┘
     (User taps "Done — Start Next 20m" in app or shade)
```

* **`PAUSED` State:** Can be entered from any active state, preserving the exact remaining time and phase (`WORK_20_MINUTES` or `REST_COUNTDOWN`) until resumed.
* **Duplicate-Event Prevention:** Every state transition increments a monotonic `stateVersion` token persisted in `SharedPreferences`. Simultaneous triggers from the Foreground Service ticker, `AlarmManager`, or double-taps are safely deduplicated so transitions occur **exactly once**.

---

## Background Operation & Lifecycle Reliability

* **Ongoing Foreground Service (`EyeCareForegroundService`):** Runs as a `specialUse` foreground service (`START_STICKY`) with a live countdown notification so the timer stays active when the app is minimized or swiped away from Recent Apps.
* **System Wake-Up Alarms (`EyeCareAlarmScheduler`):** Schedules `RTC_WAKEUP` alarms via `AlarmManager.setExactAndAllowWhileIdle` (with automatic fallback to `setAndAllowWhileIdle` if exact alarm permission is not granted) so transitions occur even when the screen is off or locked.
* **Automatic Reboot Recovery (`EyeCareReceiver`):** Listens for `BOOT_COMPLETED`, `QUICKBOOT_POWERON`, and `MY_PACKAGE_REPLACED` to automatically restore active reminders after the phone is restarted without firing stale alarms or skipping cycles.

---

## Project Structure

```text
├── .github/workflows/
│   └── android-release.yml                # Automated CI test, APK build & GitHub Release publisher
├── app/
│   ├── build.gradle.kts                   # App module Gradle configuration
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml        # Permissions, ForegroundService & BroadcastReceiver
│       │   ├── java/com/example/
│       │   │   ├── MainActivity.kt        # Edge-to-edge Activity & runtime permission launchers
│       │   │   ├── data/
│       │   │   │   └── EyeCareRepository.kt        # Persistent state machine & boot reconciliation
│       │   │   ├── model/
│       │   │   │   └── ReminderModels.kt           # ReminderState, PausedPhase, ReminderSnapshot
│       │   │   ├── receiver/
│       │   │   │   └── EyeCareReceiver.kt          # Boot, AlarmManager & notification action receiver
│       │   │   ├── service/
│       │   │   │   ├── EyeCareAlarmScheduler.kt    # Exact & fallback RTC_WAKEUP alarm scheduling
│       │   │   │   ├── EyeCareCoordinator.kt       # Atomic state + service + audio + alarm orchestrator
│       │   │   │   ├── EyeCareForegroundService.kt # Sticky foreground service & 1s background ticker
│       │   │   │   ├── EyeCareNotificationHelper.kt# Ongoing & heads-up actionable notifications
│       │   │   │   └── EyeCareSoundPlayer.kt       # Synthesized harmonic PCM bell chimes & haptics
│       │   │   └── ui/
│       │   │       ├── EyeCareScreen.kt            # Responsive M3 wellness interface
│       │   │       ├── EyeCareViewModel.kt         # Lifecycle-aware UI state holder
│       │   │       ├── components/
│       │   │       │   └── EyeCareVisualComponents.kt # Custom Canvas dial & botanical horizon card
│       │   │       └── theme/                      # Botanical Sage Light & Nocturnal Pine Dark themes
│       │   └── res/                                # Bundled Google Fonts, adaptive icons & strings
│       └── test/java/com/example/
│           └── ExampleRobolectricTest.kt  # Automated JVM & Robolectric test suite (10 tests)
├── build.gradle.kts
├── gradle/libs.versions.toml              # Version catalog
└── settings.gradle.kts
```

---

## Local Build & Test Instructions

### Prerequisites
- **JDK 17+**
- **Android SDK (API 36)**

### Build Debug APK
```bash
gradle :app:assembleDebug
```
The compiled APK will be generated at:
`app/build/outputs/apk/debug/app-debug.apk`

### Run Automated Unit & Robolectric Tests
```bash
gradle :app:testDebugUnitTest
```
