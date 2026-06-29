package com.spacemishka.app.ft_81xcompanion.service

import android.content.Context
import android.hardware.usb.UsbManager
import android.util.Log
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.driver.UsbSerialProber
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.IOException

class UsbCatPort {
    companion object {
        private const val TAG = "UsbCatPort"
        private const val WRITE_TIMEOUT_MS = 200
        private const val READ_TIMEOUT_MS = 500
    }

    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private var usbSerialPort: UsbSerialPort? = null
    private val ioMutex = Mutex()

    suspend fun connect(context: Context, baudRate: Int): Boolean = withContext(Dispatchers.IO) {
        ioMutex.withLock {
            if (_connectionState.value == ConnectionState.CONNECTED) {
                return@withLock true
            }

            _connectionState.value = ConnectionState.CONNECTING
            _errorMessage.value = null
            Log.d(TAG, "Connecting via USB OTG at $baudRate bps...")

            val manager = context.getSystemService(Context.USB_SERVICE) as? UsbManager
            if (manager == null) {
                _connectionState.value = ConnectionState.ERROR
                _errorMessage.value = "USB Service not available"
                return@withLock false
            }

            val availableDrivers = UsbSerialProber.getDefaultProber().findAllDrivers(manager)
            if (availableDrivers.isEmpty()) {
                _connectionState.value = ConnectionState.ERROR
                _errorMessage.value = "No USB Serial devices found. Check OTG connection."
                return@withLock false
            }

            // Find first driver
            val driver = availableDrivers[0]
            val device = driver.device

            // Request permission if we don't have it
            if (!manager.hasPermission(device)) {
                _connectionState.value = ConnectionState.ERROR
                _errorMessage.value = "USB Permission required"
                return@withLock false
            }

            try {
                val connection = manager.openDevice(device)
                if (connection == null) {
                    _connectionState.value = ConnectionState.ERROR
                    _errorMessage.value = "Failed to open USB device connection"
                    return@withLock false
                }

                val port = driver.ports[0]
                port.open(connection)
                
                // Yaesu default framing: 8 data bits, no parity, 2 stop bits
                port.setParameters(baudRate, 8, UsbSerialPort.STOPBITS_2, UsbSerialPort.PARITY_NONE)
                usbSerialPort = port
                _connectionState.value = ConnectionState.CONNECTED
                Log.d(TAG, "USB Connected successfully to device: ${device.deviceName}")
                true
            } catch (e: Exception) {
                closePortInternal()
                _connectionState.value = ConnectionState.ERROR
                _errorMessage.value = "USB connection error: ${e.localizedMessage}"
                Log.e(TAG, "Error opening USB serial port", e)
                false
            }
        }
    }

    suspend fun disconnect() = withContext(Dispatchers.IO) {
        ioMutex.withLock {
            closePortInternal()
            _connectionState.value = ConnectionState.DISCONNECTED
            _errorMessage.value = null
            Log.d(TAG, "USB Disconnected")
        }
    }

    private fun closePortInternal() {
        try {
            usbSerialPort?.close()
        } catch (ignored: Exception) {}
        usbSerialPort = null
    }

    suspend fun sendCommand(command: ByteArray, expectedResponseLength: Int): ByteArray? = withContext(Dispatchers.IO) {
        if (command.size != 5) {
            Log.e(TAG, "Invalid CAT command size: ${command.size} bytes. Must be 5.")
            return@withContext null
        }

        ioMutex.withLock {
            val port = usbSerialPort
            if (_connectionState.value != ConnectionState.CONNECTED || port == null) {
                Log.w(TAG, "USB Not connected. Cannot send command.")
                return@withContext null
            }

            // Clear any stale bytes in input buffer
            try {
                val dummy = ByteArray(256)
                while (port.read(dummy, 10) > 0) { }
            } catch (ignored: Exception) {}

            try {
                port.write(command, WRITE_TIMEOUT_MS)
            } catch (e: IOException) {
                Log.e(TAG, "Failed to write USB CAT command", e)
                handlePortFailure("Write failed: ${e.localizedMessage}")
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
                        Log.e(TAG, "USB Read timeout. Read $bytesRead/$expectedResponseLength bytes.")
                        return@withContext null
                    }
                    val buffer = ByteArray(expectedResponseLength - bytesRead)
                    val read = port.read(buffer, 100)
                    if (read > 0) {
                        System.arraycopy(buffer, 0, response, bytesRead, read)
                        bytesRead += read
                    } else if (read < 0) {
                        Log.e(TAG, "USB port returned EOF")
                        handlePortFailure("Port closed")
                        return@withContext null
                    } else {
                        delay(5)
                    }
                }
                response
            } catch (e: IOException) {
                Log.e(TAG, "Failed to read USB response", e)
                handlePortFailure("Read failed: ${e.localizedMessage}")
                return@withContext null
            }
        }
    }

    private fun handlePortFailure(reason: String) {
        closePortInternal()
        _connectionState.value = ConnectionState.ERROR
        _errorMessage.value = reason
        Log.e(TAG, "USB connection lost: $reason")
    }
}
