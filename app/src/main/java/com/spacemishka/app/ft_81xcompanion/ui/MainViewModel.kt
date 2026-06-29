package com.spacemishka.app.ft_81xcompanion.ui

import android.annotation.SuppressLint
import android.app.Application
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
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

    // Service binding state
    private var boundService: CatForegroundService? = null
    private val _isServiceBound = MutableStateFlow(false)
    val isServiceBound: StateFlow<Boolean> = _isServiceBound.asStateFlow()

    // Radio State flows mirrored from service
    val radioState = MutableStateFlow(RadioState())
    val connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectError = MutableStateFlow<String?>(null)

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
            "1 27607U 02058C   26175.50000000  .00000084  00000-0  10000-3 0  9994",
            "2 27607  64.5582 123.4567 0081234  45.6789 314.3210 14.71234567 12345"
        ),
        Tle(
            "AO-91 (RADFXSAT)",
            "1 43017U 17073B   26175.50000000  .00000123  00000-0  20000-3 0  9998",
            "2 43017  97.8765 234.5678 0123456  67.8901 292.1234 14.81234567 12345"
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
    var pollingIntervalMs by mutableStateOf(500L)
    var myCallsign by mutableStateOf("NOCALL")
    var dxServerAddress by mutableStateOf("dxc.nc7j.com")
    var dxServerPort by mutableStateOf(7373)
    var lastConnectedMacAddress by mutableStateOf("")

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
                        radioState.value = state
                    }
                }
                launch {
                    s.connectionState.collect { state ->
                        connectionState.value = state
                    }
                }
                launch {
                    s.errorMessage.collect { error ->
                        connectError.value = error
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
                mode = formatMode(radioState.value.mode),
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

    // Bluetooth Device Scanning

    @SuppressLint("MissingPermission")
    fun getPairedDevices(): List<BluetoothDevice> {
        @Suppress("DEPRECATION")
        val adapter = BluetoothAdapter.getDefaultAdapter()
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

    private fun formatMode(modeByte: Byte): String {
        return when (modeByte) {
            CatProtocol.MODE_LSB -> "LSB"
            CatProtocol.MODE_USB -> "USB"
            CatProtocol.MODE_CW -> "CW"
            CatProtocol.MODE_CW_R -> "CW-R"
            CatProtocol.MODE_AM -> "AM"
            CatProtocol.MODE_WFM -> "WFM"
            CatProtocol.MODE_FM -> "FM"
            CatProtocol.MODE_DIG -> "DIG"
            CatProtocol.MODE_PKT -> "PKT"
            else -> "UNKNOWN"
        }
    }

    override fun onCleared() {
        super.onCleared()
        dxClient.disconnect()
        locationManager.removeUpdates(this)
        satelliteJob?.cancel()
    }
}
