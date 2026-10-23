# Twenty Rule — Automatic 20-20-20 Android Eye-Care Reminder

**Twenty Rule** is a native Android wellness utility built with **Kotlin** and **Jetpack Compose (Material 3)** that automatically reminds you to rest your eyes following the **20-20-20 rule**: every **20 minutes** of active phone use, look at something **20 feet (6 meters)** away for **20 seconds** (default) or **30 seconds**.

---

## Direct APK Download

1. **Direct Repository APK Download (Immediate):**
   - The compiled, signed APK is included directly in this repository at **[`releases/Twenty-Rule-v1.0.apk`](releases/Twenty-Rule-v1.0.apk)**.
   - Open [`releases/Twenty-Rule-v1.0.apk`](releases/Twenty-Rule-v1.0.apk) on GitHub and click **Download raw file** (or **View raw**) to download and install **Twenty Rule** directly on your Android phone.
2. **GitHub Releases Download:**
   - The included GitHub Actions workflow ([`.github/workflows/android-release.yml`](.github/workflows/android-release.yml)) also publishes **`Twenty-Rule-v1.0.apk`** directly to **GitHub Releases (`v1.0.0`)** under the repository's **Releases** tab:
   - `https://github.com/<YOUR_GITHUB_USERNAME>/<YOUR_REPO_NAME>/releases/latest/download/Twenty-Rule-v1.0.apk`

---

## Core Cycle & State Machine

The reminder system implements a strict, persistent 6-state machine (`ReminderState`):

```text
DISABLED
   │ (User taps Start Reminder)
   ▼
COUNTING_20_MINUTES ◄──────────────────────────────────────┐
   │ (20:00 elapses → acoustic bell chime + notification)  │
   ▼                                                       │
ALARM_RINGING                                              │
   │ (User taps "Stop Alarm & Rest" in app or shade)       │
   ▼                                                       │
COUNTING_REST (20s default or 30s option)                  │
   │ (Rest timer finishes → completion chime + alert)      │
   ▼                                                       │
REST_COMPLETED ────────────────────────────────────────────┘
     (User taps "Done (Start 20m)" in app or shade)
```

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
The compiled APK is generated at `app/build/outputs/apk/debug/app-debug.apk` (and pre-packaged at `releases/Twenty-Rule-v1.0.apk`).

### Run Automated Unit & Robolectric Tests
```bash
gradle :app:testDebugUnitTest
```
