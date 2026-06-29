package com.spacemishka.app.ft_81xcompanion.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.spacemishka.app.ft_81xcompanion.satellite.SatellitePassState
import com.spacemishka.app.ft_81xcompanion.ui.MainViewModel
import com.spacemishka.app.ft_81xcompanion.ui.theme.*
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SatelliteScreen(viewModel: MainViewModel) {
    val satState = viewModel.satelliteState
    val currentSat = viewModel.selectedSatellite
    val obsLoc = viewModel.observerLocation

    var showSatSelectDropdown by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(RadioBlack)
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Satellite Selection Panel
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = RadioCharcoal),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "ACTIVE SATELLITE",
                    color = Color.Gray,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )

                Box {
                    Button(
                        onClick = { showSatSelectDropdown = true },
                        colors = ButtonDefaults.buttonColors(containerColor = RadioPanel),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = currentSat?.name ?: "SELECT SATELLITE",
                                color = RadioOrange,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(text = "▼", color = Color.Gray, fontSize = 12.sp)
                        }
                    }

                    DropdownMenu(
                        expanded = showSatSelectDropdown,
                        onDismissRequest = { showSatSelectDropdown = false },
                        modifier = Modifier
                            .fillMaxWidth(0.85f)
                            .background(RadioPanel)
                    ) {
                        viewModel.satellites.forEach { sat ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = sat.name,
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold
                                    )
                                },
                                onClick = {
                                    viewModel.selectedSatellite = sat
                                    showSatSelectDropdown = false
                                }
                            )
                        }
                    }
                }
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "GPS: " + if (obsLoc != null) String.format(Locale.US, "%.4f, %.4f", obsLoc.latitude, obsLoc.longitude) else "NO GPS FIX (USING DEFAULT)",
                        color = if (obsLoc != null) RadioGreen else Color.Gray,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Alt: " + if (obsLoc != null) "${obsLoc.altitude.toInt()}m" else "0m",
                        color = Color.Gray,
                        fontSize = 11.sp
                    )
                }
            }
        }

        // 2. Dual VFO Doppler Frequency Panel
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = RadioCharcoal),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "DUAL VFO DOPPLER SATELLITE TRANSCEIVER",
                    color = Color.Gray,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Downlink VFO-A
                    Card(
                        modifier = Modifier
                            .weight(1f)
                            .border(1.dp, RadioPanel, RoundedCornerShape(8.dp)),
                        colors = CardDefaults.cardColors(containerColor = RadioPanel)
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(text = "DOWNLINK (RX) VFO-A", color = Color.Gray, fontSize = 10.sp)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = formatFrequency(viewModel.satDownlinkFreqHz),
                                color = RadioAmber,
                                fontSize = 16.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            val dShift = satState?.downlinkDopplerHz ?: 0L
                            Text(
                                text = "Doppler: " + if (dShift >= 0) "+$dShift Hz" else "$dShift Hz",
                                color = if (dShift >= 0) RadioGreen else RadioRed,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }

                    // Uplink VFO-B
                    Card(
                        modifier = Modifier
                            .weight(1f)
                            .border(1.dp, RadioPanel, RoundedCornerShape(8.dp)),
                        colors = CardDefaults.cardColors(containerColor = RadioPanel)
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(text = "UPLINK (TX) VFO-B", color = Color.Gray, fontSize = 10.sp)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = formatFrequency(viewModel.satUplinkFreqHz),
                                color = RadioOrange,
                                fontSize = 16.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            val uShift = satState?.uplinkDopplerHz ?: 0L
                            Text(
                                text = "Doppler: " + if (uShift >= 0) "+$uShift Hz" else "$uShift Hz",
                                color = if (uShift >= 0) RadioGreen else RadioRed,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }

                // Doppler tracking toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "DOPPLER TRACKING CONTROL",
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Automatically adjust VFOs in real-time",
                            color = Color.Gray,
                            fontSize = 11.sp
                        )
                    }

                    Switch(
                        checked = viewModel.isDopplerTrackingActive,
                        onCheckedChange = { viewModel.isDopplerTrackingActive = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = RadioOrange,
                            checkedTrackColor = RadioOrangeGlow
                        )
                    )
                }
            }
        }

        // 3. Radar & Pass Status Panel
        if (satState != null) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = RadioCharcoal),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Radar view
                    SatelliteRadar(
                        azimuth = satState.azimuth,
                        elevation = satState.elevation,
                        isVisible = satState.isVisible,
                        modifier = Modifier.size(120.dp)
                    )

                    Spacer(modifier = Modifier.width(16.dp))

                    // Pass metrics
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = if (satState.isVisible) "PASS IN PROGRESS" else "BELOW HORIZON (AOS PENDING)",
                            color = if (satState.isVisible) RadioGreen else RadioRed,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )

                        PassMetricRow(label = "Elevation:", value = String.format(Locale.US, "%.1f°", satState.elevation))
                        PassMetricRow(label = "Azimuth:", value = String.format(Locale.US, "%.1f°", satState.azimuth))
                        PassMetricRow(label = "Range:", value = String.format(Locale.US, "%.1f km", satState.rangeKm))
                        PassMetricRow(
                            label = "Range Rate:", 
                            value = String.format(Locale.US, "%.2f km/s", satState.rangeRateMS / 1000.0)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SatelliteRadar(azimuth: Double, elevation: Double, isVisible: Boolean, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val center = Offset(size.width / 2, size.height / 2)
        val radius = size.width / 2
        
        // Draw concentric circles
        drawCircle(
            color = RadioPanel,
            radius = radius,
            center = center,
            style = Stroke(width = 2.dp.toPx())
        )
        drawCircle(
            color = RadioPanel,
            radius = radius * 0.66f,
            center = center,
            style = Stroke(width = 1.dp.toPx())
        )
        drawCircle(
            color = RadioPanel,
            radius = radius * 0.33f,
            center = center,
            style = Stroke(width = 1.dp.toPx())
        )

        // Draw crosshairs
        drawLine(
            color = RadioPanel,
            start = Offset(center.x - radius, center.y),
            end = Offset(center.x + radius, center.y),
            strokeWidth = 1.dp.toPx()
        )
        drawLine(
            color = RadioPanel,
            start = Offset(center.x, center.y - radius),
            end = Offset(center.x, center.y + radius),
            strokeWidth = 1.dp.toPx()
        )

        // Draw compass headings
        // North is top, East is right

        // Plot satellite point
        if (isVisible) {
            // Elevation is 0 at horizon (outer edge) and 90 at zenith (center)
            val relDistance = (90.0 - elevation) / 90.0
            val plotRadius = radius * relDistance.toFloat()
            
            // In radar, 0 deg Azimuth is top (North). 90 deg is right (East).
            // Standard polar coords: 0 deg is right, rotates counter-clockwise.
            // Map Azimuth to standard Polar angle: angleRad = (90 - Azimuth) * PI / 180
            val angleRad = (90.0 - azimuth) * PI / 180.0
            
            val satX = center.x + (plotRadius * cos(angleRad)).toFloat()
            val satY = center.y - (plotRadius * sin(angleRad)).toFloat() // negative y is up
            
            // Draw glowing dot
            drawCircle(
                color = RadioOrange,
                radius = 8.dp.toPx(),
                center = Offset(satX, satY)
            )
            drawCircle(
                color = Color.White,
                radius = 3.dp.toPx(),
                center = Offset(satX, satY)
            )
        }
    }
}

@Composable
fun PassMetricRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, color = Color.Gray, fontSize = 12.sp)
        Text(text = value, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
    }
}

private fun formatFrequency(hz: Long): String {
    val mhz = hz / 1_000_000.0
    return String.format(Locale.US, "%.4f MHz", mhz)
}
