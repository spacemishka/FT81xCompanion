package com.spacemishka.app.ft_81xcompanion.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.spacemishka.app.ft_81xcompanion.database.Qso
import com.spacemishka.app.ft_81xcompanion.network.DxSpot
import com.spacemishka.app.ft_81xcompanion.service.CatProtocol
import com.spacemishka.app.ft_81xcompanion.ui.MainViewModel
import com.spacemishka.app.ft_81xcompanion.ui.theme.*
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoggingScreen(viewModel: MainViewModel) {
    val radioState by viewModel.radioState.collectAsState()
    val dxSpotsList by viewModel.dxSpots.collectAsState()
    val isDxConnected by viewModel.isDxConnected.collectAsState()
    val context = LocalContext.current

    var activeTab by remember { mutableStateOf(0) } // 0 = QSO Log, 1 = DX Cluster

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(RadioBlack)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Tab Headers
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = { activeTab = 0 },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (activeTab == 0) RadioOrange else RadioPanel,
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                Text(text = "QSO LOGGER & LOGS", fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
            
            Button(
                onClick = { activeTab = 1 },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (activeTab == 1) RadioOrange else RadioPanel,
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                Text(text = "DX CLUSTER SPOTS", fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
        }

        if (activeTab == 0) {
            // QSO logger view
            QsoLoggerAndHistory(viewModel = viewModel, context = context)
        } else {
            // DX Cluster spots view
            DxSpotsPanel(viewModel = viewModel, dxSpotsList = dxSpotsList, isConnected = isDxConnected, context = context)
        }
    }
}

@Composable
fun QsoLoggerAndHistory(viewModel: MainViewModel, context: Context) {
    var callsign by remember { mutableStateOf("") }
    var rstSent by remember { mutableStateOf("59") }
    var rstRcvd by remember { mutableStateOf("59") }
    var power by remember { mutableStateOf("5") }
    var notes by remember { mutableStateOf("") }

    val radioState by viewModel.radioState.collectAsState()

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Form to log a new QSO
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
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "LOG NEW CONTACT", color = RadioOrange, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Text(
                        text = "Freq: ${formatFrequency(radioState.frequencyHz)} (${formatMode(radioState.mode)})",
                        color = Color.Gray,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    OutlinedTextField(
                        value = callsign,
                        onValueChange = { callsign = it },
                        label = { Text("Callsign", color = Color.Gray, fontSize = 10.sp) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = RadioOrange,
                            unfocusedBorderColor = RadioPanel,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        singleLine = true,
                        modifier = Modifier.weight(1.5f),
                        shape = RoundedCornerShape(8.dp)
                    )

                    OutlinedTextField(
                        value = rstSent,
                        onValueChange = { rstSent = it },
                        label = { Text("RST S", color = Color.Gray, fontSize = 10.sp) },
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
                        value = rstRcvd,
                        onValueChange = { rstRcvd = it },
                        label = { Text("RST R", color = Color.Gray, fontSize = 10.sp) },
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
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = power,
                        onValueChange = { power = it },
                        label = { Text("Power (W)", color = Color.Gray, fontSize = 10.sp) },
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
                        value = notes,
                        onValueChange = { notes = it },
                        label = { Text("Notes", color = Color.Gray, fontSize = 10.sp) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = RadioOrange,
                            unfocusedBorderColor = RadioPanel,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        singleLine = true,
                        modifier = Modifier.weight(2.5f),
                        shape = RoundedCornerShape(8.dp)
                    )
                }

                Button(
                    onClick = {
                        if (callsign.isNotEmpty()) {
                            viewModel.logQso(
                                callsign = callsign,
                                rstSent = rstSent,
                                rstRcvd = rstRcvd,
                                power = power.toIntOrNull() ?: 5,
                                notes = notes
                            )
                            callsign = ""
                            notes = ""
                            Toast.makeText(context, "Contact logged!", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = RadioOrange),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(text = "LOG QSO", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            }
        }

        // QSO history table with ADIF export
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = "QSO LOG HISTORY", color = Color.Gray, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            
            Button(
                onClick = {
                    val adifStr = viewModel.exportAdif(context)
                    if (adifStr.isNotEmpty()) {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = ClipData.newPlainText("QSO ADIF Logs", adifStr)
                        clipboard.setPrimaryClip(clip)
                        Toast.makeText(context, "ADIF logs copied to clipboard!", Toast.LENGTH_LONG).show()
                    } else {
                        Toast.makeText(context, "Log is empty!", Toast.LENGTH_SHORT).show()
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = RadioPanel),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                modifier = Modifier.height(28.dp),
                shape = RoundedCornerShape(4.dp)
            ) {
                Text(text = "EXPORT ADIF", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .border(1.dp, RadioPanel, RoundedCornerShape(8.dp))
                .background(RadioCharcoal)
                .padding(4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (viewModel.qsoList.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = "No contacts logged yet.", color = Color.Gray, fontSize = 12.sp)
                    }
                }
            } else {
                items(viewModel.qsoList) { qso ->
                    QsoRow(qso = qso) {
                        viewModel.deleteQso(qso.id)
                    }
                }
            }
        }
    }
}

@Composable
fun QsoRow(qso: Qso, onDelete: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        colors = CardDefaults.cardColors(containerColor = RadioPanel),
        shape = RoundedCornerShape(6.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(text = qso.callsign, color = RadioOrange, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text(text = formatQsoTime(qso.timestamp), color = Color.Gray, fontSize = 10.sp)
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(text = formatFrequency(qso.frequencyHz), color = Color.White, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                Text(text = qso.mode, color = Color.Gray, fontSize = 10.sp)
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "S:${qso.rstSent} R:${qso.rstRcvd}",
                    color = Color.White,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    text = "❌",
                    color = Color.Red,
                    fontSize = 12.sp,
                    modifier = Modifier
                        .clickable { onDelete() }
                        .padding(4.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DxSpotsPanel(viewModel: MainViewModel, dxSpotsList: List<DxSpot>, isConnected: Boolean, context: Context) {
    var serverAddr by remember { mutableStateOf(viewModel.dxServerAddress) }
    var serverPort by remember { mutableStateOf(viewModel.dxServerPort.toString()) }
    var callsignInput by remember { mutableStateOf(viewModel.myCallsign) }

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Connect control panel
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
                Text(
                    text = "DX CLUSTER TELNET CONNECT",
                    color = RadioOrange,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = serverAddr,
                        onValueChange = { serverAddr = it },
                        label = { Text("Server", color = Color.Gray, fontSize = 10.sp) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = RadioOrange,
                            unfocusedBorderColor = RadioPanel,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        singleLine = true,
                        modifier = Modifier.weight(1.8f),
                        shape = RoundedCornerShape(8.dp)
                    )

                    OutlinedTextField(
                        value = serverPort,
                        onValueChange = { serverPort = it },
                        label = { Text("Port", color = Color.Gray, fontSize = 10.sp) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = RadioOrange,
                            unfocusedBorderColor = RadioPanel,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        singleLine = true,
                        modifier = Modifier.weight(0.8f),
                        shape = RoundedCornerShape(8.dp)
                    )

                    OutlinedTextField(
                        value = callsignInput,
                        onValueChange = { callsignInput = it },
                        label = { Text("Call", color = Color.Gray, fontSize = 10.sp) },
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

                Button(
                    onClick = {
                        if (isConnected) {
                            viewModel.disconnectDxCluster()
                        } else {
                            viewModel.connectDxCluster(
                                server = serverAddr,
                                port = serverPort.toIntOrNull() ?: 7373,
                                callsign = callsignInput
                            )
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isConnected) RadioRed else RadioOrange
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = if (isConnected) "DISCONNECT FROM CLUSTER" else "CONNECT TO CLUSTER",
                        color = if (isConnected) Color.White else Color.Black,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Spots list
        Text(
            text = "LIVE DX SPOTS (TAP SPOT TO QSY / TUNE)",
            color = Color.Gray,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
        )

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .border(1.dp, RadioPanel, RoundedCornerShape(8.dp))
                .background(RadioCharcoal)
                .padding(4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (dxSpotsList.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (isConnected) "Awaiting spots from cluster..." else "Connect to see live DX spots.",
                            color = Color.Gray,
                            fontSize = 12.sp
                        )
                    }
                }
            } else {
                items(dxSpotsList) { spot ->
                    DxSpotRow(spot = spot) {
                        // QSY: convert kHz to Hz
                        val hz = (spot.frequencyKhz * 1000).toLong()
                        viewModel.setFrequency(hz)
                        // Deduce appropriate mode based on frequency band
                        val mode = getAppropriateMode(hz)
                        viewModel.setMode(mode)
                        Toast.makeText(context, "Tuned to ${spot.dxCall} on ${formatFrequency(hz)}!", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }
}

@Composable
fun DxSpotRow(spot: DxSpot, onQsy: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onQsy() }
            .padding(vertical = 2.dp),
        colors = CardDefaults.cardColors(containerColor = RadioPanel),
        shape = RoundedCornerShape(6.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1.2f)) {
                Text(text = spot.dxCall, color = RadioAmber, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text(text = "de ${spot.spotter}", color = Color.Gray, fontSize = 10.sp)
                spot.distanceKm?.let { dist ->
                    Text(
                        text = "${dist.toInt()} km ${spot.bearing?.let { "@ ${it.toInt()}° " } ?: ""}${spot.direction ?: ""}",
                        color = RadioGreen,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Column(
                modifier = Modifier.weight(1.5f),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                val mhz = spot.frequencyKhz / 1000.0
                Text(
                    text = String.format(Locale.US, "%.4f MHz", mhz),
                    color = Color.White,
                    fontSize = 13.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
                Text(text = getBandLabel((spot.frequencyKhz * 1000).toLong()), color = Color.Gray, fontSize = 10.sp)
            }

            Column(
                modifier = Modifier.weight(2f),
                horizontalAlignment = Alignment.End
            ) {
                Text(
                    text = spot.comment,
                    color = Color.LightGray,
                    fontSize = 11.sp,
                    textAlign = TextAlign.End,
                    maxLines = 1
                )
                Text(text = spot.time, color = Color.Gray, fontSize = 9.sp)
            }
        }
    }
}

private fun formatFrequency(hz: Long): String {
    val mhz = hz / 1_000_000.0
    return String.format(Locale.US, "%.4f MHz", mhz)
}

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

private fun formatQsoTime(isoString: String): String {
    // Convert "2026-06-24T18:23:00Z" to "2026-06-24 18:23 UTC"
    return try {
        val parts = isoString.split("T")
        val date = parts[0]
        val time = parts[1].substring(0, 5)
        "$date $time UTC"
    } catch (e: Exception) {
        isoString
    }
}

private fun getAppropriateMode(freqHz: Long): Byte {
    return when {
        freqHz < 10000000L -> CatProtocol.MODE_LSB // 40m, 80m, 160m use LSB
        freqHz in 10000000L..30000000L -> CatProtocol.MODE_USB // 20m and up HF use USB
        freqHz in 30000000L..108000000L -> CatProtocol.MODE_FM
        freqHz > 108000000L -> CatProtocol.MODE_FM // VHF/UHF repeater default
        else -> CatProtocol.MODE_USB
    }
}

private fun getBandLabel(freqHz: Long): String {
    return when (freqHz) {
        in 1800000L..2000000L -> "160m"
        in 3500000L..4000000L -> "80m"
        in 7000000L..7300000L -> "40m"
        in 10100000L..10150000L -> "30m"
        in 14000000L..14350000L -> "20m"
        in 18068000L..18168000L -> "17m"
        in 21000000L..21450000L -> "15m"
        in 24890000L..24990000L -> "12m"
        in 28000000L..29700000L -> "10m"
        in 50000000L..54000000L -> "6m"
        in 144000000L..148000000L -> "2m"
        in 420000000L..450000000L -> "70cm"
        else -> "GEN"
    }
}
