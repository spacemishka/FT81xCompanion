package com.spacemishka.app.ft_81xcompanion.network

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.Socket
import java.util.Locale
import java.util.regex.Pattern

data class DxSpot(
    val spotter: String,
    val frequencyKhz: Double,
    val dxCall: String,
    val comment: String,
    val time: String,
    var distanceKm: Double? = null,
    var bearing: Double? = null,
    var direction: String? = null
)

class DxClusterClient(private val scope: CoroutineScope) {

    companion object {
        private const val TAG = "DxCluster"
        private const val DEFAULT_SERVER = "dxc.nc7j.com"
        private const val DEFAULT_PORT = 7373
        private const val DEFAULT_CALLSIGN = "NOCALL"
        
        // Typical DX Spot format:
        // DX de K1USN-4:   14074.0  UA3DX        FT8 +05dB                   1823Z
        // Group 1: Spotter, Group 2: Freq (kHz), Group 3: DX Call, Group 4: Comment, Group 5: Time (UTC)
        private val SPOT_REGEX = Pattern.compile(
            "DX\\s+de\\s+([A-Z0-9\\-/]+):?\\s+([0-9.]+)\\s+([A-Z0-9\\-/]+)\\s+(.*?)\\s+([0-9]{4}Z)",
            Pattern.CASE_INSENSITIVE
        )
    }

    private val _spots = MutableSharedFlow<DxSpot>(replay = 20)
    val spots: SharedFlow<DxSpot> = _spots.asSharedFlow()

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private var socket: Socket? = null
    private var writer: OutputStream? = null
    private var reader: BufferedReader? = null
    private var connectionJob: Job? = null
    
    private var activeServer = DEFAULT_SERVER
    private var activePort = DEFAULT_PORT
    private var userCallsign = DEFAULT_CALLSIGN

    fun connect(server: String = DEFAULT_SERVER, port: Int = DEFAULT_PORT, callsign: String = DEFAULT_CALLSIGN) {
        activeServer = server
        activePort = port
        userCallsign = callsign.uppercase(Locale.US).trim()
        
        if (userCallsign.isEmpty()) {
            userCallsign = DEFAULT_CALLSIGN
        }

        disconnect()

        connectionJob = scope.launch(Dispatchers.IO) {
            var retries = 0
            while (connectionJob?.isActive == true) {
                try {
                    Log.d(TAG, "Connecting to DX Cluster $activeServer:$activePort...")
                    val sock = Socket(activeServer, activePort)
                    socket = sock
                    writer = sock.getOutputStream()
                    reader = BufferedReader(InputStreamReader(sock.getInputStream()))
                    
                    _isConnected.value = true
                    retries = 0
                    Log.d(TAG, "Connected to DX Cluster. Starting read loop.")

                    readLoop()
                } catch (e: Exception) {
                    Log.e(TAG, "DX Cluster connection error", e)
                    _isConnected.value = false
                    cleanup()
                    
                    // Exponential backoff up to 30 seconds
                    val waitSec = minOf(30, 2 + (1 shl retries))
                    Log.d(TAG, "Reconnecting in $waitSec seconds...")
                    delay(waitSec * 1000L)
                    retries++
                }
            }
        }
    }

    fun disconnect() {
        connectionJob?.cancel()
        connectionJob = null
        scope.launch(Dispatchers.IO) {
            cleanup()
            _isConnected.value = false
        }
    }

    private fun cleanup() {
        try {
            writer?.close()
        } catch (ignored: Exception) {}
        try {
            reader?.close()
        } catch (ignored: Exception) {}
        try {
            socket?.close()
        } catch (ignored: Exception) {}
        writer = null
        reader = null
        socket = null
    }

    private suspend fun readLoop() = withContext(Dispatchers.IO) {
        val currentReader = reader ?: return@withContext
        val currentWriter = writer ?: return@withContext
        
        val buffer = CharArray(1024)
        var textBuffer = ""

        while (connectionJob?.isActive == true) {
            val read = currentReader.read(buffer)
            if (read == -1) {
                Log.w(TAG, "DX Cluster end of stream reached")
                throw Exception("Connection closed by server")
            }

            val chunk = String(buffer, 0, read)
            textBuffer += chunk

            // Check if we are prompted for a login/callsign
            if (textBuffer.lowercase(Locale.US).contains("login:") || 
                textBuffer.lowercase(Locale.US).contains("callsign:")) {
                Log.d(TAG, "Sending login callsign: $userCallsign")
                currentWriter.write("$userCallsign\r\n".toByteArray())
                currentWriter.flush()
                textBuffer = "" // clear to prevent repeating
            }

            // Parse complete lines
            while (textBuffer.contains("\n")) {
                val lineEndIdx = textBuffer.indexOf("\n")
                val line = textBuffer.substring(0, lineEndIdx).trim()
                textBuffer = textBuffer.substring(lineEndIdx + 1)
                
                if (line.isNotEmpty()) {
                    parseLine(line)
                }
            }
        }
    }

    private suspend fun parseLine(line: String) {
        val matcher = SPOT_REGEX.matcher(line)
        if (matcher.find()) {
            try {
                val spotter = matcher.group(1) ?: ""
                val freqKhz = matcher.group(2)?.toDouble() ?: 0.0
                val dxCall = matcher.group(3) ?: ""
                val comment = matcher.group(4)?.trim() ?: ""
                val time = matcher.group(5) ?: ""

                val spot = DxSpot(
                    spotter = spotter,
                    frequencyKhz = freqKhz,
                    dxCall = dxCall,
                    comment = comment,
                    time = time
                )
                
                _spots.emit(spot)
            } catch (e: Exception) {
                Log.w(TAG, "Error parsing DX spot line: $line", e)
            }
        }
    }
}
