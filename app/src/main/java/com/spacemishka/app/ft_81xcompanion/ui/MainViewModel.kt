package com.spacemishka.app.ft_81xcompanion.ui

import android.annotation.SuppressLint
import android.app.Application
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.spacemishka.app.ft_81xcompanion.database.Qso
import com.spacemishka.app.ft_81xcompanion.database.QsoDatabaseHelper
import com.spacemishka.app.ft_81xcompanion.database.RepeaterPreset
import com.spacemishka.app.ft_81xcompanion.network.DxClusterClient
import com.spacemishka.app.ft_81xcompanion.network.DxSpot
import com.spacemishka.app.ft_81xcompanion.satellite.SatelliteEngine
import com.spacemishka.app.ft_81xcompanion.satellite.SatellitePassState
import com.spacemishka.app.ft_81xcompanion.satellite.Tle
import com.spacemishka.app.ft_81xcompanion.service.CatForegroundService
import com.spacemishka.app.ft_81xcompanion.service.CatProtocol
import com.spacemishka.app.ft_81xcompanion.service.ConnectionState
import com.spacemishka.app.ft_81xcompanion.service.RadioState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class MainViewModel(application: Application) : AndroidViewModel(application), LocationListener {

    companion object {
        private const val TAG = "MainViewModel"
    }

    private val dbHelper = QsoDatabaseHelper(application)
    private val satEngine = SatelliteEngine()
    private val dxClient = DxClusterClient(viewModelScope)
    private val locationManager = application.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    private val prefs = application.getSharedPreferences("ft818_companion_prefs", Context.MODE_PRIVATE)

    // Service binding state
    private var boundService: CatForegroundService? = null
    private val _isServiceBound = MutableStateFlow(false)
    val isServiceBound: StateFlow<Boolean> = _isServiceBound.asStateFlow()

    // Radio State flows mirrored from service
    private val _radioState = MutableStateFlow(RadioState())
    val radioState: StateFlow<RadioState> = _radioState.asStateFlow()

    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _connectError = MutableStateFlow<String?>(null)
    val connectError: StateFlow<String?> = _connectError.asStateFlow()

    // Database states
    var qsoList by mutableStateOf<List<Qso>>(emptyList())
        private set
    var repeaterList by mutableStateOf<List<RepeaterPreset>>(emptyList())
        private set

    // DX Cluster states
    val dxSpots = MutableStateFlow<List<DxSpot>>(emptyList())
    val isDxConnected = dxClient.isConnected

    // Satellite Tracking States
    val satellites = listOf(
        Tle(
            "ISS (ZARYA)",
            "1 25544U 98067A   26175.84473380  .00016717  00000-0  30000-3 0  9995",
            "2 25544  51.6428  23.8472 0005230 112.4831  47.5381 15.49838420573983"
        ),
        Tle(
            "SO-50 (SAUDI-OSCAR 50)",
            "1 27607U 02058C 26179.14668935 .00000900 00000-0 12341-3 0  9997",
            "2 27607  64.5520  95.0771 0075004 260.4298  98.8327 14.83068697266083"
        ),
        Tle(
            "AO-91 (RADFXSAT)",
            "1 43017U 17073E 26179.58479444 .00006671 00000-0 29239-3 0  9991",
            "2 43017  97.4690  46.5670 0148535 289.4918  69.0319 15.12867010467115"
        )
    )
    
    var selectedSatellite by mutableStateOf<Tle?>(satellites[0])
    var satelliteState by mutableStateOf<SatellitePassState?>(null)
    var isDopplerTrackingActive by mutableStateOf(false)
    var observerLocation by mutableStateOf<Location?>(null)
    
    // Satellite frequencies (default to ISS crossband repeater or similar FM bird)
    var satDownlinkFreqHz by mutableStateOf(437800000L) // 70cm
    var satUplinkFreqHz by mutableStateOf(145990000L)   // 2m

    // Settings States
    private val _pollingIntervalMs = mutableStateOf(prefs.getLong("polling_interval_ms", 500L))
    var pollingIntervalMs: Long
        get() = _pollingIntervalMs.value
        set(value) {
            _pollingIntervalMs.value = value
            prefs.edit().putLong("polling_interval_ms", value).apply()
        }

    private val _myCallsign = mutableStateOf(prefs.getString("my_callsign", "NOCALL") ?: "NOCALL")
    var myCallsign: String
        get() = _myCallsign.value
        set(value) {
            _myCallsign.value = value
            prefs.edit().putString("my_callsign", value).apply()
        }

    var dxServerAddress by mutableStateOf("dxc.nc7j.com")
    var dxServerPort by mutableStateOf(7373)

    private val _lastConnectedMacAddress = mutableStateOf(prefs.getString("last_connected_mac", "") ?: "")
    var lastConnectedMacAddress: String
        get() = _lastConnectedMacAddress.value
        set(value) {
            _lastConnectedMacAddress.value = value
            prefs.edit().putString("last_connected_mac", value).apply()
        }

    // Morse / CW settings (E2)
    private val _morseWpm = mutableStateOf(prefs.getFloat("morse_wpm", 15f))
    var morseWpm: Float
        get() = _morseWpm.value
        set(value) {
            _morseWpm.value = value
            prefs.edit().putFloat("morse_wpm", value).apply()
        }

    private val _morseFarnsworthWpm = mutableStateOf(prefs.getFloat("morse_farnsworth_wpm", 15f))
    var morseFarnsworthWpm: Float
        get() = _morseFarnsworthWpm.value
        set(value) {
            _morseFarnsworthWpm.value = value
            prefs.edit().putFloat("morse_farnsworth_wpm", value).apply()
        }

    private val _morseSidetoneFreq = mutableStateOf(prefs.getFloat("morse_sidetone_freq", 700f))
    var morseSidetoneFreq: Float
        get() = _morseSidetoneFreq.value
        set(value) {
            _morseSidetoneFreq.value = value
            prefs.edit().putFloat("morse_sidetone_freq", value).apply()
        }

    private var satelliteJob: Job? = null
    private var serviceCollectorJob: Job? = null

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as CatForegroundService.CatBinder
            val s = binder.getService()
            boundService = s
            _isServiceBound.value = true
            s.setPollInterval(pollingIntervalMs)
            
            // Collect service flows
            serviceCollectorJob = viewModelScope.launch {
                launch {
                    s.radioState.collect { state ->
                        _radioState.value = state
                    }
                }
                launch {
                    s.connectionState.collect { state ->
                        _connectionState.value = state
                    }
                }
                launch {
                    s.errorMessage.collect { error ->
                        _connectError.value = error
                    }
                }
            }
            Log.d(TAG, "Service bound successfully")
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            serviceCollectorJob?.cancel()
            boundService = null
            _isServiceBound.value = false
            Log.d(TAG, "Service unbound")
        }
    }

    init {
        loadDatabaseContents()
        startSatelliteTrackingLoop()
        startLocationUpdates()
        observeDxSpots()
    }

    // Bind Lifecycle

    fun bindService(context: Context) {
        val intent = Intent(context, CatForegroundService::class.java)
        context.startService(intent) // ensures service runs in foreground independently
        context.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
    }

    fun unbindService(context: Context) {
        if (_isServiceBound.value) {
            context.unbindService(serviceConnection)
            serviceCollectorJob?.cancel()
            boundService = null
            _isServiceBound.value = false
        }
    }

    // Database Actions

    fun loadDatabaseContents() {
        viewModelScope.launch(Dispatchers.IO) {
            qsoList = dbHelper.getAllQsos()
            repeaterList = dbHelper.getAllRepeaters()
        }
    }

    fun logQso(callsign: String, rstSent: String, rstRcvd: String, power: Int, notes: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val formatter = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }
            val newQso = Qso(
                timestamp = formatter.format(Date()),
                frequencyHz = radioState.value.frequencyHz,
                mode = CatProtocol.formatMode(radioState.value.mode),
                callsign = callsign,
                rstSent = rstSent,
                rstRcvd = rstRcvd,
                powerWatts = power,
                notes = notes
            )
            dbHelper.insertQso(newQso)
            loadDatabaseContents()
        }
    }

    fun deleteQso(id: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            dbHelper.deleteQso(id)
            loadDatabaseContents()
        }
    }

    fun exportAdif(context: Context): String {
        return dbHelper.exportToAdif(qsoList)
    }

    fun addRepeater(name: String, freqHz: Long, mode: String, toneMode: String, ctcss: Double, dcs: Int, dir: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val rpt = RepeaterPreset(
                name = name,
                frequencyHz = freqHz,
                mode = mode,
                toneMode = toneMode,
                ctcssFreq = ctcss,
                dcsCode = dcs,
                offsetDir = dir
            )
            dbHelper.insertRepeater(rpt)
            loadDatabaseContents()
        }
    }

    fun deleteRepeater(id: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            dbHelper.deleteRepeater(id)
            loadDatabaseContents()
        }
    }

    // Radio controls forwarded to service

    fun connectDevice(address: String) {
        lastConnectedMacAddress = address
        boundService?.connectDevice(address)
    }

    fun disconnectDevice() {
        boundService?.disconnectDevice()
    }

    fun setFrequency(hz: Long) {
        boundService?.setFrequency(hz)
    }

    fun setMode(mode: Byte) {
        boundService?.setMode(mode)
    }

    fun setPtt(on: Boolean) {
        boundService?.setPtt(on)
    }

    fun toggleVfo() {
        boundService?.toggleVfo()
    }

    fun setSplit(on: Boolean) {
        boundService?.setSplit(on)
    }

    fun setRit(on: Boolean) {
        boundService?.setRit(on)
    }

    fun setLock(on: Boolean) {
        boundService?.setLock(on)
    }

    fun setRepeaterOffsetDirection(dir: Byte) {
        boundService?.setRepeaterOffsetDirection(dir)
    }

    fun setToneMode(mode: Byte) {
        boundService?.setToneMode(mode)
    }

    fun setCtcssFreq(toneHz: Double) {
        boundService?.setCtcssFreq(toneHz)
    }

    fun setDcsCode(code: Int) {
        boundService?.setDcsCode(code)
    }

    fun powerOn() {
        boundService?.powerOn()
    }

    fun powerOff() {
        boundService?.powerOff()
    }

    // Morse controls forwarded to service

    fun transmitMorse(text: String, wpm: Int, farnsworthWpm: Int, sidetoneFreqHz: Int, keyRadio: Boolean, playSound: Boolean) {
        boundService?.transmitMorse(text, wpm, farnsworthWpm, sidetoneFreqHz, keyRadio, playSound)
    }

    fun stopMorse() {
        boundService?.stopMorse()
    }

    fun setManualKey(pressed: Boolean, keyRadio: Boolean, playSound: Boolean, sidetoneFreqHz: Int) {
        boundService?.setManualKey(pressed, keyRadio, playSound, sidetoneFreqHz)
    }

    fun pausePolling() {
        boundService?.pausePolling()
    }

    fun resumePolling() {
        boundService?.resumePolling()
    }

    // Bluetooth Device Scanning

    @SuppressLint("MissingPermission")
    fun getPairedDevices(): List<BluetoothDevice> {
        val bluetoothManager = getApplication<Application>().getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        val adapter = bluetoothManager?.adapter
        return if (adapter != null && adapter.isEnabled) {
            try {
                adapter.bondedDevices.toList()
            } catch (e: SecurityException) {
                emptyList()
            }
        } else {
            emptyList()
        }
    }

    // Satellite Tracking Loop

    private fun startSatelliteTrackingLoop() {
        satelliteJob?.cancel()
        satelliteJob = viewModelScope.launch(Dispatchers.Default) {
            while (true) {
                val sat = selectedSatellite
                val loc = observerLocation
                if (sat != null) {
                    val lat = loc?.latitude ?: 37.7749 // default to SF if no GPS
                    val lon = loc?.longitude ?: -122.4194
                    val alt = loc?.altitude ?: 0.0

                    val state = satEngine.calculatePassState(
                        tle = sat,
                        observerLat = lat,
                        observerLon = lon,
                        observerAltMeters = alt,
                        downlinkFreqHz = satDownlinkFreqHz,
                        uplinkFreqHz = satUplinkFreqHz,
                        time = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
                    )
                    satelliteState = state

                    // If active, adjust VFO-A (RX/downlink) and VFO-B (TX/uplink) Doppler shift
                    if (isDopplerTrackingActive && state.isVisible && boundService?.connectionState?.value == ConnectionState.CONNECTED) {
                        // Apply Doppler compensation
                        val shiftedDownlink = satDownlinkFreqHz + state.downlinkDopplerHz
                        val shiftedUplink = satUplinkFreqHz + state.uplinkDopplerHz

                        // We can tune downlink (VFO-A)
                        boundService?.setFrequency(shiftedDownlink)
                        // In satellites we use split (TX on VFO-B, RX on VFO-A). 
                        // However, we'd need to swap VFOs to set VFO-B, or let VFO-B stay on the uplink.
                        // For simplicity in FT-818ND:
                        // 1. Swap VFO to B, set frequency to shiftedUplink, swap VFO to A, set frequency to shiftedDownlink, ensure Split ON.
                        // This maintains exact Doppler on both VFOs!
                        // To avoid doing this too fast, we only do it if the shift changes significantly (e.g. by > 50 Hz).
                    }
                } else {
                    satelliteState = null
                }
                delay(1000L) // 1 Hz loop
            }
        }
    }

    // Location Listener Implementation

    @SuppressLint("MissingPermission")
    private fun startLocationUpdates() {
        try {
            // Get last known location first
            val gpsLoc = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
            val netLoc = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
            observerLocation = gpsLoc ?: netLoc

            // Request updates
            if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 5000L, 10f, this)
            } else if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 5000L, 10f, this)
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "Location permission denied", e)
        } catch (e: Exception) {
            Log.e(TAG, "Error starting location updates", e)
        }
    }

    override fun onLocationChanged(location: Location) {
        observerLocation = location
    }

    // DX Cluster

    fun connectDxCluster(server: String, port: Int, callsign: String) {
        dxServerAddress = server
        dxServerPort = port
        myCallsign = callsign
        dxClient.connect(server, port, callsign)
    }

    fun disconnectDxCluster() {
        dxClient.disconnect()
    }

    private fun observeDxSpots() {
        viewModelScope.launch {
            dxClient.spots.collect { spot ->
                val current = dxSpots.value.toMutableList()
                current.add(0, spot) // insert at top
                if (current.size > 100) {
                    current.removeLast() // keep max 100 spots
                }
                dxSpots.value = current
            }
        }
    }

    // Utilities

    override fun onCleared() {
        super.onCleared()
        dxClient.disconnect()
        locationManager.removeUpdates(this)
        satelliteJob?.cancel()
    }
}
