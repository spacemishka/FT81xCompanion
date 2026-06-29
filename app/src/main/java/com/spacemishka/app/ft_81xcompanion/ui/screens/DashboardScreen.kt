package com.spacemishka.app.ft_81xcompanion.ui.screens

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.spacemishka.app.ft_81xcompanion.service.CatProtocol
import com.spacemishka.app.ft_81xcompanion.service.ConnectionState
import com.spacemishka.app.ft_81xcompanion.ui.MainViewModel
import com.spacemishka.app.ft_81xcompanion.ui.theme.*
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(viewModel: MainViewModel) {
    val radioState by viewModel.radioState.collectAsState()
    val connState by viewModel.connectionState.collectAsState()
    
    var showDirectEntryDialog by remember { mutableStateOf(false) }
    var tuningStepHz by remember { mutableStateOf(1000L) } // default 1 kHz
    var isScanExpanded by remember { mutableStateOf(false) }

    val bands = listOf(
        BandPreset("160m", 1800000L, 2000000L, CatProtocol.MODE_LSB),
        BandPreset("80m", 3500000L, 4000000L, CatProtocol.MODE_LSB),
        BandPreset("40m", 7000000L, 7300000L, CatProtocol.MODE_LSB),
        BandPreset("30m", 10100000L, 10150000L, CatProtocol.MODE_CW),
        BandPreset("20m", 14000000L, 14350000L, CatProtocol.MODE_USB),
        BandPreset("17m", 18068000L, 18168000L, CatProtocol.MODE_USB),
        BandPreset("15m", 21000000L, 21450000L, CatProtocol.MODE_USB),
        BandPreset("12m", 24890000L, 24990000L, CatProtocol.MODE_USB),
        BandPreset("10m", 28000000L, 29700000L, CatProtocol.MODE_USB),
        BandPreset("6m", 50000000L, 54000000L, CatProtocol.MODE_USB),
        BandPreset("2m", 144000000L, 148000000L, CatProtocol.MODE_FM),
        BandPreset("70cm", 420000000L, 450000000L, CatProtocol.MODE_FM)
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(RadioBlack)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 1. Connection Header
        ConnectionBar(connState = connState, radioState.isLocked) {
            viewModel.setLock(!radioState.isLocked)
        }

        // 2. Frequency & Mode Panel
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, RadioPanel, RoundedCornerShape(12.dp)),
            colors = CardDefaults.cardColors(containerColor = RadioCharcoal),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Mode indicator and Split / RIT flags
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = CatProtocol.formatMode(radioState.mode),
                        color = RadioOrange,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        IndicatorTag(text = "SPLIT", active = radioState.isSplitActive) {
                            viewModel.setSplit(!radioState.isSplitActive)
                        }
                        IndicatorTag(text = "RIT", active = radioState.isRitActive) {
                            viewModel.setRit(!radioState.isRitActive)
                        }
                        IndicatorTag(text = "VFO A/B", active = true) {
                            viewModel.toggleVfo()
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Large Glowing Frequency Readout
                Text(
                    text = formatFrequency(radioState.frequencyHz),
                    color = RadioAmber,
                    fontSize = 38.sp,
                    fontWeight = FontWeight.ExtraBold,
                    fontFamily = FontFamily.Monospace,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .clickable { showDirectEntryDialog = true }
                        .padding(vertical = 8.dp)
                        .shadow(elevation = 8.dp, spotColor = RadioOrange)
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Segmented LED S-Meter
                LedMeter(
                    sValue = radioState.sMeter,
                    isTx = radioState.isPttActive,
                    txPower = radioState.txPower,
                    swrHigh = radioState.isSwrHigh,
                    sqOpen = radioState.isSquelchOpen
                )
            }
        }

        // 3. Tuning Controls & Step Selection
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Tuning step selector
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.weight(1f)
            ) {
                listOf(100L, 1000L, 10000L, 100000L).forEach { step ->
                    val label = when (step) {
                        100L -> "100Hz"
                        1000L -> "1kHz"
                        10000L -> "10kHz"
                        100000L -> "100kHz"
                        else -> ""
                    }
                    Button(
                        onClick = { tuningStepHz = step },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (tuningStepHz == step) RadioOrange else RadioPanel,
                            contentColor = Color.White
                        ),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                        modifier = Modifier
                            .height(32.dp)
                            .weight(1f),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(text = label, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Frequency manual tune step buttons
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Button(
                    onClick = { viewModel.setFrequency(radioState.frequencyHz - tuningStepHz) },
                    colors = ButtonDefaults.buttonColors(containerColor = RadioPanel),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.size(44.dp),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Text(text = "-", fontSize = 22.sp, color = Color.White)
                }
                Button(
                    onClick = { viewModel.setFrequency(radioState.frequencyHz + tuningStepHz) },
                    colors = ButtonDefaults.buttonColors(containerColor = RadioPanel),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.size(44.dp),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Text(text = "+", fontSize = 22.sp, color = Color.White)
                }
            }
        }

        // 4. Primary Mode Selector
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            val modes = listOf(
                "USB" to CatProtocol.MODE_USB,
                "LSB" to CatProtocol.MODE_LSB,
                "CW" to CatProtocol.MODE_CW,
                "AM" to CatProtocol.MODE_AM,
                "FM" to CatProtocol.MODE_FM,
                "DIG" to CatProtocol.MODE_DIG
            )
            modes.forEach { (name, code) ->
                val isSelected = radioState.mode == code
                Button(
                    onClick = {
                        viewModel.setMode(code)
                        // Mode appropriateness warning check
                        checkModeBandAppropriateness(name, radioState.frequencyHz)
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isSelected) RadioOrange else RadioPanel,
                        contentColor = Color.White
                    ),
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(vertical = 8.dp),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(text = name, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        
        // Smart-Scan Panel (Collapsible Card)
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = RadioCharcoal),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Header (Click to toggle expansion)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { isScanExpanded = !isScanExpanded },
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically, 
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "SMART-SCANNER",
                            color = RadioOrange,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                        if (radioState.isScanActive) {
                            Text(
                                text = if (radioState.isScanPausedOnSignal) "[ PAUSED ON SIGNAL ]" else "[ SCANNING ]",
                                color = if (radioState.isScanPausedOnSignal) RadioOrange else RadioGreen,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    Text(
                        text = if (isScanExpanded) "▲" else "▼",
                        color = Color.Gray,
                        fontSize = 11.sp
                    )
                }

                if (isScanExpanded) {
                    // Scanning Parameters Configuration
                    var startFreqText by remember { mutableStateOf((radioState.scanStartFreqHz / 1_000_000.0).toString()) }
                    var endFreqText by remember { mutableStateOf((radioState.scanEndFreqHz / 1_000_000.0).toString()) }
                    var selectedStepHz by remember { mutableStateOf(radioState.scanStepHz) }
                    val selectedDwellMs by remember { mutableStateOf(radioState.scanDwellMs) }
                    var showStepDropdown by remember { mutableStateOf(false) }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = startFreqText,
                            onValueChange = { startFreqText = it },
                            label = { Text("Start (MHz)", color = Color.Gray, fontSize = 10.sp) },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = RadioOrange,
                                unfocusedBorderColor = RadioPanel,
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            ),
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp)
                        )

                        OutlinedTextField(
                            value = endFreqText,
                            onValueChange = { endFreqText = it },
                            label = { Text("End (MHz)", color = Color.Gray, fontSize = 10.sp) },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = RadioOrange,
                                unfocusedBorderColor = RadioPanel,
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            ),
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp)
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = "Step Size", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Box {
                            val stepLabels = mapOf(
                                5000L to "5 kHz",
                                10000L to "10 kHz",
                                12500L to "12.5 kHz",
                                25000L to "25 kHz",
                                50000L to "50 kHz",
                                100000L to "100 kHz"
                            )
                            Button(
                                onClick = { showStepDropdown = true },
                                colors = ButtonDefaults.buttonColors(containerColor = RadioPanel),
                                shape = RoundedCornerShape(6.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp)
                            ) {
                                Text(text = stepLabels[selectedStepHz] ?: "12.5 kHz", color = RadioOrange, fontSize = 12.sp)
                            }
                            DropdownMenu(
                                expanded = showStepDropdown,
                                onDismissRequest = { showStepDropdown = false },
                                modifier = Modifier.background(RadioPanel)
                            ) {
                                stepLabels.forEach { (stepHz, label) ->
                                    DropdownMenuItem(
                                        text = { Text(text = label, color = Color.White) },
                                        onClick = {
                                            selectedStepHz = stepHz
                                            showStepDropdown = false
                                        }
                                    )
                                }
                            }
                        }
                    }

                    // Start/Stop Action Buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val isScanning = radioState.isScanActive
                        Button(
                            onClick = {
                                if (isScanning) {
                                    viewModel.stopBandScan()
                                } else {
                                    val startHz = (startFreqText.toDoubleOrNull() ?: 144.0) * 1_000_000
                                    val endHz = (endFreqText.toDoubleOrNull() ?: 146.0) * 1_000_000
                                    viewModel.startBandScan(startHz.toLong(), endHz.toLong(), selectedStepHz, selectedDwellMs)
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isScanning) RadioRed else RadioOrange
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = if (isScanning) "STOP SCAN" else "START SCAN",
                                color = if (isScanning) Color.White else Color.Black,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        // 5. Band Stack Grid
        Text(
            text = "BAND STACK",
            color = Color.Gray,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 4.dp)
        )

        LazyVerticalGrid(
            columns = GridCells.Fixed(4),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.weight(1f)
        ) {
            items(bands) { band ->
                val isCurrentBand = radioState.frequencyHz in band.minFreq..band.maxFreq
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            viewModel.setFrequency(band.defaultFreq)
                            viewModel.setMode(band.defaultMode)
                        }
                        .border(
                            1.dp,
                            if (isCurrentBand) RadioOrange else Color.Transparent,
                            RoundedCornerShape(8.dp)
                        ),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isCurrentBand) RadioOrangeGlow else RadioPanel
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = band.name,
                            color = if (isCurrentBand) RadioOrange else Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // 6. Giant Press-to-Talk (PTT) Button
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(80.dp),
            contentAlignment = Alignment.Center
        ) {
            val isTx = radioState.isPttActive
            val isConnected = connState == ConnectionState.CONNECTED
            
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(40.dp))
                    .background(
                        Brush.verticalGradient(
                            if (isTx) listOf(RadioRed, Color(0xFFB71C1C))
                            else listOf(RadioPanel, Color(0xFF1E1E1E))
                        )
                    )
                    .border(
                        2.dp,
                        if (isTx) RadioLedRed else if (isConnected) RadioOrange else Color.Gray,
                        RoundedCornerShape(40.dp)
                    )
                    .pointerInput(isConnected) {
                        if (!isConnected) return@pointerInput
                        awaitPointerEventScope {
                            while (true) {
                                awaitFirstDown()
                                viewModel.setPtt(true)
                                waitForUpOrCancellation()
                                viewModel.setPtt(false)
                            }
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Transmitter status LED indicator
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .clip(CircleShape)
                            .background(if (isTx) RadioLedRed else if (isConnected) RadioLedGreen else Color.Gray)
                    )
                    Text(
                        text = if (isTx) "TRANSMITTING (PTT ON)" else if (isConnected) "PRESS AND HOLD PTT" else "PTT LOCKED (DISCONNECTED)",
                        color = if (isTx) Color.White else if (isConnected) Color.White else Color.Gray,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }

    // Direct Frequency Entry Dialog
    if (showDirectEntryDialog) {
        var entryText by remember { mutableStateOf("") }
        Dialog(onDismissRequest = { showDirectEntryDialog = false }) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                colors = CardDefaults.cardColors(containerColor = RadioCharcoal),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "DIRECT QSY",
                        color = RadioOrange,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    
                    // Display frequency entry formatted
                    Text(
                        text = if (entryText.isEmpty()) "0 Hz" else "${entryText} Hz",
                        color = Color.White,
                        fontSize = 24.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )

                    // Numeric grid pad
                    val keys = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "C", "0", "OK")
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(keys) { key ->
                            Button(
                                onClick = {
                                    when (key) {
                                        "C" -> entryText = ""
                                        "OK" -> {
                                            val hz = entryText.toLongOrNull()
                                            if (hz != null && hz in 100000L..470000000L) {
                                                viewModel.setFrequency(hz)
                                                showDirectEntryDialog = false
                                            }
                                        }
                                        else -> {
                                            if (entryText.length < 9) {
                                                entryText += key
                                            }
                                        }
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = RadioPanel),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.height(48.dp)
                            ) {
                                Text(
                                    text = key,
                                    color = if (key == "OK") RadioOrange else Color.White,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                    
                    TextButton(onClick = { showDirectEntryDialog = false }) {
                        Text(text = "CANCEL", color = Color.Gray)
                    }
                }
            }
        }
    }
}

@Composable
fun ConnectionBar(connState: ConnectionState, isLocked: Boolean, onLockToggle: () -> Unit) {
    val statusText = when (connState) {
        ConnectionState.CONNECTED -> "CONNECTED"
        ConnectionState.CONNECTING -> "CONNECTING..."
        ConnectionState.ERROR -> "CONNECTION ERROR"
        ConnectionState.DISCONNECTED -> "DISCONNECTED"
    }
    val statusColor = when (connState) {
        ConnectionState.CONNECTED -> RadioGreen
        ConnectionState.CONNECTING -> RadioOrange
        ConnectionState.ERROR -> RadioRed
        ConnectionState.DISCONNECTED -> Color.Gray
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(RadioCharcoal)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(statusColor)
            )
            Text(
                text = "CAT: $statusText",
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        }
        
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = onLockToggle,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isLocked) RadioOrange else RadioPanel,
                    contentColor = Color.White
                ),
                contentPadding = PaddingValues(horizontal = 12.dp),
                modifier = Modifier.height(28.dp),
                shape = RoundedCornerShape(4.dp)
            ) {
                Text(
                    text = if (isLocked) "KEY LOCK ON" else "KEY LOCK OFF",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun IndicatorTag(text: String, active: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(if (active) RadioOrange else RadioPanel)
            .clickable { onClick() }
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(
            text = text,
            color = if (active) Color.Black else Color.Gray,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun LedMeter(sValue: Int, isTx: Boolean, txPower: Int, swrHigh: Boolean, sqOpen: Boolean) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (isTx) "RF POWER OUT" else "SIGNAL STRENGTH (S-METER)",
                color = Color.Gray,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold
            )
            
            if (isTx && swrHigh) {
                Text(
                    text = "HIGH SWR WARNING",
                    color = RadioRed,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            } else if (!isTx) {
                Text(
                    text = if (sqOpen) "SQL OPEN" else "MUTED",
                    color = if (sqOpen) RadioGreen else Color.Gray,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // 15-segment LED bar
        val activeSegments = if (isTx) txPower else sValue
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(16.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            for (i in 0..14) {
                val isActive = i < activeSegments
                val baseColor = when {
                    i < 9 -> RadioGreen  // S1 - S9 (Green)
                    i < 12 -> RadioOrange // +10 - +20 dB (Orange)
                    else -> RadioRed     // +30 dB (Red)
                }
                val finalColor = if (isActive) baseColor else RadioPanel
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(2.dp))
                        .background(finalColor)
                )
            }
        }

        // Labels underneath meter
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            if (isTx) {
                Text(text = "0", color = Color.Gray, fontSize = 8.sp)
                Text(text = "PWR MID", color = Color.Gray, fontSize = 8.sp)
                Text(text = "MAX (5W)", color = Color.Gray, fontSize = 8.sp)
            } else {
                Text(text = "S1", color = Color.Gray, fontSize = 8.sp)
                Text(text = "S5", color = Color.Gray, fontSize = 8.sp)
                Text(text = "S9", color = Color.Gray, fontSize = 8.sp)
                Text(text = "+20dB", color = Color.Gray, fontSize = 8.sp)
                Text(text = "+30dB", color = Color.Gray, fontSize = 8.sp)
            }
        }
    }
}

data class BandPreset(
    val name: String,
    val minFreq: Long,
    val maxFreq: Long,
    val defaultMode: Byte,
    val defaultFreq: Long = (minFreq + maxFreq) / 2
)

private fun formatFrequency(hz: Long): String {
    val mhz = hz / 1_000_000.0
    return String.format(Locale.US, "%,.4f MHz", mhz)
}


private fun checkModeBandAppropriateness(modeName: String, frequencyHz: Long) {
    // Basic band-appropriateness warnings (informational)
    // E.g. using LSB above 10 MHz, or USB below 10 MHz
    if (frequencyHz < 10000000L && modeName == "USB") {
        Log.w("BandCheck", "Convention warning: USB is typically used above 10 MHz.")
    } else if (frequencyHz > 10000000L && frequencyHz < 30000000L && modeName == "LSB") {
        Log.w("BandCheck", "Convention warning: LSB is typically used below 10 MHz.")
    }
}
