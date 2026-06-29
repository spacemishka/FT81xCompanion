package com.spacemishka.app.ft_81xcompanion

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.spacemishka.app.ft_81xcompanion.ui.theme.RadioPanel
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.core.content.ContextCompat
import com.spacemishka.app.ft_81xcompanion.ui.MainViewModel
import com.spacemishka.app.ft_81xcompanion.ui.screens.DashboardScreen
import com.spacemishka.app.ft_81xcompanion.ui.screens.LoggingScreen
import com.spacemishka.app.ft_81xcompanion.ui.screens.SatelliteScreen
import com.spacemishka.app.ft_81xcompanion.ui.screens.SettingsScreen
import com.spacemishka.app.ft_81xcompanion.ui.screens.MorseScreen
import com.spacemishka.app.ft_81xcompanion.ui.theme.FT81xCompanionTheme
import com.spacemishka.app.ft_81xcompanion.ui.theme.RadioBlack
import com.spacemishka.app.ft_81xcompanion.ui.theme.RadioCharcoal
import com.spacemishka.app.ft_81xcompanion.ui.theme.RadioOrange

class MainActivity : ComponentActivity() {

    companion object {
        private const val TAG = "MainActivity"
    }

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "MainActivity onCreate")
        enableEdgeToEdge()
        
        // Bind foreground service
        viewModel.bindService(this)

        setContent {
            FT81xCompanionTheme(darkTheme = true, dynamicColor = false) {
                var permissionsGranted by remember { mutableStateOf(checkAllPermissions()) }

                val requestPermissionLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestMultiplePermissions()
                ) { results ->
                    val allGranted = results.values.all { it }
                    permissionsGranted = allGranted
                    if (!allGranted) {
                        Log.w(TAG, "Some required permissions were denied by the user")
                    } else {
                        Log.d(TAG, "All permissions granted by the user")
                        viewModel.loadDatabaseContents() // reload/init with location etc
                    }
                }

                LaunchedEffect(Unit) {
                    if (!permissionsGranted) {
                        requestPermissions(requestPermissionLauncher)
                    }
                }

                if (permissionsGranted) {
                    MainAppContent(viewModel)
                } else {
                    PermissionRequestScreen {
                        requestPermissions(requestPermissionLauncher)
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        Log.d(TAG, "MainActivity onDestroy")
        viewModel.unbindService(this)
        super.onDestroy()
    }

    // Permission Helpers

    private fun checkAllPermissions(): Boolean {
        val permissions = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
            permissions.add(Manifest.permission.BLUETOOTH_SCAN)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        return permissions.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun requestPermissions(launcher: androidx.activity.result.ActivityResultLauncher<Array<String>>) {
        val permissions = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
            permissions.add(Manifest.permission.BLUETOOTH_SCAN)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        launcher.launch(permissions.toTypedArray())
    }
}

@Composable
fun MainAppContent(viewModel: MainViewModel) {
    var selectedScreenIndex by remember { mutableIntStateOf(0) }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            NavigationBar(
                containerColor = RadioCharcoal,
                contentColor = Color.White
            ) {
                NavigationBarItem(
                    selected = selectedScreenIndex == 0,
                    onClick = { selectedScreenIndex = 0 },
                    icon = { Icon(Icons.Default.Home, contentDescription = "Dashboard") },
                    label = { Text("Dashboard", fontSize = 10.sp) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = RadioOrange,
                        selectedTextColor = RadioOrange,
                        unselectedIconColor = Color.Gray,
                        unselectedTextColor = Color.Gray,
                        indicatorColor = RadioBlack
                    )
                )

                NavigationBarItem(
                    selected = selectedScreenIndex == 1,
                    onClick = { selectedScreenIndex = 1 },
                    icon = { Icon(Icons.Default.LocationOn, contentDescription = "Satellite") },
                    label = { Text("Satellite", fontSize = 10.sp) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = RadioOrange,
                        selectedTextColor = RadioOrange,
                        unselectedIconColor = Color.Gray,
                        unselectedTextColor = Color.Gray,
                        indicatorColor = RadioBlack
                    )
                )

                NavigationBarItem(
                    selected = selectedScreenIndex == 2,
                    onClick = { selectedScreenIndex = 2 },
                    icon = { Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Morse/CW") },
                    label = { Text("Morse/CW", fontSize = 10.sp) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = RadioOrange,
                        selectedTextColor = RadioOrange,
                        unselectedIconColor = Color.Gray,
                        unselectedTextColor = Color.Gray,
                        indicatorColor = RadioBlack
                    )
                )

                NavigationBarItem(
                    selected = selectedScreenIndex == 3,
                    onClick = { selectedScreenIndex = 3 },
                    icon = { Icon(Icons.AutoMirrored.Filled.List, contentDescription = "Logbook") },
                    label = { Text("Logbook", fontSize = 10.sp) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = RadioOrange,
                        selectedTextColor = RadioOrange,
                        unselectedIconColor = Color.Gray,
                        unselectedTextColor = Color.Gray,
                        indicatorColor = RadioBlack
                    )
                )

                NavigationBarItem(
                    selected = selectedScreenIndex == 4,
                    onClick = { selectedScreenIndex = 4 },
                    icon = { Icon(Icons.Default.Settings, contentDescription = "Settings") },
                    label = { Text("Settings", fontSize = 10.sp) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = RadioOrange,
                        selectedTextColor = RadioOrange,
                        unselectedIconColor = Color.Gray,
                        unselectedTextColor = Color.Gray,
                        indicatorColor = RadioBlack
                    )
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(RadioBlack)
                .padding(innerPadding)
        ) {
            when (selectedScreenIndex) {
                0 -> DashboardScreen(viewModel = viewModel)
                1 -> SatelliteScreen(viewModel = viewModel)
                2 -> MorseScreen(viewModel = viewModel)
                3 -> LoggingScreen(viewModel = viewModel)
                4 -> SettingsScreen(viewModel = viewModel)
            }
        }
    }
}

@Composable
fun PermissionRequestScreen(onRequest: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = RadioBlack
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, RadioPanel, RoundedCornerShape(16.dp))
                    .padding(8.dp),
                colors = CardDefaults.cardColors(containerColor = RadioCharcoal),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "PERMISSIONS REQUIRED",
                        color = RadioOrange,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.ExtraBold,
                        textAlign = TextAlign.Center
                    )

                    Text(
                        text = "To interface with the Yaesu FT-818ND CAT controller, the app requires:\n\n" +
                               "• Bluetooth: To connect to the wireless serial adapter\n" +
                               "• Location: To calculate observer-relative satellite passes and Doppler shifts\n" +
                               "• Notifications: To display radio connection status in the background",
                        color = Color.LightGray,
                        fontSize = 13.sp,
                        lineHeight = 20.sp,
                        textAlign = TextAlign.Start
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Button(
                        onClick = onRequest,
                        colors = ButtonDefaults.buttonColors(containerColor = RadioOrange),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "GRANT PERMISSIONS",
                            color = Color.Black,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }
                }
            }
        }
    }
}