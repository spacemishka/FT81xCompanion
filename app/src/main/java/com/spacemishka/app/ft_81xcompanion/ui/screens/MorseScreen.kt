package com.spacemishka.app.ft_81xcompanion.ui.screens

import android.widget.Toast
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.spacemishka.app.ft_81xcompanion.service.CatProtocol
import com.spacemishka.app.ft_81xcompanion.service.ConnectionState
import com.spacemishka.app.ft_81xcompanion.service.MorseTranslator
import com.spacemishka.app.ft_81xcompanion.ui.MainViewModel
import com.spacemishka.app.ft_81xcompanion.ui.theme.*

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun MorseScreen(viewModel: MainViewModel) {
    val radioState by viewModel.radioState.collectAsState()
    val connState by viewModel.connectionState.collectAsState()
    val context = LocalContext.current

    var messageText by remember { mutableStateOf("") }
    val wpmSpeed = viewModel.morseWpm
    val farnsworthWpm = viewModel.morseFarnsworthWpm
    val sidetoneFreq = viewModel.morseSidetoneFreq

    var keyRadio by remember { mutableStateOf(false) }
    var playSound by remember { mutableStateOf(true) }
    var manualModeActive by remember { mutableStateOf(false) }
    var pausePollingForManual by remember { mutableStateOf(true) }

    val isConnected = connState == ConnectionState.CONNECTED
    val isMorseActive = radioState.isMorseActive

    // Automatically check / uncheck keyRadio based on connection status
    LaunchedEffect(isConnected) {
        if (!isConnected) {
            keyRadio = false
        }
    }

    // Manage polling when manual mode is toggled
    DisposableEffect(manualModeActive, pausePollingForManual) {
        if (manualModeActive && pausePollingForManual) {
            viewModel.pausePolling()
        } else {
            viewModel.resumePolling()
        }
        onDispose {
            viewModel.resumePolling()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(RadioBlack)
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Tab Headers to switch Auto vs Manual
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(RadioCharcoal)
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Button(
                onClick = { manualModeActive = false },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (!manualModeActive) RadioOrange else Color.Transparent,
                    contentColor = if (!manualModeActive) Color.Black else Color.Gray
                ),
                shape = RoundedCornerShape(6.dp),
                modifier = Modifier.weight(1f)
            ) {
                Text("AUTO KEYER", fontWeight = FontWeight.Bold)
            }

            Button(
                onClick = { manualModeActive = true },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (manualModeActive) RadioOrange else Color.Transparent,
                    contentColor = if (manualModeActive) Color.Black else Color.Gray
                ),
                shape = RoundedCornerShape(6.dp),
                modifier = Modifier.weight(1f)
            ) {
                Text("MANUAL KEY", fontWeight = FontWeight.Bold)
            }
        }

        // Warnings Check (e.g. if radio connection is required, or radio mode is not CW)
        if (keyRadio && isConnected && radioState.mode != CatProtocol.MODE_CW && radioState.mode != CatProtocol.MODE_CW_R) {
            Card(
                colors = CardDefaults.cardColors(containerColor = RadioRedGlow),
                border = BorderStroke(1.dp, RadioRed),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "WARNING: Radio is not in CW/CW-R mode. Please switch operating mode to CW to transmit Morse code RF carrier.",
                        color = RadioRed,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Button(
                        onClick = { viewModel.setMode(CatProtocol.MODE_CW) },
                        colors = ButtonDefaults.buttonColors(containerColor = RadioRed),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text("Switch Radio to CW Mode", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }
        }

        if (!manualModeActive) {
            // --- AUTO KEYER MODE ---
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = RadioCharcoal),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "TRANSMIT TEXT",
                        color = RadioOrange,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )

                    OutlinedTextField(
                        value = messageText,
                        onValueChange = { if (!isMorseActive) messageText = it },
                        placeholder = { Text("Type message here...", color = Color.Gray) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = RadioOrange,
                            unfocusedBorderColor = RadioPanel,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        enabled = !isMorseActive,
                        maxLines = 4,
                        shape = RoundedCornerShape(8.dp)
                    )

                    // Presets Section
                    Text(
                        text = "QUICK PRESETS",
                        color = Color.Gray,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                    
                    val myCall = viewModel.myCallsign
                    val presets = listOf(
                        "CQ CQ DE $myCall K" to "CQ CALL",
                        "SOS" to "SOS",
                        "559 TU E E" to "RST",
                        "DE $myCall K" to "SIGN OFF"
                    )

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        presets.forEach { (msg, label) ->
                            SuggestionChip(
                                onClick = { if (!isMorseActive) messageText = msg },
                                label = { Text(label, color = Color.White) },
                                colors = SuggestionChipDefaults.suggestionChipColors(
                                    containerColor = RadioPanel
                                ),
                                border = BorderStroke(1.dp, Color.Gray)
                            )
                        }
                    }
                }
            }
        } else {
            // --- MANUAL KEY MODE ---
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = RadioCharcoal),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "MANUAL STRAIGHT KEY PRACTICE",
                        color = RadioOrange,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Disable Radio Polling", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            Text("Prevents CAT traffic from interfering during manual keying", color = Color.Gray, fontSize = 11.sp)
                        }
                        Switch(
                            checked = pausePollingForManual,
                            onCheckedChange = { pausePollingForManual = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = RadioOrange,
                                checkedTrackColor = RadioOrangeGlow
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Giant Circular Touch Area representing the straight key
                    var isKeyPressed by remember { mutableStateOf(false) }
                    val keyScale by animateFloatAsState(
                        targetValue = if (isKeyPressed) 0.93f else 1.0f,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessLow
                        ),
                        label = "key_scale"
                    )

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(160.dp)
                                .scale(keyScale)
                                .clip(CircleShape)
                                .background(
                                    Brush.radialGradient(
                                        if (isKeyPressed) listOf(RadioOrange, RadioCharcoal)
                                        else listOf(RadioPanel, RadioBlack)
                                    )
                                )
                                .border(
                                    4.dp,
                                    if (isKeyPressed) RadioOrange else Color.Gray,
                                    CircleShape
                                    )
                                .shadow(if (isKeyPressed) 16.dp else 4.dp, CircleShape)
                                .pointerInput(playSound, keyRadio, sidetoneFreq) {
                                    awaitPointerEventScope {
                                        while (true) {
                                            awaitFirstDown()
                                            isKeyPressed = true
                                            viewModel.setManualKey(
                                                pressed = true,
                                                keyRadio = keyRadio,
                                                playSound = playSound,
                                                sidetoneFreqHz = sidetoneFreq.toInt()
                                            )
                                            waitForUpOrCancellation()
                                            isKeyPressed = false
                                            viewModel.setManualKey(
                                                pressed = false,
                                                keyRadio = keyRadio,
                                                playSound = playSound,
                                                sidetoneFreqHz = sidetoneFreq.toInt()
                                            )
                                        }
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "PRESS &\nHOLD",
                                color = if (isKeyPressed) Color.Black else Color.Gray,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
        }

        // --- KEYER AND SIDETONE CONFIGURATION PANEL ---
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = RadioCharcoal),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "SETTINGS & PARAMETERS",
                    color = Color.Gray,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )

                // Speed Slider (WPM)
                if (!manualModeActive) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Target Morse Speed", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            Text("${wpmSpeed.toInt()} WPM", color = RadioOrange, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        }
                        Slider(
                            value = wpmSpeed,
                            onValueChange = { 
                                viewModel.morseWpm = it
                                if (viewModel.morseFarnsworthWpm < it) {
                                    viewModel.morseFarnsworthWpm = it
                                }
                            },
                            valueRange = 5f..35f,
                            steps = 29,
                            colors = SliderDefaults.colors(
                                thumbColor = RadioOrange,
                                activeTrackColor = RadioOrange
                            ),
                            enabled = !isMorseActive
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Farnsworth Character Speed", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            Text("${farnsworthWpm.toInt()} WPM", color = RadioOrange, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        }
                        Slider(
                            value = farnsworthWpm,
                            onValueChange = { 
                                if (it >= wpmSpeed) {
                                    viewModel.morseFarnsworthWpm = it
                                }
                            },
                            valueRange = 5f..35f,
                            steps = 29,
                            colors = SliderDefaults.colors(
                                thumbColor = RadioOrange,
                                activeTrackColor = RadioOrange
                            ),
                            enabled = !isMorseActive
                        )
                    }
                }

                // Sidetone Pitch Slider (Hz)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Sidetone Tone Frequency", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        Text("${sidetoneFreq.toInt()} Hz", color = RadioOrange, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }
                    Slider(
                        value = sidetoneFreq,
                        onValueChange = { viewModel.morseSidetoneFreq = it },
                        valueRange = 400f..1000f,
                        steps = 12,
                        colors = SliderDefaults.colors(
                            thumbColor = RadioOrange,
                            activeTrackColor = RadioOrange
                        ),
                        enabled = !isMorseActive
                    )
                }

                // Audio Playback Toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Play Local Sidetone", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        Text("Sounds pure audio tone on the phone speaker", color = Color.Gray, fontSize = 11.sp)
                    }
                    Switch(
                        checked = playSound,
                        onCheckedChange = { if (!isMorseActive) playSound = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = RadioOrange,
                            checkedTrackColor = RadioOrangeGlow
                        ),
                        enabled = !isMorseActive
                    )
                }

                // Radio CAT Keying Toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Key Radio via CAT", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        val desc = if (isConnected) "Keys transmitter PTT over Bluetooth Classic connection" else "Not connected (Select BT adapter in Settings)"
                        Text(desc, color = if (isConnected) Color.Gray else RadioRed, fontSize = 11.sp)
                    }
                    Switch(
                        checked = keyRadio,
                        onCheckedChange = { if (!isMorseActive) keyRadio = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = RadioOrange,
                            checkedTrackColor = RadioOrangeGlow
                        ),
                        enabled = isConnected && !isMorseActive
                    )
                }
            }
        }

        // --- DYNAMIC TRANSMISSION STATUS DISPLAY (AUTO KEYER MODE) ---
        if (!manualModeActive && isMorseActive) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = RadioCharcoal),
                border = BorderStroke(1.dp, RadioOrange),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "TRANSMITTING...",
                        color = RadioOrange,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )

                    // Text display highlighting the active index
                    val annotatedString = buildAnnotatedString {
                        val text = radioState.morseText
                        val currentIdx = radioState.morseCurrentCharIndex
                        for (i in text.indices) {
                            if (i == currentIdx) {
                                withStyle(
                                    style = SpanStyle(
                                        color = RadioOrange,
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 24.sp
                                    )
                                ) {
                                    append(text[i])
                                }
                            } else {
                                withStyle(
                                    style = SpanStyle(
                                        color = if (i < currentIdx) Color.Gray else Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 18.sp
                                    )
                                ) {
                                    append(text[i])
                                }
                            }
                        }
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(RadioPanel, RoundedCornerShape(6.dp))
                            .padding(12.dp)
                    ) {
                        Text(
                            text = annotatedString,
                            textAlign = TextAlign.Start,
                            fontFamily = FontFamily.Monospace,
                            lineHeight = 32.sp
                        )
                    }
                }
            }
        }

        // --- TRANSMIT BUTTONS PANEL (AUTO KEYER MODE) ---
        if (!manualModeActive) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = {
                        if (isMorseActive) {
                            viewModel.stopMorse()
                        } else {
                            // Input Validation (E10)
                            val hasTranslatable = messageText.any { MorseTranslator.getMorse(it) != null || it == ' ' }
                            if (!hasTranslatable) {
                                Toast.makeText(context, "Message contains no translatable Morse characters!", Toast.LENGTH_SHORT).show()
                            } else {
                                viewModel.transmitMorse(
                                    text = messageText,
                                    wpm = wpmSpeed.toInt(),
                                    farnsworthWpm = farnsworthWpm.toInt(),
                                    sidetoneFreqHz = sidetoneFreq.toInt(),
                                    keyRadio = keyRadio,
                                    playSound = playSound
                                )
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isMorseActive) RadioRed else RadioOrange
                    ),
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp),
                    enabled = isMorseActive || messageText.isNotBlank()
                ) {
                    Text(
                        text = if (isMorseActive) "STOP" else "TRANSMIT MESSAGE",
                        color = if (isMorseActive) Color.White else Color.Black,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}


