package com.example.service

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.example.R

/**
 * Plays pleasant, studio-mastered acoustic singing-bowl and marimba chimes
 * bundled in res/raw/ (`twenty_rule_alarm_chime.wav` and `twenty_rule_rest_complete.wav`).
 *
 * Properly manages AudioFocus, AudioAttributes (USAGE_ALARM vs USAGE_NOTIFICATION),
 * MediaPlayer lifecycle, and haptic vibration so alarms start reliably and stop
 * instantaneously when dismissed from the notification shade or in-app controls.
 */
class EyeCareSoundPlayer(private val context: Context) {

    private val audioManager: AudioManager? =
        context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager

    private var alarmMediaPlayer: MediaPlayer? = null
    private var oneshotMediaPlayer: MediaPlayer? = null
    private var currentFocusRequest: AudioFocusRequest? = null

    @Volatile
    var isAlarmLooping: Boolean = false
        private set

    @Volatile
    var completionChimeCount: Int = 0
        private set

    /**
     * Starts the looping 20-minute eye-break acoustic chime and gentle haptic pulse.
     */
    @Synchronized
    fun startAlarmLoop(soundEnabled: Boolean) {
        stopAlarmLoop()
        isAlarmLooping = true
        vibratePattern(longArrayOf(0L, 280L, 180L, 280L))

        if (!soundEnabled) return

        runCatching {
            requestTransientAudioFocus(AudioAttributes.USAGE_ALARM)
            val attrs = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()

            val player = MediaPlayer.create(context, R.raw.twenty_rule_alarm_chime, attrs, 0)
            if (player != null) {
                player.isLooping = true
                player.setVolume(0.90f, 0.90f)
                player.start()
                alarmMediaPlayer = player
            }
        }
    }

    /**
     * Immediately stops the repeating 20-minute alarm chime, cancels active vibration,
     * and releases audio focus.
     */
    @Synchronized
    fun stopAlarmLoop() {
        isAlarmLooping = false
        releaseAlarmPlayer()
        cancelVibration()
        abandonAudioFocus()
    }

    /**
     * Plays the short, uplifting completion chime when the 20s/30s eye-rest countdown finishes.
     */
    @Synchronized
    fun playRestCompletionSound(soundEnabled: Boolean) {
        stopAlarmLoop()
        completionChimeCount += 1
        vibratePattern(longArrayOf(0L, 120L, 90L, 180L))

        if (!soundEnabled) return

        // Respect silent ringer mode for non-alarm completion notifications if device is muted
        val ringerMode = audioManager?.ringerMode ?: AudioManager.RINGER_MODE_NORMAL
        if (ringerMode == AudioManager.RINGER_MODE_SILENT) return

        playOneShotRawResource(
            rawResId = R.raw.twenty_rule_rest_complete,
            usage = AudioAttributes.USAGE_NOTIFICATION
        )
    }

    /**
     * Plays a single preview of the 20-minute alarm chime when the user taps "Test Alarm Chime".
     */
    @Synchronized
    fun previewAlarmChime() {
        if (isAlarmLooping) return
        playOneShotRawResource(
            rawResId = R.raw.twenty_rule_alarm_chime,
            usage = AudioAttributes.USAGE_MEDIA
        )
    }

    /**
     * Plays a single preview of the rest-completion chime when the user taps "Test Rest-Done Chime".
     */
    @Synchronized
    fun previewRestCompleteChime() {
        if (isAlarmLooping) return
        playOneShotRawResource(
            rawResId = R.raw.twenty_rule_rest_complete,
            usage = AudioAttributes.USAGE_MEDIA
        )
    }

    @Synchronized
    private fun playOneShotRawResource(rawResId: Int, usage: Int) {
        releaseOneshotPlayer()
        runCatching {
            requestTransientAudioFocus(usage)
            val attrs = AudioAttributes.Builder()
                .setUsage(usage)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()

            val player = MediaPlayer.create(context, rawResId, attrs, 0)
            if (player != null) {
                player.isLooping = false
                player.setVolume(0.90f, 0.90f)
                player.setOnCompletionListener { mp ->
                    runCatching { mp.release() }
                    synchronized(this@EyeCareSoundPlayer) {
                        if (oneshotMediaPlayer === mp) {
                            oneshotMediaPlayer = null
                        }
                        if (!isAlarmLooping) {
                            abandonAudioFocus()
                        }
                    }
                }
                player.start()
                oneshotMediaPlayer = player
            }
        }
    }

    private fun releaseAlarmPlayer() {
        val player = alarmMediaPlayer
        alarmMediaPlayer = null
        if (player != null) {
            runCatching {
                if (player.isPlaying) {
                    player.stop()
                }
            }
            runCatching { player.release() }
        }
    }

    private fun releaseOneshotPlayer() {
        val player = oneshotMediaPlayer
        oneshotMediaPlayer = null
        if (player != null) {
            runCatching {
                if (player.isPlaying) {
                    player.stop()
                }
            }
            runCatching { player.release() }
        }
    }

    private fun requestTransientAudioFocus(usage: Int) {
        val manager = audioManager ?: return
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val attrs = AudioAttributes.Builder()
                    .setUsage(usage)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
                val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                    .setAudioAttributes(attrs)
                    .setWillPauseWhenDucked(false)
                    .build()
                currentFocusRequest = req
                manager.requestAudioFocus(req)
            } else {
                @Suppress("DEPRECATION")
                manager.requestAudioFocus(
                    null,
                    AudioManager.STREAM_ALARM,
                    AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK
                )
            }
        }
    }

    private fun abandonAudioFocus() {
        val manager = audioManager ?: return
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                currentFocusRequest?.let { manager.abandonAudioFocusRequest(it) }
                currentFocusRequest = null
            } else {
                @Suppress("DEPRECATION")
                manager.abandonAudioFocus(null)
            }
        }
    }

    private fun getVibrator(): Vibrator? {
        return runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                manager?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
        }.getOrNull()
    }

    private fun vibratePattern(pattern: LongArray) {
        runCatching {
            val vibrator = getVibrator()
            if (vibrator?.hasVibrator() == true) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(pattern, -1)
                }
            }
        }
    }

    private fun cancelVibration() {
        runCatching {
            getVibrator()?.cancel()
        }
    }
}
