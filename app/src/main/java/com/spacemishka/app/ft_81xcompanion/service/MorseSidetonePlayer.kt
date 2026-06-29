package com.spacemishka.app.ft_81xcompanion.service

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Build
import android.util.Log

class MorseSidetonePlayer(private val context: Context) {
    companion object {
        private const val TAG = "MorseSidetonePlayer"
        private const val SAMPLE_RATE = 44100
    }

    private val lock = Any()
    private var audioTrack: AudioTrack? = null
    private var currentFrequencyHz = 800.0

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var focusRequest: android.media.AudioFocusRequest? = null
    private val audioFocusChangeListener = AudioManager.OnAudioFocusChangeListener { }

    /**
     * Updates the sidetone frequency. Reinitializes the track if the frequency changes and is already loaded.
     */
    fun setFrequency(frequencyHz: Double) {
        synchronized(lock) {
            if (frequencyHz != currentFrequencyHz) {
                currentFrequencyHz = frequencyHz
                if (audioTrack != null) {
                    initAudioTrack()
                }
            }
        }
    }

    /**
     * Initializes the static AudioTrack with a looping multi-cycle sine wave.
     * Fits integer cycles near a target size to prevent pitch errors and phase clicks.
     */
    private fun initAudioTrack() {
        try {
            audioTrack?.release()
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing old AudioTrack", e)
        }

        val cycleLength = SAMPLE_RATE / currentFrequencyHz
        val numCycles = kotlin.math.max(1, (1000.0 / cycleLength).toInt())
        val cycleSamples = (cycleLength * numCycles).toInt()

        val buffer = ShortArray(cycleSamples)
        
        // Generate a pure sine wave. We scale the amplitude to 0.4 (40%) to keep it comfortable.
        val amplitude = (Short.MAX_VALUE * 0.4).toInt()
        for (i in 0 until cycleSamples) {
            val angle = 2.0 * Math.PI * i / cycleLength
            buffer[i] = (Math.sin(angle) * amplitude).toInt().toShort()
        }

        try {
            audioTrack = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(SAMPLE_RATE)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(cycleSamples * 2)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build().apply {
                    write(buffer, 0, cycleSamples)
                    setLoopPoints(0, cycleSamples, -1) // loop infinitely
                }
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing AudioTrack", e)
        }
    }

    /**
     * Starts playing the sidetone.
     */
    fun start() {
        synchronized(lock) {
            requestAudioFocus()
            if (audioTrack == null) {
                initAudioTrack()
            }
            try {
                audioTrack?.play()
            } catch (e: Exception) {
                Log.e(TAG, "Error playing sidetone", e)
            }
        }
    }

    /**
     * Stops playing the sidetone and resets playback position.
     */
    fun stop() {
        synchronized(lock) {
            try {
                audioTrack?.pause()
                audioTrack?.reloadStaticData()
            } catch (e: Exception) {
                Log.e(TAG, "Error stopping sidetone", e)
            }
            abandonAudioFocus()
        }
    }

    /**
     * Releases system resources.
     */
    fun release() {
        synchronized(lock) {
            try {
                audioTrack?.release()
            } catch (e: Exception) {
                Log.e(TAG, "Error releasing AudioTrack", e)
            }
            audioTrack = null
            abandonAudioFocus()
        }
    }

    private fun requestAudioFocus() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (focusRequest == null) {
                    val playbackAttributes = AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                    focusRequest = android.media.AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                        .setAudioAttributes(playbackAttributes)
                        .setAcceptsDelayedFocusGain(true)
                        .setOnAudioFocusChangeListener(audioFocusChangeListener)
                        .build()
                }
                focusRequest?.let { audioManager.requestAudioFocus(it) }
            } else {
                @Suppress("DEPRECATION")
                audioManager.requestAudioFocus(
                    audioFocusChangeListener,
                    AudioManager.STREAM_MUSIC,
                    AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to request audio focus", e)
        }
    }

    private fun abandonAudioFocus() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                focusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
            } else {
                @Suppress("DEPRECATION")
                audioManager.abandonAudioFocus(audioFocusChangeListener)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to abandon audio focus", e)
        }
    }
}
