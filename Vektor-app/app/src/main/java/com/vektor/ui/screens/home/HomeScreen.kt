package com.vektor.ui.screens.home

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.vektor.lockscreen.LockScreenOverlayService
import com.vektor.sensor.SensorMonitorService

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavigateToChat: () -> Unit,
    onNavigateToQr: () -> Unit,
    onNavigateToHealth: () -> Unit,
    onManualSos: () -> Unit,
    onLogout: () -> Unit = {},
    viewModel: HomeViewModel = hiltViewModel()
) {
    val profile by viewModel.profile.collectAsState()
    val context = LocalContext.current
    var showOverlayDialog by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }

    // Track overlay state via SharedPreferences
    val overlayPrefs = remember {
        context.getSharedPreferences("overlay_prefs", Context.MODE_PRIVATE)
    }
    var overlayEnabled by remember {
        mutableStateOf(overlayPrefs.getBoolean("overlay_enabled", false))
    }

    // Start sensor monitoring service when home screen is shown
    LaunchedEffect(Unit) {
        val intent = Intent(context, SensorMonitorService::class.java)
        context.startForegroundService(intent)
    }

    // Overlay permission dialog
    if (showOverlayDialog) {
        AlertDialog(
            onDismissRequest = { showOverlayDialog = false },
            title = { Text("Lock Screen QR") },
            text = {
                Text(
                    "To show your emergency QR on the lock screen, Vektor needs the " +
                    "\"Display over other apps\" permission.\n\n" +
                    "Tap Open Settings, enable it for Vektor, then come back."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showOverlayDialog = false
                    val intent = Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:${context.packageName}")
                    )
                    context.startActivity(intent)
                }) { Text("Open Settings") }
            },
            dismissButton = {
                TextButton(onClick = { showOverlayDialog = false }) { Text("Cancel") }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Vektor") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.primary
                ),
                actions = {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Menu")
                    }
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Logout") },
                            onClick = {
                                showMenu = false
                                viewModel.logout()
                                onLogout()
                            }
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onManualSos,
                containerColor = MaterialTheme.colorScheme.error,
                contentColor = MaterialTheme.colorScheme.onError
            ) {
                Icon(Icons.Default.Warning, contentDescription = "Manual SOS")
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Profile card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Profile Monitor", style = MaterialTheme.typography.titleLarge)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Active User: ${profile?.name ?: "Loading…"}")
                    Text("Status: Background Sensors Running")
                    if (profile != null) {
                        Text("Blood Group: ${profile!!.bloodGroup}")
                        if (profile!!.allergies.isNotEmpty()) {
                            Text(
                                "Allergies: ${profile!!.allergies.joinToString(", ")}",
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }

            // Lock screen QR toggle button
            OutlinedButton(
                onClick = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
                        !Settings.canDrawOverlays(context)
                    ) {
                        showOverlayDialog = true
                    } else {
                        if (overlayEnabled) {
                            // Hide overlay
                            val intent = Intent(context, LockScreenOverlayService::class.java).apply {
                                action = LockScreenOverlayService.ACTION_HIDE_OVERLAY
                            }
                            context.startService(intent)
                            overlayEnabled = false
                            overlayPrefs.edit().putBoolean("overlay_enabled", false).apply()
                        } else {
                            // Show overlay
                            val intent = Intent(context, LockScreenOverlayService::class.java).apply {
                                action = LockScreenOverlayService.ACTION_SHOW_OVERLAY
                            }
                            context.startService(intent)
                            overlayEnabled = true
                            overlayPrefs.edit().putBoolean("overlay_enabled", true).apply()
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
            ) {
                Icon(Icons.Default.Lock, contentDescription = "Lock screen QR")
                Spacer(modifier = Modifier.width(8.dp))
                Text(if (overlayEnabled) "Hide QR from Lock Screen" else "Show QR on Lock Screen")
            }
        }
    }
}
