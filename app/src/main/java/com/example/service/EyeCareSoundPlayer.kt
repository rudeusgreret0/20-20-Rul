package com.example.service

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/**
 * Generates clear, pleasant, wellness-inspired harmonic chimes using synthesized PCM audio
 * and gentle haptic feedback. Works reliably across all Android devices and emulators
 * without depending on external audio files.
 */
class EyeCareSoundPlayer(private val context: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var alarmJob: Job? = null
    private var activeAudioTrack: AudioTrack? = null

    @Volatile
    var isAlarmLooping: Boolean = false
        private set

    @Volatile
    var completionChimeCount: Int = 0
        private set

    /**
     * Starts a gentle, noticeable repeating harmonic alarm chime for the 20-minute break alert.
     */
    @Synchronized
    fun startAlarmLoop(soundEnabled: Boolean) {
        stopAlarmLoop()
        isAlarmLooping = true
        vibratePattern(longArrayOf(0L, 250L, 150L, 250L))

        if (!soundEnabled) return

        alarmJob = scope.launch {
            while (isActive && isAlarmLooping) {
                playSynthesizedSequence(
                    notesHz = doubleArrayOf(523.25, 659.25, 783.99, 1046.50), // C5 - E5 - G5 - C6 warm bell chord arpeggio
                    noteDurationMs = 220,
                    usage = AudioAttributes.USAGE_ALARM
                )
                delay(1800L)
            }
        }
    }

    /**
     * Immediately stops the repeating 20-minute alarm sound.
     */
    @Synchronized
    fun stopAlarmLoop() {
        isAlarmLooping = false
        alarmJob?.cancel()
        alarmJob = null
        runCatching {
            activeAudioTrack?.pause()
            activeAudioTrack?.flush()
            activeAudioTrack?.release()
        }
        activeAudioTrack = null
    }

    /**
     * Plays a short, uplifting completion sound when the 20s/30s eye-rest countdown finishes.
     */
    @Synchronized
    fun playRestCompletionSound(soundEnabled: Boolean) {
        stopAlarmLoop()
        completionChimeCount += 1
        vibratePattern(longArrayOf(0L, 120L, 80L, 180L))

        if (!soundEnabled) return

        scope.launch {
            playSynthesizedSequence(
                notesHz = doubleArrayOf(587.33, 739.99, 880.00), // D5 - F#5 - A5 soothing completion triad
                noteDurationMs = 180,
                usage = AudioAttributes.USAGE_NOTIFICATION
            )
        }
    }

    private fun playSynthesizedSequence(
        notesHz: DoubleArray,
        noteDurationMs: Int,
        usage: Int
    ) {
        runCatching {
            val sampleRate = 24000
            val samplesPerNote = (sampleRate * noteDurationMs) / 1000
            val totalSamples = samplesPerNote * notesHz.size
            val buffer = ShortArray(totalSamples)

            for (noteIdx in notesHz.indices) {
                val freq = notesHz[noteIdx]
                val offset = noteIdx * samplesPerNote
                for (i in 0 until samplesPerNote) {
                    val t = i.toDouble() / sampleRate
                    // Soft attack and exponential decay envelope for a warm acoustic bell timbre
                    val attack = (i.toDouble() / (sampleRate * 0.015)).coerceAtMost(1.0)
                    val decay = exp(-3.5 * i.toDouble() / samplesPerNote)
                    val fundamental = sin(2.0 * PI * freq * t)
                    val overtone = 0.25 * sin(2.0 * PI * (freq * 2.0) * t)
                    val sampleValue = (fundamental + overtone) * 0.75 * attack * decay
                    buffer[offset + i] = (sampleValue * Short.MAX_VALUE * 0.55).toInt()
                        .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                        .toShort()
                }
            }

            val audioTrack = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(usage)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(buffer.size * 2)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()

            activeAudioTrack = audioTrack
            audioTrack.write(buffer, 0, buffer.size)
            audioTrack.play()
        }
    }

    private fun vibratePattern(pattern: LongArray) {
        runCatching {
            val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                manager?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
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
}
