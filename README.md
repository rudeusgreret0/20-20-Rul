# 20-20 Eye Care — Automatic 20-20-20 Android Reminder

A native Android wellness utility built with **Kotlin** and **Jetpack Compose (Material 3)** that automatically reminds you to rest your eyes following the **20-20-20 rule**: every **20 minutes** of active phone use, look at something **20 feet (6 meters)** away for **20 seconds** (or **30 seconds**).

---

## Direct APK Download

1. **Direct Repository Download (Immediate):**
   - The compiled, signed APK is included directly in this repository at **[`releases/20-20-Eye-Care-v1.0.apk`](releases/20-20-Eye-Care-v1.0.apk)**.
   - Click on `releases/20-20-Eye-Care-v1.0.apk` in GitHub and tap **Download raw file** (or **View raw**) to download and install the APK immediately on your Android phone.
2. **GitHub Releases Download:**
   - The included GitHub Actions workflow ([`.github/workflows/android-release.yml`](.github/workflows/android-release.yml)) also publishes **`20-20-Eye-Care-v1.0.apk`** directly to **GitHub Releases (`v1.0.0`)** under the repository's **Releases** tab.

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

## Local Build & Test Instructions

### Prerequisites
- **JDK 21** (`temurin-21`)
- **Gradle 9.3.1**
- **Android SDK (API 36.1)**

### Build Debug APK
```bash
gradle :app:assembleDebug
```
The compiled APK is generated at `app/build/outputs/apk/debug/app-debug.apk` (and pre-packaged at `releases/20-20-Eye-Care-v1.0.apk`).

### Run Automated Unit & Robolectric Tests
```bash
gradle :app:testDebugUnitTest
```
