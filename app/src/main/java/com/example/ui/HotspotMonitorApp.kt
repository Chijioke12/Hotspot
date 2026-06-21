package com.example.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.HotspotDevice
import com.example.data.HotspotLog
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HotspotMonitorApp(viewModel: HotspotViewModel) {
    val context = LocalContext.current
    val devices by viewModel.devices.collectAsStateWithLifecycle()
    val logs by viewModel.logs.collectAsStateWithLifecycle()
    val scanState by viewModel.scanState.collectAsStateWithLifecycle()
    val lastAlert by viewModel.lastAlert.collectAsStateWithLifecycle()

    var selectedTab by remember { mutableStateOf(0) }
    var deviceToEdit by remember { mutableStateOf<HotspotDevice?>(null) }
    var showBlockGuideByMac by remember { mutableStateOf<String?>(null) }

    // Modern Deep Security Palette overrides
    val primaryCyan = Color(0xFF00E5FF)
    val alertRed = Color(0xFFFF1744)
    val trustGreen = Color(0xFF00E676)
    val backgroundBg = MaterialTheme.colorScheme.background

    Scaffold(
        topBar = {
            LargeTopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Security,
                            contentDescription = null,
                            tint = primaryCyan,
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Hotspot Monitor",
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp,
                            fontFamily = FontFamily.SansSerif
                        )
                    }
                },
                colors = TopAppBarDefaults.largeTopAppBarColors(
                    containerColor = backgroundBg,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        },
        containerColor = backgroundBg
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
        ) {
            // Dismissible Urgent Warning Banner
            lastAlert?.let { alertMessage ->
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = alertRed.copy(alpha = 0.15f),
                        contentColor = alertRed
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .padding(16.dp)
                            .fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = "Intruder Alert",
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = alertMessage,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1.0f)
                        )
                        IconButton(onClick = { viewModel.dismissAlert() }) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Dismiss",
                                tint = alertRed
                            )
                        }
                    }
                }
            }

            // Radar Visual Header Card
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            val activeCount = devices.count { it.isCurrentlyActive }
                            Text(
                                text = "Active Shield Status",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.typography.labelLarge.color.copy(alpha = 0.7f)
                            )
                            Text(
                                text = when (scanState) {
                                    is ScanState.Scanning -> "Scanning Network..."
                                    else -> if (activeCount > 0) "$activeCount Device(s) Connected" else "No Active Devices Found"
                                },
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (devices.any { it.isCurrentlyActive && it.status == "FLAGGED" }) alertRed else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        // Hotspot Tethering System Settings Shortcut
                        OutlinedButton(
                            onClick = {
                                try {
                                    val intent = Intent().apply {
                                        action = Intent.ACTION_MAIN
                                        setClassName("com.android.settings", "com.android.settings.TetherSettings")
                                    }
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    try {
                                        val intentFallback = Intent(Settings.ACTION_WIRELESS_SETTINGS)
                                        context.startActivity(intentFallback)
                                    } catch (ex: Exception) {
                                        Toast.makeText(context, "Could not open tethering settings. Please open manually.", Toast.LENGTH_LONG).show()
                                    }
                                }
                            },
                            modifier = Modifier.testTag("hotspot_settings_button"),
                            shape = RoundedCornerShape(12.dp),
                            border = ButtonDefaults.outlinedButtonBorder.copy(width = 1.dp)
                        ) {
                            Icon(Icons.Default.WifiTethering, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("System Settings", fontSize = 12.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // Pulsing Radar Scanner Graphic
                    val onSurfaceVariantColor = MaterialTheme.colorScheme.onSurfaceVariant
                    val outlineColor = MaterialTheme.colorScheme.outline
                    val surfaceColor = MaterialTheme.colorScheme.surface
                    val surfaceVariantColor = MaterialTheme.colorScheme.surfaceVariant

                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.size(140.dp)
                    ) {
                        val infiniteTransition = rememberInfiniteTransition(label = "RadarPulse")
                        
                        // Outer Pulsing Ring
                        val pulseScale by infiniteTransition.animateFloat(
                            initialValue = 1.0f,
                            targetValue = 1.6f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(durationMillis = 2000, easing = LinearOutSlowInEasing),
                                repeatMode = RepeatMode.Restart
                            ),
                            label = "PulseScale"
                        )
                        val pulseAlpha by infiniteTransition.animateFloat(
                            initialValue = 0.4f,
                            targetValue = 0.0f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(durationMillis = 2000, easing = LinearOutSlowInEasing),
                                repeatMode = RepeatMode.Restart
                            ),
                            label = "PulseAlpha"
                        )

                        // Rotating Sweeper
                        val rotationAngle by infiniteTransition.animateFloat(
                            initialValue = 0f,
                            targetValue = 360f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(durationMillis = 3000, easing = LinearEasing),
                                repeatMode = RepeatMode.Restart
                            ),
                            label = "Rotation"
                        )

                        val isScanning = scanState is ScanState.Scanning

                        // Background circular guides
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            drawCircle(
                                color = if (isScanning) primaryCyan.copy(alpha = 0.1f) else onSurfaceVariantColor.copy(alpha = 0.05f),
                                radius = size.minDimension / 2
                            )
                            drawCircle(
                                color = if (isScanning) primaryCyan.copy(alpha = 0.15f) else onSurfaceVariantColor.copy(alpha = 0.08f),
                                radius = size.minDimension / 3,
                                style = Stroke(width = 1.dp.toPx())
                            )
                            drawCircle(
                                color = if (isScanning) primaryCyan.copy(alpha = 0.2f) else onSurfaceVariantColor.copy(alpha = 0.12f),
                                radius = size.minDimension / 4,
                                style = Stroke(width = 1.dp.toPx())
                            )
                        }

                        // Pulses
                        if (isScanning) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .rotate(rotationAngle)
                            ) {
                                Canvas(modifier = Modifier.fillMaxSize()) {
                                    drawArc(
                                        brush = Brush.sweepGradient(
                                            0.0f to primaryCyan.copy(alpha = 0.5f),
                                            0.2f to primaryCyan.copy(alpha = 0.1f),
                                            1.0f to Color.Transparent
                                        ),
                                        startAngle = 0f,
                                        sweepAngle = 90f,
                                        useCenter = true
                                    )
                                }
                            }
                        }

                        // Outer glowing pulse ring
                        Box(
                            modifier = Modifier
                                .size(90.dp)
                                .rotate(if (isScanning) rotationAngle else 0f)
                        ) {
                            Canvas(modifier = Modifier.fillMaxSize()) {
                                if (isScanning) {
                                    drawCircle(
                                        color = primaryCyan.copy(alpha = pulseAlpha),
                                        radius = (size.minDimension / 2) * pulseScale,
                                        style = Stroke(width = 2.dp.toPx())
                                    )
                                }
                                drawCircle(
                                    color = if (isScanning) primaryCyan else outlineColor,
                                    radius = size.minDimension / 2,
                                    style = Stroke(width = 2.dp.toPx())
                                )
                            }
                        }

                        // Center Shield Icon
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(CircleShape)
                                .background(
                                    Brush.radialGradient(
                                        colors = listOf(
                                            if (isScanning) primaryCyan.copy(alpha = 0.8f) else surfaceColor,
                                            if (isScanning) primaryCyan.copy(alpha = 0.3f) else surfaceVariantColor
                                        )
                                    )
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (devices.any { it.isCurrentlyActive && it.status == "FLAGGED" }) {
                                    Icons.Default.Warning
                                } else {
                                    Icons.Default.Shield
                                },
                                contentDescription = null,
                                tint = if (devices.any { it.isCurrentlyActive && it.status == "FLAGGED" }) {
                                    alertRed
                                } else if (isScanning) {
                                    Color.White
                                } else {
                                    MaterialTheme.colorScheme.primary
                                },
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // Scanning actions & Progress status
                    AnimatedContent(
                        targetState = scanState,
                        label = "ScanStateControls"
                    ) { state ->
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            when (state) {
                                is ScanState.Scanning -> {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(
                                            text = "Analyzing Subnet... ${state.progress}%",
                                            fontWeight = FontWeight.Bold,
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))
                                        LinearProgressIndicator(
                                            progress = { state.progress.toFloat() / 100f },
                                            modifier = Modifier
                                                .fillMaxWidth(0.8f)
                                                .height(6.dp)
                                                .clip(RoundedCornerShape(3.dp)),
                                            color = primaryCyan,
                                            trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = "Found ${state.foundCount} Active Devices",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                                        )
                                    }
                                }

                                else -> {
                                    Button(
                                        onClick = { viewModel.startScan() },
                                        modifier = Modifier
                                            .fillMaxWidth(0.9f)
                                            .height(52.dp)
                                            .testTag("scan_button"),
                                        shape = RoundedCornerShape(16.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = MaterialTheme.colorScheme.primary
                                        ),
                                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Refresh,
                                            contentDescription = "Scan"
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = if (state is ScanState.Success) "Rescan Hotspot" else "Scan Connected Devices",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 16.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Tabs panel
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = Color.Transparent,
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
            ) {
                val tabs = listOf("Connected", "Saved Shield", "Shield Logs")
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = {
                            Text(
                                text = title,
                                fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Medium
                            )
                        }
                    )
                }
            }

            // Main Tab content view container
            Box(modifier = Modifier.weight(1.0f)) {
                when (selectedTab) {
                    0 -> ConnectedDevicesTab(
                        devices = devices.filter { it.isCurrentlyActive },
                        onEditDevice = { deviceToEdit = it },
                        onBlockGuide = { showBlockGuideByMac = it }
                    )

                    1 -> SavedShieldTab(
                        devices = devices,
                        onEditDevice = { deviceToEdit = it },
                        onBlockGuide = { showBlockGuideByMac = it }
                    )

                    2 -> ShieldLogsTab(
                        logs = logs,
                        onClearLogs = { viewModel.clearHistory() }
                    )
                }
            }
        }
    }

    // Modal Sheet / Custom Dialog to edit Device Profile
    deviceToEdit?.let { device ->
        var labelState by remember { mutableStateOf(device.customLabel) }
        var typeState by remember { mutableStateOf(device.deviceType) }
        var statusState by remember { mutableStateOf(device.status) }

        Dialog(onDismissRequest = { deviceToEdit = null }) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp)
                ) {
                    Text(
                        text = "Edit Device Profile",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    OutlinedTextField(
                        value = labelState,
                        onValueChange = { labelState = it },
                        label = { Text("Friendly Name") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("edit_device_label_input"),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Device types select chips
                    Text(
                        text = "Device Category:",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    val categories = listOf("MOBILE", "LAPTOP", "TABLET", "TV", "SMART_HOME", "UNKNOWN")
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        // Display chips in small grid
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                categories.take(3).forEach { cat ->
                                    val isSelected = typeState == cat
                                    FilterChip(
                                        selected = isSelected,
                                        onClick = { typeState = cat },
                                        label = { Text(cat, fontSize = 11.sp) }
                                    )
                                }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                categories.drop(3).forEach { cat ->
                                    val isSelected = typeState == cat
                                    FilterChip(
                                        selected = isSelected,
                                        onClick = { typeState = cat },
                                        label = { Text(cat, fontSize = 11.sp) }
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Status selection
                    Text(
                        text = "Security Shield Status:",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Button(
                            onClick = { statusState = "APPROVED" },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (statusState == "APPROVED") trustGreen else MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = if (statusState == "APPROVED") Color.Black else MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            modifier = Modifier.weight(1f).height(44.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Trusted", fontWeight = FontWeight.SemiBold)
                        }

                        Button(
                            onClick = { statusState = "FLAGGED" },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (statusState == "FLAGGED") alertRed else MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = if (statusState == "FLAGGED") Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            modifier = Modifier.weight(1f).height(44.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Alert Check", fontWeight = FontWeight.SemiBold)
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(
                            onClick = {
                                viewModel.removeDevice(device.ipAddress)
                                deviceToEdit = null
                            },
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = null, tint = alertRed)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Forget", color = alertRed)
                        }

                        Row {
                            TextButton(onClick = { deviceToEdit = null }) {
                                Text("Cancel")
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(
                                onClick = {
                                    viewModel.updateDevice(
                                        ipAddress = device.ipAddress,
                                        customLabel = labelState,
                                        deviceType = typeState,
                                        status = statusState
                                    )
                                    deviceToEdit = null
                                },
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("Save Settings")
                            }
                        }
                    }
                }
            }
        }
    }

    // Comprehensive blocking instructions popup for modern Android
    showBlockGuideByMac?.let { mac ->
        Dialog(onDismissRequest = { showBlockGuideByMac = null }) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Security, contentDescription = null, tint = alertRed, modifier = Modifier.size(28.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Access Lockout Instructions",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "Because Google sandbox privacy guidelines restrict third-party apps from modifying dynamic system firewalls directly, blocking must be finalized in system configurations. Simply follow these easy steps:",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Copy MAC option
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text("Target Device MAC Address:", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
                                Text(mac.uppercase(), fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, fontSize = 14.sp)
                            }
                            IconButton(
                                onClick = {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    val clip = ClipData.newPlainText("MAC Address", mac)
                                    clipboard.setPrimaryClip(clip)
                                    Toast.makeText(context, "MAC Copied to Clipboard!", Toast.LENGTH_SHORT).show()
                                }
                            ) {
                                Icon(Icons.Default.ContentCopy, contentDescription = "Copy MAC")
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Steps List
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row {
                            Text("1. ", fontWeight = FontWeight.Bold)
                            Text("Tap the button below to launch system WiFi Tethering configurations.")
                        }
                        Row {
                            Text("2. ", fontWeight = FontWeight.Bold)
                            Text("Locate Connected Users or the Device List section.")
                        }
                        Row {
                            Text("3. ", fontWeight = FontWeight.Bold)
                            Text("Tap the target device and select Block, Add to Disallowed list, or input the copied MAC address under MAC Address Filtering.")
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = { showBlockGuideByMac = null }) {
                            Text("Close")
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                try {
                                    val intent = Intent().apply {
                                        action = Intent.ACTION_MAIN
                                        setClassName("com.android.settings", "com.android.settings.TetherSettings")
                                    }
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    try {
                                        context.startActivity(Intent(Settings.ACTION_WIRELESS_SETTINGS))
                                    } catch (ex: Exception) {
                                        Toast.makeText(context, "Settings launch failure. Navigate from drawer.", Toast.LENGTH_SHORT).show()
                                    }
                                }
                                showBlockGuideByMac = null
                            },
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.WifiTethering, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Open Tether Settings")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ConnectedDevicesTab(
    devices: List<HotspotDevice>,
    onEditDevice: (HotspotDevice) -> Unit,
    onBlockGuide: (String) -> Unit
) {
    if (devices.isEmpty()) {
        EmptyCollectionPlaceholder(
            icon = Icons.Default.WifiOff,
            title = "No Connections Discovered",
            tip = "Enable your mobile hotspot, invite other devices to connect, and perform a Network Scan to find them."
        )
    } else {
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(vertical = 8.dp)
        ) {
            items(devices, key = { it.ipAddress }) { device ->
                DeviceCardItem(
                    device = device,
                    onEditProfile = { onEditDevice(device) },
                    onBlock = { onBlockGuide(device.macAddress) }
                )
            }
        }
    }
}

@Composable
fun SavedShieldTab(
    devices: List<HotspotDevice>,
    onEditDevice: (HotspotDevice) -> Unit,
    onBlockGuide: (String) -> Unit
) {
    if (devices.isEmpty()) {
        EmptyCollectionPlaceholder(
            icon = Icons.Outlined.Shield,
            title = "Security Registry is Empty",
            tip = "Any devices discovered during scanning are securely indexed here where you can categorize and flag intruders."
        )
    } else {
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(vertical = 8.dp)
        ) {
            items(devices, key = { it.ipAddress }) { device ->
                DeviceCardItem(
                    device = device,
                    onEditProfile = { onEditDevice(device) },
                    onBlock = { onBlockGuide(device.macAddress) }
                )
            }
        }
    }
}

@Composable
fun ShieldLogsTab(
    logs: List<HotspotLog>,
    onClearLogs: () -> Unit
) {
    if (logs.isEmpty()) {
        EmptyCollectionPlaceholder(
            icon = Icons.Outlined.NotificationsActive,
            title = "Logs are Clear",
            tip = "Security activities, connection join histories, and priority intruder warnings will appear here in real-time."
        )
    } else {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Most Recent Security Reports",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f)
                )
                TextButton(onClick = onClearLogs) {
                    Icon(Icons.Default.ClearAll, contentDescription = "Clear")
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Clear Logs")
                }
            }
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                items(logs, key = { it.id }) { log ->
                    LogReportCard(log)
                }
            }
        }
    }
}

@Composable
fun DeviceCardItem(
    device: HotspotDevice,
    onEditProfile: () -> Unit,
    onBlock: () -> Unit
) {
    val typeIcon = when (device.deviceType.uppercase()) {
        "MOBILE" -> Icons.Default.PhoneAndroid
        "LAPTOP" -> Icons.Default.Laptop
        "TABLET" -> Icons.Default.TabletAndroid
        "TV" -> Icons.Default.Tv
        "SMART_HOME" -> Icons.Default.SmartButton
        else -> Icons.Default.DevicesOther
    }

    val primaryCyan = Color(0xFF00E5FF)
    val alertRed = Color(0xFFFF1744)
    val trustGreen = Color(0xFF00E676)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onEditProfile() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = typeIcon,
                            contentDescription = "Device Category",
                            tint = if (device.status == "FLAGGED") alertRed else MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column {
                        Text(
                            text = device.customLabel.ifEmpty { "Generic Device" },
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.bodyLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = device.ipAddress,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                                fontFamily = FontFamily.Monospace
                            )
                            if (device.responseTimeMs > 0) {
                                Spacer(modifier = Modifier.width(8.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.NetworkPing,
                                        contentDescription = null,
                                        modifier = Modifier.size(10.dp),
                                        tint = trustGreen
                                    )
                                    Spacer(modifier = Modifier.width(2.dp))
                                    Text(
                                        text = "${device.responseTimeMs}ms",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = trustGreen
                                    )
                                }
                            }
                        }
                    }
                }

                // Connection indicator / block action
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Badge status
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = when {
                                device.status == "FLAGGED" -> alertRed.copy(alpha = 0.15f)
                                device.status == "APPROVED" -> trustGreen.copy(alpha = 0.15f)
                                else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.1f)
                            },
                            contentColor = when {
                                device.status == "FLAGGED" -> alertRed
                                device.status == "APPROVED" -> trustGreen
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        ),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Text(
                            text = when {
                                device.status == "APPROVED" -> "Trusted"
                                device.status == "FLAGGED" -> "Intruder"
                                else -> "New Device"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }

                    // Blocking Action Button
                    IconButton(
                        onClick = onBlock,
                        modifier = Modifier.testTag("device_block_button_${device.ipAddress}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Block,
                            contentDescription = "Block Access",
                            tint = alertRed,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            // Expanded technical info
            Spacer(modifier = Modifier.height(8.dp))
            Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "MAC: " + device.macAddress.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    text = "Last seen: " + SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date(device.lastSeen)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            }
        }
    }
}

@Composable
fun LogReportCard(log: HotspotLog) {
    val isAlert = log.eventType == "ALERT_TRIGGERED"
    val colorAccent = if (isAlert) Color(0xFFFF1744) else Color(0xFF00E676)
    val icon = if (isAlert) Icons.Default.Warning else Icons.Default.Login

    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f)
        ),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(colorAccent.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = colorAccent,
                    modifier = Modifier.size(18.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (isAlert) "PREVENTED INTRUSION ALERT" else "DEVICE CONNECTION INBOUND",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = colorAccent,
                    letterSpacing = 0.5.sp
                )
                Text(
                    text = "${log.deviceLabel} (${log.ipAddress})",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Text(
                text = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date(log.timestamp)),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
        }
    }
}

@Composable
fun EmptyCollectionPlaceholder(
    icon: ImageVector,
    title: String,
    tip: String
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                modifier = Modifier.size(36.dp)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = tip,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
    }
}
