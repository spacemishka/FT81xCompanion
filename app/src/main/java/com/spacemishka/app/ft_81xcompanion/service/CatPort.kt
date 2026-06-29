package com.spacemishka.app.ft_81xcompanion.service

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID

enum class ConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    ERROR
}

class CatPort {
    companion object {
        private const val TAG = "CatPort"
        private val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
        private const val WRITE_TIMEOUT_MS = 200L
        private const val READ_TIMEOUT_MS = 500L
    }

    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private var bluetoothSocket: BluetoothSocket? = null
    private var inputStream: InputStream? = null
    private var outputStream: OutputStream? = null
    private val ioMutex = Mutex()

    @SuppressLint("MissingPermission")
    suspend fun connect(deviceAddress: String): Boolean = withContext(Dispatchers.IO) {
        ioMutex.withLock {
            if (_connectionState.value == ConnectionState.CONNECTED) {
                return@withLock true
            }

            _connectionState.value = ConnectionState.CONNECTING
            _errorMessage.value = null
            Log.d(TAG, "Connecting to $deviceAddress...")

            @Suppress("DEPRECATION")
            val adapter = BluetoothAdapter.getDefaultAdapter()
            if (adapter == null || !adapter.isEnabled) {
                _connectionState.value = ConnectionState.ERROR
                _errorMessage.value = "Bluetooth is disabled or unavailable"
                return@withLock false
            }

            val device: BluetoothDevice
            try {
                device = adapter.getRemoteDevice(deviceAddress)
            } catch (e: IllegalArgumentException) {
                _connectionState.value = ConnectionState.ERROR
                _errorMessage.value = "Invalid MAC address: $deviceAddress"
                return@withLock false
            }

            try {
                val socket = device.createRfcommSocketToServiceRecord(SPP_UUID)
                bluetoothSocket = socket
                socket.connect()
                inputStream = socket.inputStream
                outputStream = socket.outputStream
                _connectionState.value = ConnectionState.CONNECTED
                Log.d(TAG, "Connected successfully to $deviceAddress")
                true
            } catch (e: SecurityException) {
                closeSocket()
                _connectionState.value = ConnectionState.ERROR
                _errorMessage.value = "Bluetooth permission denied"
                Log.e(TAG, "SecurityException connecting", e)
                false
            } catch (e: IOException) {
                closeSocket()
                _connectionState.value = ConnectionState.ERROR
                _errorMessage.value = "Failed to connect: ${e.localizedMessage}"
                Log.e(TAG, "IOException connecting", e)
                false
            }
        }
    }

    suspend fun disconnect() = withContext(Dispatchers.IO) {
        ioMutex.withLock {
            closeSocket()
            _connectionState.value = ConnectionState.DISCONNECTED
            _errorMessage.value = null
            Log.d(TAG, "Disconnected")
        }
    }

    fun closeSocket() {
        try {
            inputStream?.close()
        } catch (ignored: Exception) {}
        try {
            outputStream?.close()
        } catch (ignored: Exception) {}
        try {
            bluetoothSocket?.close()
        } catch (ignored: Exception) {}
        inputStream = null
        outputStream = null
        bluetoothSocket = null
    }

    /**
     * Sends a 5-byte CAT command to the radio and waits for the response.
     * Guaranteed to execute sequentially due to the ioMutex.
     */
    suspend fun sendCommand(command: ByteArray, expectedResponseLength: Int): ByteArray? = withContext(Dispatchers.IO) {
        if (command.size != 5) {
            Log.e(TAG, "Invalid CAT command size: ${command.size} bytes. Must be exactly 5.")
            return@withContext null
        }

        ioMutex.withLock {
            val outStream = outputStream
            val inStream = inputStream
            if (_connectionState.value != ConnectionState.CONNECTED || outStream == null || inStream == null) {
                Log.w(TAG, "Not connected. Cannot send command.")
                return@withContext null
            }

            // Clear any stale bytes in the input stream buffer before writing
            try {
                while (inStream.available() > 0) {
                    inStream.read()
                }
            } catch (e: IOException) {
                Log.w(TAG, "Error clearing input buffer", e)
            }

            try {
                // Write all 5 bytes in a single operation
                outStream.write(command)
                outStream.flush()
            } catch (e: IOException) {
                Log.e(TAG, "Failed to write command", e)
                handleConnectionFailure("Write failed: ${e.localizedMessage}")
                return@withContext null
            }

            if (expectedResponseLength <= 0) {
                return@withContext ByteArray(0)
            }

            val response = ByteArray(expectedResponseLength)
            var bytesRead = 0
            val startTime = System.currentTimeMillis()

            try {
                while (bytesRead < expectedResponseLength) {
                    if (System.currentTimeMillis() - startTime > READ_TIMEOUT_MS) {
                        Log.e(TAG, "Read timeout. Read $bytesRead/$expectedResponseLength bytes.")
                        return@withContext null
                    }
                    if (inStream.available() > 0) {
                        val read = inStream.read(response, bytesRead, expectedResponseLength - bytesRead)
                        if (read == -1) {
                            Log.e(TAG, "End of stream reached")
                            handleConnectionFailure("Connection closed by peer")
                            return@withContext null
                        }
                        bytesRead += read
                    } else {
                        // Small sleep to avoid spinning
                        Thread.sleep(5)
                    }
                }
                response
            } catch (e: IOException) {
                Log.e(TAG, "Failed to read response", e)
                handleConnectionFailure("Read failed: ${e.localizedMessage}")
                return@withContext null
            } catch (e: InterruptedException) {
                Log.e(TAG, "Read interrupted", e)
                null
            }
        }
    }

    private fun handleConnectionFailure(reason: String) {
        closeSocket()
        _connectionState.value = ConnectionState.ERROR
        _errorMessage.value = reason
        Log.e(TAG, "Connection lost: $reason")
    }
}
