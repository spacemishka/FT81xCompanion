package com.spacemishka.app.ft_81xcompanion.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.spacemishka.app.ft_81xcompanion.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking
import java.util.Locale

data class RadioState(
    val frequencyHz: Long = 14250000L,
    val mode: Byte = CatProtocol.MODE_USB,
    val isPttActive: Boolean = false,
    val sMeter: Int = 0,
    val isSquelchOpen: Boolean = false,
    val txPower: Int = 0,
    val isSwrHigh: Boolean = false,
    val isSplitActive: Boolean = false,
    val isRitActive: Boolean = false,
    val isLocked: Boolean = false,
    val toneMode: Byte = CatProtocol.TONE_MODE_OFF,
    val ctcssFreqHz: Double = 88.5,
    val dcsCode: Int = 23,
    val repeaterOffsetDir: Byte = CatProtocol.RPT_DIR_SIMPLEX,
    val isMorseActive: Boolean = false,
    val morseText: String = "",
    val morseCurrentCharIndex: Int = -1
)

data class ManualKeyEvent(
    val pressed: Boolean,
    val keyRadio: Boolean,
    val playSound: Boolean,
    val sidetoneFreqHz: Float
)

class CatForegroundService : Service() {

    companion object {
        private const val TAG = "CatService"
        private const val CHANNEL_ID = "FT818ND_CAT_Service"
        private const val NOTIFICATION_ID = 818
        private const val DEFAULT_POLL_INTERVAL_MS = 500L
        private const val PTT_TIMEOUT_MS = 180000L // 3 minutes
    }

    private val binder = CatBinder()
    private val catPort = CatPort()
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    
    private var pollingJob: Job? = null
    private var pttTimeoutJob: Job? = null
    private var morseJob: Job? = null
    private val sidetonePlayer by lazy { MorseSidetonePlayer(applicationContext) }
    private val manualKeyChannel = Channel<ManualKeyEvent>(Channel.UNLIMITED)
    private var pollIntervalMs = DEFAULT_POLL_INTERVAL_MS

    private val _radioState = MutableStateFlow(RadioState())
    val radioState: StateFlow<RadioState> = _radioState.asStateFlow()

    val connectionState: StateFlow<ConnectionState> = catPort.connectionState
    val errorMessage: StateFlow<String?> = catPort.errorMessage

    inner class CatBinder : Binder() {
        fun getService(): CatForegroundService = this@CatForegroundService
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service onCreate")
        createNotificationChannel()
        observeConnectionState()
        startManualKeyProcessor()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "Service onStartCommand")
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder {
        Log.d(TAG, "Service onBind")
        return binder
    }

    override fun onDestroy() {
        Log.d(TAG, "Service onDestroy")
        // Ensure transmitter is unkeyed before shutting down
        if (_radioState.value.isPttActive) {
            runBlocking(Dispatchers.IO) {
                catPort.sendCommand(CatProtocol.buildPttOff(), 1)
            }
        }
        sidetonePlayer.release()
        manualKeyChannel.close()
        serviceScope.cancel()
        runBlocking(Dispatchers.IO) {
            catPort.disconnect()
        }
        super.onDestroy()
    }

    // Bluetooth Connection Controls

    fun connectDevice(address: String) {
        serviceScope.launch {
            catPort.connect(this@CatForegroundService, address)
        }
    }

    fun disconnectDevice() {
        serviceScope.launch {
            catPort.disconnect()
        }
    }

    // Radio State Commands

    fun setFrequency(hz: Long) {
        sendCommand(CatProtocol.buildSetFrequency(hz), 1) {
            _radioState.value = _radioState.value.copy(frequencyHz = hz)
        }
    }

    fun setMode(mode: Byte) {
        sendCommand(CatProtocol.buildSetMode(mode), 1) {
            _radioState.value = _radioState.value.copy(mode = mode)
        }
    }

    fun setPtt(on: Boolean) {
        val cmd = if (on) CatProtocol.buildPttOn() else CatProtocol.buildPttOff()
        sendCommand(cmd, 1) {
            _radioState.value = _radioState.value.copy(isPttActive = on)
            triggerHapticFeedback()
            if (on) {
                startPttTimeoutWatcher()
            } else {
                cancelPttTimeoutWatcher()
            }
        }
    }

    fun toggleVfo() {
        sendCommand(CatProtocol.buildToggleVfo(), 1) {
            // Internal VFO tracking is updated on next poll
        }
    }

    fun setSplit(on: Boolean) {
        val cmd = if (on) CatProtocol.buildSplitOn() else CatProtocol.buildSplitOff()
        sendCommand(cmd, 1) {
            _radioState.value = _radioState.value.copy(isSplitActive = on)
        }
    }

    fun setRit(on: Boolean) {
        val cmd = if (on) CatProtocol.buildRitOn() else CatProtocol.buildRitOff()
        sendCommand(cmd, 1) {
            _radioState.value = _radioState.value.copy(isRitActive = on)
        }
    }

    fun setLock(on: Boolean) {
        val cmd = if (on) CatProtocol.buildLockOn() else CatProtocol.buildLockOff()
        sendCommand(cmd, 1) {
            _radioState.value = _radioState.value.copy(isLocked = on)
        }
    }

    fun setRepeaterOffsetDirection(dir: Byte) {
        sendCommand(CatProtocol.buildSetRepeaterOffsetDirection(dir), 1) {
            _radioState.value = _radioState.value.copy(repeaterOffsetDir = dir)
        }
    }

    fun setToneMode(mode: Byte) {
        sendCommand(CatProtocol.buildSetToneMode(mode), 1) {
            _radioState.value = _radioState.value.copy(toneMode = mode)
        }
    }

    fun setCtcssFreq(toneHz: Double) {
        sendCommand(CatProtocol.buildSetCtcssFreq(toneHz), 1) {
            _radioState.value = _radioState.value.copy(ctcssFreqHz = toneHz)
        }
    }

    fun setDcsCode(code: Int) {
        sendCommand(CatProtocol.buildSetDcsCode(code), 1) {
            _radioState.value = _radioState.value.copy(dcsCode = code)
        }
    }

    fun powerOn() {
        sendCommand(CatProtocol.buildPowerOn(), 1)
    }

    fun powerOff() {
        sendCommand(CatProtocol.buildPowerOff(), 1) {
            _radioState.value = RadioState(isPttActive = false)
        }
    }

    // Morse / CW Keyer Controls

    fun transmitMorse(
        text: String,
        wpm: Int,
        farnsworthWpm: Int,
        sidetoneFreqHz: Float,
        keyRadio: Boolean,
        playSound: Boolean
    ) {
        val oldJob = morseJob
        sidetonePlayer.setFrequency(sidetoneFreqHz.toDouble())

        morseJob = serviceScope.launch(Dispatchers.IO) {
            oldJob?.cancelAndJoin()
            val originalPollingState = pollingJob != null
            if (keyRadio) {
                // Pause polling to avoid CAT timing jitter
                stopPolling()
                // Force PTT OFF at the beginning to be safe
                catPort.sendCommand(CatProtocol.buildPttOff(), 1)
            }

            val upperText = text.uppercase(Locale.US)
            _radioState.value = _radioState.value.copy(
                isMorseActive = true,
                morseText = upperText,
                morseCurrentCharIndex = 0
            )

            val ta = 1200L / farnsworthWpm
            val tb = 1200L / wpm
            val ts = if (farnsworthWpm > wpm) {
                maxOf(ta, (60000L / wpm - 31L * ta) / 19L)
            } else {
                ta
            }

            val charSpaceMs = maxOf(ta, 3 * ts - ta)
            val wordSpaceMs = maxOf(ta * 4, 4 * ts)

            try {
                if (playSound) sidetonePlayer.start()
                for (i in upperText.indices) {
                    _radioState.value = _radioState.value.copy(morseCurrentCharIndex = i)
                    val char = upperText[i]
                    if (char == ' ') {
                        // Word space spacing
                        delay(wordSpaceMs)
                        continue
                    }

                    val code = MorseTranslator.getMorse(char)
                    if (code == null) {
                        // Unknown characters generate a small gap
                        delay(ta * 2)
                        continue
                    }

                    for (j in code.indices) {
                        val symbol = code[j]
                        val symbolDuration = if (symbol == '.') ta else ta * 3

                        // Turn on keying
                        if (playSound) sidetonePlayer.playTone()
                        if (keyRadio && connectionState.value == ConnectionState.CONNECTED) {
                            catPort.sendCommand(CatProtocol.buildPttOn(), 1)
                        }

                        // Wait for symbol duration
                        delay(symbolDuration)

                        // Turn off keying
                        if (playSound) sidetonePlayer.pauseTone()
                        if (keyRadio && connectionState.value == ConnectionState.CONNECTED) {
                            catPort.sendCommand(CatProtocol.buildPttOff(), 1)
                        }

                        // Element space is 1 unit
                        delay(ta)
                    }

                    // Character space spacing
                    delay(charSpaceMs)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Morse transmission interrupted", e)
            } finally {
                withContext(NonCancellable) {
                    // Cleanup keying state
                    sidetonePlayer.stop()
                    if (keyRadio) {
                        catPort.sendCommand(CatProtocol.buildPttOff(), 1)
                        if (originalPollingState) {
                            startPolling()
                        }
                    }
                    _radioState.value = _radioState.value.copy(
                        isMorseActive = false,
                        morseText = "",
                        morseCurrentCharIndex = -1
                    )
                }
            }
        }
    }

    fun stopMorse() {
        morseJob?.cancel()
    }

    fun setManualKey(pressed: Boolean, keyRadio: Boolean, playSound: Boolean, sidetoneFreqHz: Float) {
        manualKeyChannel.trySend(ManualKeyEvent(pressed, keyRadio, playSound, sidetoneFreqHz))
    }

    private fun startManualKeyProcessor() {
        serviceScope.launch(Dispatchers.IO) {
            for (event in manualKeyChannel) {
                if (event.pressed) {
                    sidetonePlayer.setFrequency(event.sidetoneFreqHz.toDouble())
                    if (event.playSound) sidetonePlayer.start()
                    if (event.keyRadio && connectionState.value == ConnectionState.CONNECTED) {
                        catPort.sendCommand(CatProtocol.buildPttOn(), 1)
                    }
                } else {
                    if (event.playSound) sidetonePlayer.stop()
                    if (event.keyRadio && connectionState.value == ConnectionState.CONNECTED) {
                        catPort.sendCommand(CatProtocol.buildPttOff(), 1)
                    }
                }
            }
        }
    }

    fun pausePolling() {
        stopPolling()
    }

    fun resumePolling() {
        if (connectionState.value == ConnectionState.CONNECTED) {
            startPolling()
        }
    }

    fun setPollInterval(intervalMs: Long) {
        pollIntervalMs = intervalMs
        if (pollingJob != null && connectionState.value == ConnectionState.CONNECTED) {
            startPolling(forceRestart = true)
        }
    }

    // Low-Level Command Helper

    private fun sendCommand(cmd: ByteArray, expectedLen: Int, onSuccess: (() -> Unit)? = null) {
        serviceScope.launch {
            val response = catPort.sendCommand(cmd, expectedLen)
            if (response != null && response.isNotEmpty()) {
                val ack = response[0]
                if (ack == 0x00.toByte()) {
                    onSuccess?.invoke()
                }
            }
        }
    }

    // Polling Loop and Connection Monitoring

    private fun observeConnectionState() {
        serviceScope.launch {
            connectionState.collect { state ->
                when (state) {
                    ConnectionState.CONNECTING, ConnectionState.CONNECTED -> {
                        startForegroundServiceNotification()
                    }
                    ConnectionState.DISCONNECTED, ConnectionState.ERROR -> {
                        stopForeground(STOP_FOREGROUND_REMOVE)
                    }
                }
                updateNotification()
                if (state == ConnectionState.CONNECTED) {
                    startPolling()
                } else {
                    stopPolling()
                    if (state == ConnectionState.ERROR || state == ConnectionState.DISCONNECTED) {
                        cancelPttTimeoutWatcher()
                        _radioState.value = _radioState.value.copy(isPttActive = false)
                    }
                }
            }
        }
    }

    private fun startPolling(forceRestart: Boolean = false) {
        if (!forceRestart && pollingJob?.isActive == true) return
        pollingJob?.cancel()
        pollingJob = serviceScope.launch(Dispatchers.IO) {
            while (true) {
                pollRadioState()
                delay(pollIntervalMs)
            }
        }
    }

    private fun stopPolling() {
        pollingJob?.cancel()
        pollingJob = null
    }

    private suspend fun pollRadioState() {
        // 1. Read Frequency and Mode
        val freqBytes = catPort.sendCommand(CatProtocol.buildReadFrequencyAndMode(), 5)
        if (freqBytes != null && freqBytes.size == 5) {
            val freq = CatProtocol.bcdToFrequency(freqBytes)
            val mode = freqBytes[4]
            
            // 2. Read RX or TX Status
            val isPtt = _radioState.value.isPttActive
            val statusByte = if (isPtt) {
                catPort.sendCommand(CatProtocol.buildReadTxStatus(), 1)
            } else {
                catPort.sendCommand(CatProtocol.buildReadRxStatus(), 1)
            }

            var sMeter = 0
            var sqOpen = false
            var txPower = 0
            var swrHigh = false
            var split = false

            if (statusByte != null && statusByte.isNotEmpty()) {
                val b = statusByte[0].toInt() and 0xFF
                if (isPtt) {
                    txPower = b and 0x0F
                    swrHigh = (b and 0x40) != 0
                    split = (b and 0x20) != 0
                } else {
                    sMeter = b and 0x0F
                    sqOpen = (b and 0x80) != 0
                }
            }

            // Update state flow
            _radioState.value = _radioState.value.copy(
                frequencyHz = freq,
                mode = mode,
                sMeter = sMeter,
                isSquelchOpen = sqOpen,
                txPower = txPower,
                isSwrHigh = swrHigh,
                isSplitActive = if (isPtt) split else _radioState.value.isSplitActive
            )

            // Update UI/Notification asynchronously on main thread
            serviceScope.launch {
                updateNotification()
            }
        }
    }

    // PTT Safety Watcher

    private fun startPttTimeoutWatcher() {
        pttTimeoutJob?.cancel()
        pttTimeoutJob = serviceScope.launch {
            delay(PTT_TIMEOUT_MS)
            Log.w(TAG, "PTT Safety Timeout reached! De-keying transmitter.")
            setPtt(false)
        }
    }

    private fun cancelPttTimeoutWatcher() {
        pttTimeoutJob?.cancel()
        pttTimeoutJob = null
    }

    // Haptic Feedback Helper

    private fun triggerHapticFeedback() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vibratorManager.defaultVibrator.vibrate(android.os.VibrationEffect.createOneShot(50, android.os.VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                @Suppress("DEPRECATION")
                vibrator.vibrate(50)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to trigger haptic feedback", e)
        }
    }

    // Notification Management

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Yaesu FT-818ND CAT Controller Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps the CAT interface connected in the background."
                setShowBadge(false)
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun startForegroundServiceNotification() {
        val notification = buildServiceNotification("Connecting...", "Establishing link to transceiver")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun updateNotification() {
        val state = connectionState.value
        val radio = _radioState.value
        val title: String
        val content: String

        when (state) {
            ConnectionState.CONNECTED -> {
                title = if (radio.isPttActive) "TX: ${formatFrequency(radio.frequencyHz)}" else "RX: ${formatFrequency(radio.frequencyHz)}"
                content = "Mode: ${CatProtocol.formatMode(radio.mode)} | S-Meter: S${radio.sMeter}"
            }
            ConnectionState.CONNECTING -> {
                title = "Connecting..."
                content = "Establishing link to transceiver"
            }
            ConnectionState.ERROR -> {
                title = "Connection Error"
                content = errorMessage.value ?: "An error occurred"
            }
            ConnectionState.DISCONNECTED -> {
                title = "Disconnected"
                content = "No radio connected"
            }
        }

        if (state == ConnectionState.CONNECTED || state == ConnectionState.CONNECTING) {
            val notification = buildServiceNotification(title, content)
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.notify(NOTIFICATION_ID, notification)
        }
    }

    private fun buildServiceNotification(title: String, content: String): Notification {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(content)
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth) // Use default system icon for simplicity
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()
    }

    private fun formatFrequency(hz: Long): String {
        val mhz = hz / 1_000_000.0
        return String.format(Locale.US, "%.4f MHz", mhz)
    }

}
