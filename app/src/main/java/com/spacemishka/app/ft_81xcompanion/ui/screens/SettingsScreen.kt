package com.spacemishka.app.ft_81xcompanion.ui.screens

import android.bluetooth.BluetoothDevice
import android.widget.Toast
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.spacemishka.app.ft_81xcompanion.service.ConnectionState
import com.spacemishka.app.ft_81xcompanion.ui.MainViewModel
import com.spacemishka.app.ft_81xcompanion.ui.theme.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: MainViewModel) {
    val connState by viewModel.connectionState.collectAsState()
    val errMessage by viewModel.connectError.collectAsState()
    val isBound by viewModel.isServiceBound.collectAsState()
    
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val isConnected = connState == ConnectionState.CONNECTED

    var selectedDevice by remember { mutableStateOf<BluetoothDevice?>(null) }
    var showDeviceDropdown by remember { mutableStateOf(false) }
    val pairedDevices = remember { viewModel.getPairedDevices() }

    var selectedBaud by remember { mutableStateOf("9600") }
    var showBaudDropdown by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(RadioBlack)
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Bluetooth Connection Panel
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
                    text = "BLUETOOTH CAT ADAPTER",
                    color = RadioOrange,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )

                // Device selector
                Box {
                    Button(
                        onClick = { showDeviceDropdown = true },
                        colors = ButtonDefaults.buttonColors(containerColor = RadioPanel),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            @Suppress("MissingPermission")
                            val label = selectedDevice?.let { "${it.name} (${it.address})" } ?: "SELECT PAIRED DEVICE"
                            Text(text = label, color = Color.White, fontSize = 14.sp)
                            Text(text = "▼", color = Color.Gray, fontSize = 12.sp)
                        }
                    }

                    DropdownMenu(
                        expanded = showDeviceDropdown,
                        onDismissRequest = { showDeviceDropdown = false },
                        modifier = Modifier
                            .fillMaxWidth(0.85f)
                            .background(RadioPanel)
                    ) {
                        if (pairedDevices.isEmpty()) {
                            DropdownMenuItem(
                                text = { Text("No paired devices found", color = Color.Gray) },
                                onClick = { showDeviceDropdown = false }
                            )
                        } else {
                            pairedDevices.forEach { dev ->
                                @Suppress("MissingPermission")
                                DropdownMenuItem(
                                    text = { Text("${dev.name} [${dev.address}]", color = Color.White) },
                                    onClick = {
                                        selectedDevice = dev
                                        showDeviceDropdown = false
                                    }
                                )
                            }
                        }
                    }
                }

                // Connect/Disconnect Button
                val isConnecting = connState == ConnectionState.CONNECTING
                Button(
                    onClick = {
                        if (isConnected || isConnecting) {
                            viewModel.disconnectDevice()
                        } else {
                            selectedDevice?.let {
                                viewModel.connectDevice(it.address)
                            } ?: Toast.makeText(context, "Please select a Bluetooth device first!", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isConnected) RadioRed else RadioOrange
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    val btnLabel = when {
                        isConnecting -> "CONNECTING..."
                        isConnected -> "DISCONNECT"
                        else -> "CONNECT TO RADIO"
                    }
                    Text(
                        text = btnLabel,
                        color = if (isConnected) Color.White else Color.Black,
                        fontWeight = FontWeight.Bold
                    )
                }

                if (errMessage != null) {
                    Text(
                        text = "Status: $errMessage",
                        color = RadioRed,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // 2. Radio UART Parameters Panel
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
                    text = "UART PORT SETTINGS",
                    color = Color.Gray,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )

                // Baud Rate
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = "Baud Rate", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        Text(
                            text = "Must match radio Menu #14 (4800/9600/38400)",
                            color = Color.Gray,
                            fontSize = 11.sp
                        )
                    }

                    Box {
                        Button(
                            onClick = { showBaudDropdown = true },
                            colors = ButtonDefaults.buttonColors(containerColor = RadioPanel),
                            shape = RoundedCornerShape(6.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp)
                        ) {
                            Text(text = "$selectedBaud bps", color = RadioOrange, fontSize = 12.sp)
                        }

                        DropdownMenu(
                            expanded = showBaudDropdown,
                            onDismissRequest = { showBaudDropdown = false },
                            modifier = Modifier.background(RadioPanel)
                        ) {
                            listOf("4800", "9600", "38400").forEach { baud ->
                                DropdownMenuItem(
                                    text = { Text(text = "$baud bps", color = Color.White) },
                                    onClick = {
                                        selectedBaud = baud
                                        showBaudDropdown = false
                                    }
                                )
                            }
                        }
                    }
                }

                // Stop Bits info (Static)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(text = "Data Framing", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        Text(
                            text = "Yaesu default: 8 Data Bits, No Parity, 2 Stop Bits",
                            color = Color.Gray,
                            fontSize = 11.sp
                        )
                    }
                    Text(text = "8N2", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                }

                // PTT Safety Watchdog Status
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(text = "PTT Safety Timeout", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        Text(
                            text = "Automatically de-keys transmitter after limit",
                            color = Color.Gray,
                            fontSize = 11.sp
                        )
                    }
                    Text(text = "3 min (Mandatory)", color = RadioGreen, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }
        }

    }
}
