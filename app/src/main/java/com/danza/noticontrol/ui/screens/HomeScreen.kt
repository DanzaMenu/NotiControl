package com.danza.noticontrol.ui.screens

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.runtime.DisposableEffect
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import com.danza.noticontrol.data.SilencedNotification
import com.danza.noticontrol.ui.theme.*
import com.danza.noticontrol.updater.AppUpdater
import com.danza.noticontrol.updater.UpdateInfo
import com.danza.noticontrol.viewmodel.ActiveSilence
import com.danza.noticontrol.viewmodel.MainViewModel
import com.danza.noticontrol.widget.SilenceWidget
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun HomeScreen(
    viewModel: MainViewModel,
    onNavigateToLog: () -> Unit,
    onNavigateToAppSelector: () -> Unit
) {
    val context = LocalContext.current
    val activeSilence by viewModel.activeSilence.collectAsState()
    val liveNotifications by viewModel.selectedSessionNotifications.collectAsState()
    val blockMode by viewModel.blockMode.collectAsState()
    val blockedPackages by viewModel.blockedPackages.collectAsState()
    val durationType by viewModel.durationType.collectAsState()
    val presetMinutes by viewModel.presetMinutes.collectAsState()
    val customFromHour by viewModel.customFromHour.collectAsState()
    val customFromMin by viewModel.customFromMin.collectAsState()
    val customToHour by viewModel.customToHour.collectAsState()
    val customToMin by viewModel.customToMin.collectAsState()

    // Keep live notifications loaded for the active session
    LaunchedEffect(activeSilence?.sessionId) {
        activeSilence?.let { viewModel.loadNotificationsForSession(context, it.sessionId) }
    }

    // Auto-update check
    var updateInfo by remember { mutableStateOf<UpdateInfo?>(null) }
    var showUpdateDialog by remember { mutableStateOf(false) }
    var isDownloading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        val info = AppUpdater.checkForUpdate(context)
        if (info != null) {
            updateInfo = info
            showUpdateDialog = true
        }
    }

    if (showUpdateDialog && updateInfo != null) {
        AlertDialog(
            onDismissRequest = { showUpdateDialog = false },
            containerColor = Color(0xFF0D1B2E),
            title = {
                Text(
                    "Update Available",
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column {
                    Text(
                        "v${updateInfo!!.latestVersion} is available (you have v${AppUpdater.getCurrentVersion(context)})",
                        color = Color.White.copy(0.85f),
                        fontSize = 14.sp
                    )
                    if (updateInfo!!.releaseNotes.isNotBlank()) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            updateInfo!!.releaseNotes,
                            color = Color.White.copy(0.5f),
                            fontSize = 12.sp
                        )
                    }
                    if (isDownloading) {
                        Spacer(Modifier.height(12.dp))
                        LinearProgressIndicator(
                            modifier = Modifier.fillMaxWidth(),
                            color = AccentBlue
                        )
                        Spacer(Modifier.height(4.dp))
                        Text("Downloading…", color = Color.White.copy(0.5f), fontSize = 11.sp)
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        isDownloading = true
                        scope.launch {
                            try {
                                AppUpdater.downloadAndInstall(context, updateInfo!!.downloadUrl)
                            } catch (_: Exception) {
                                android.widget.Toast.makeText(context, "Download failed", android.widget.Toast.LENGTH_SHORT).show()
                            }
                            isDownloading = false
                            showUpdateDialog = false
                        }
                    },
                    enabled = !isDownloading
                ) {
                    Text("Update", color = if (isDownloading) Color.Gray else AccentBlue, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                if (!isDownloading) {
                    TextButton(onClick = { showUpdateDialog = false }) {
                        Text("Later", color = Color.White.copy(0.5f))
                    }
                }
            }
        )
    }

    var showCustomPicker by remember { mutableStateOf(false) }
    var fromHour by remember(customFromHour) { mutableStateOf(customFromHour) }
    var fromMin by remember(customFromMin) { mutableStateOf(customFromMin) }
    var toHour by remember(customToHour) { mutableStateOf(customToHour) }
    var toMin by remember(customToMin) { mutableStateOf(customToMin) }

    // Check notification listener permission — re-checks every time screen resumes
    var hasListenerPermission by remember { mutableStateOf(false) }
    val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                hasListenerPermission = android.provider.Settings.Secure.getString(
                    context.contentResolver,
                    "enabled_notification_listeners"
                )?.contains(context.packageName) == true
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val isSilenced = activeSilence != null
    val isOrbMode = activeSilence?.durationMinutes == -1

    val inf = rememberInfiniteTransition(label = "bg")
    val bgShift by inf.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(8000, easing = LinearEasing), RepeatMode.Reverse),
        label = "bgShift"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .drawBehind {
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(Color(0xFF0B1E3D), Color(0xFF050A14)),
                        center = Offset(size.width * 0.5f, size.height * (0.3f + bgShift * 0.08f)),
                        radius = size.height * 0.9f
                    )
                )
            }
    ) {
        // Glow blobs
        Box(
            modifier = Modifier
                .size(380.dp)
                .align(Alignment.TopCenter)
                .offset(y = (-60).dp)
                .drawBehind {
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                when {
                                    isOrbMode -> Color(0x40EF5350)
                                    isSilenced -> Color(0x40CE93D8)
                                    else -> Color(0x304FC3F7)
                                },
                                Color.Transparent
                            )
                        )
                    )
                }
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(16.dp))

            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        "Noti Control by Danza",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        letterSpacing = (-0.3).sp
                    )
                    Text(
                        "DanzaBot LLC  |  KingDanza.dev",
                        fontSize = 11.sp,
                        color = Color.White.copy(alpha = 0.35f),
                        letterSpacing = 0.3.sp
                    )
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .glassSurface(cornerRadius = 12.dp)
                        .clickable { onNavigateToLog() }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.History, contentDescription = null, tint = Color.White, modifier = Modifier.size(17.dp))
                        Spacer(Modifier.width(5.dp))
                        Text("History", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }

            Spacer(Modifier.height(28.dp))

            var showPinDialog by remember { mutableStateOf(false) }

            if (showPinDialog) {
                AlertDialog(
                    onDismissRequest = { showPinDialog = false },
                    containerColor = Color(0xFF0D1B2E),
                    title = { Text("Add to Home Screen", color = Color.White, fontWeight = FontWeight.Bold) },
                    text = { Text("Add the Noti Control bell to your home screen as a widget?", color = Color.White.copy(0.7f)) },
                    confirmButton = {
                        TextButton(onClick = {
                            showPinDialog = false
                            val manager = AppWidgetManager.getInstance(context)
                            val provider = ComponentName(context, SilenceWidget::class.java)
                            if (manager.isRequestPinAppWidgetSupported) {
                                val successIntent = android.app.PendingIntent.getBroadcast(
                                    context, 0,
                                    android.content.Intent("com.danza.noticontrol.WIDGET_PINNED").setPackage(context.packageName),
                                    android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
                                )
                                manager.requestPinAppWidget(provider, null, successIntent)
                                android.widget.Toast.makeText(context, "Widget added — check your home screen pages", android.widget.Toast.LENGTH_LONG).show()
                            } else {
                                android.widget.Toast.makeText(context, "Long-press your home screen → Widgets → Noti Control", android.widget.Toast.LENGTH_LONG).show()
                            }
                        }) { Text("Add", color = AccentBlue, fontWeight = FontWeight.Bold) }
                    },
                    dismissButton = {
                        TextButton(onClick = { showPinDialog = false }) {
                            Text("Cancel", color = Color.White.copy(0.5f))
                        }
                    }
                )
            }

            // Orb — tap to toggle indefinite silence, long-press to pin widget
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .pointerInput(isSilenced, isOrbMode) {
                        detectTapGestures(
                            onTap = {
                                when {
                                    isOrbMode -> viewModel.endSilenceEarly(context)
                                    !isSilenced -> viewModel.startOrbSilence(context)
                                }
                            },
                            onLongPress = { showPinDialog = true }
                        )
                    }
            ) {
                PulsingOrb(orbState = when {
                    isOrbMode -> OrbState.ORB
                    isSilenced -> OrbState.TIMED
                    else -> OrbState.IDLE
                }, size = 190.dp)
            }

            Spacer(Modifier.height(18.dp))

            // Status dot + label
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(when {
                            isOrbMode -> DangerRed
                            isSilenced -> AccentPurple
                            else -> Color(0xFF4CAF50)
                        })
                )
                Spacer(Modifier.width(7.dp))
                Text(
                    when {
                        isOrbMode -> "Silencing All — Tap to Stop"
                        isSilenced -> "Silence Mode On"
                        else -> "All notifications allowed"
                    },
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White.copy(alpha = 0.9f)
                )
            }

            if (!isSilenced) {
                Spacer(Modifier.height(6.dp))
                Text(
                    "Tap the bell to silence everything instantly",
                    fontSize = 12.sp,
                    color = Color.White.copy(alpha = 0.3f),
                    textAlign = TextAlign.Center
                )
            }

            Spacer(Modifier.height(28.dp))

            if (!hasListenerPermission) {
                PermissionCard(context)
            } else if (isOrbMode) {
                // Orb mode — just show the live silenced notifications
                OrbSilenceNotificationsCard(notifications = liveNotifications)
            } else if (isSilenced && activeSilence != null) {
                ActiveSilenceCard(
                    activeSilence = activeSilence!!,
                    notifications = liveNotifications,
                    onEndEarly = { viewModel.endSilenceEarly(context) }
                )
            } else {
                // Block mode toggle
                BlockModeToggle(
                    blockMode = blockMode,
                    onModeChange = { viewModel.setBlockMode(context, it) }
                )

                Spacer(Modifier.height(10.dp))

                // Selected apps entry if mode = selected
                AnimatedVisibility(visible = blockMode == "selected") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .glassSurface(cornerRadius = 14.dp)
                            .clickable { onNavigateToAppSelector() }
                            .padding(horizontal = 16.dp, vertical = 14.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Apps, contentDescription = null, tint = AccentBlue, modifier = Modifier.size(20.dp))
                                Spacer(Modifier.width(10.dp))
                                Column {
                                    Text("Select Apps to Silence", fontSize = 14.sp, color = Color.White, fontWeight = FontWeight.Medium)
                                    Text(
                                        if (blockedPackages.isEmpty()) "No apps selected" else "${blockedPackages.size} app${if (blockedPackages.size != 1) "s" else ""} selected",
                                        fontSize = 12.sp,
                                        color = if (blockedPackages.isEmpty()) DangerRed.copy(alpha = 0.8f) else AccentBlue
                                    )
                                }
                            }
                            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = Color.White.copy(alpha = 0.4f))
                        }
                    }
                }

                Spacer(Modifier.height(10.dp))

                // Duration card
                DurationCard(
                    durationType = durationType,
                    presetMinutes = presetMinutes,
                    showCustomPicker = showCustomPicker,
                    fromHour = fromHour, fromMin = fromMin,
                    toHour = toHour, toMin = toMin,
                    onFromHourChange = { fromHour = it },
                    onFromMinChange = { fromMin = it },
                    onToHourChange = { toHour = it },
                    onToMinChange = { toMin = it },
                    onShowCustom = { showCustomPicker = true },
                    onHideCustom = { showCustomPicker = false },
                    onPresetSelected = { mins ->
                        val canStart = blockMode == "all" || blockedPackages.isNotEmpty()
                        if (canStart) viewModel.startSilencePreset(context, mins)
                    },
                    onStartCustom = {
                        val canStart = blockMode == "all" || blockedPackages.isNotEmpty()
                        if (canStart) {
                            viewModel.startSilenceCustom(context, fromHour, fromMin, toHour, toMin)
                            showCustomPicker = false
                        }
                    },
                    blockMode = blockMode,
                    hasAppsSelected = blockedPackages.isNotEmpty()
                )
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
fun BlockModeToggle(blockMode: String, onModeChange: (String) -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .glassSurface(cornerRadius = 14.dp)
            .padding(6.dp)
    ) {
        Row {
            listOf("all" to "All Apps", "selected" to "Selected Apps").forEach { (mode, label) ->
                val selected = blockMode == mode
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            if (selected) Brush.linearGradient(listOf(AccentBlue.copy(alpha = 0.4f), AccentPurple.copy(alpha = 0.3f)))
                            else Brush.linearGradient(listOf(Color.Transparent, Color.Transparent))
                        )
                        .clickable { onModeChange(mode) }
                        .padding(vertical = 11.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        label,
                        fontSize = 14.sp,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                        color = if (selected) Color.White else Color.White.copy(alpha = 0.4f)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DurationCard(
    durationType: String,
    presetMinutes: Int,
    showCustomPicker: Boolean,
    fromHour: Int, fromMin: Int,
    toHour: Int, toMin: Int,
    onFromHourChange: (Int) -> Unit,
    onFromMinChange: (Int) -> Unit,
    onToHourChange: (Int) -> Unit,
    onToMinChange: (Int) -> Unit,
    onShowCustom: () -> Unit,
    onHideCustom: () -> Unit,
    onPresetSelected: (Int) -> Unit,
    onStartCustom: () -> Unit,
    blockMode: String,
    hasAppsSelected: Boolean
) {
    val canStart = blockMode == "all" || hasAppsSelected
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // Bottom sheet for custom picker
    if (showCustomPicker) {
        ModalBottomSheet(
            onDismissRequest = { onHideCustom() },
            sheetState = sheetState,
            containerColor = Color(0xFF0D1B2E),
            contentColor = Color.White,
            tonalElevation = 0.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Custom Time Range", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Spacer(Modifier.height(20.dp))

                Text("From", fontSize = 11.sp, color = Color.White.copy(0.4f), letterSpacing = 0.8.sp, modifier = Modifier.align(Alignment.Start))
                Spacer(Modifier.height(8.dp))
                TimePickerRow(hour = fromHour, minute = fromMin, onHourChange = onFromHourChange, onMinChange = onFromMinChange)

                Spacer(Modifier.height(16.dp))
                Text("To", fontSize = 11.sp, color = Color.White.copy(0.4f), letterSpacing = 0.8.sp, modifier = Modifier.align(Alignment.Start))
                Spacer(Modifier.height(8.dp))
                TimePickerRow(hour = toHour, minute = toMin, onHourChange = onToHourChange, onMinChange = onToMinChange)

                Spacer(Modifier.height(14.dp))
                val durationMins = calcDurationMins(fromHour, fromMin, toHour, toMin)
                Text("Duration: ${formatDuration(durationMins)}", fontSize = 13.sp, color = AccentBlue)

                Spacer(Modifier.height(20.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color.White.copy(alpha = 0.08f))
                            .clickable { onHideCustom() }
                            .padding(vertical = 14.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Cancel", color = Color.White.copy(0.6f), fontSize = 15.sp)
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(14.dp))
                            .background(
                                if (canStart)
                                    Brush.linearGradient(listOf(AccentBlue, AccentPurple))
                                else
                                    Brush.linearGradient(listOf(Color.White.copy(0.1f), Color.White.copy(0.05f)))
                            )
                            .clickable { if (canStart) { onStartCustom(); onHideCustom() } }
                            .padding(vertical = 14.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Start", color = if (canStart) Color.White else Color.White.copy(0.3f), fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    }
                }
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .glassSurface(cornerRadius = 20.dp)
            .padding(18.dp)
    ) {
        Column {
            Text(
                "DURATION",
                fontSize = 11.sp,
                color = Color.White.copy(alpha = 0.35f),
                fontWeight = FontWeight.Medium,
                letterSpacing = 1.2.sp
            )
            Spacer(Modifier.height(12.dp))

            // Preset chips
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(30 to "30m", 60 to "1h", 120 to "2h").forEach { (mins, label) ->
                    val isLast = durationType == "preset" && presetMinutes == mins
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .drawBehind {
                                drawRoundRect(
                                    brush = Brush.linearGradient(
                                        if (isLast)
                                            listOf(AccentBlue.copy(alpha = 0.4f), AccentPurple.copy(alpha = 0.3f))
                                        else
                                            listOf(Color.White.copy(alpha = 0.1f), Color.White.copy(alpha = 0.05f))
                                    ),
                                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(12.dp.toPx())
                                )
                                drawRoundRect(
                                    color = if (isLast) AccentBlue.copy(alpha = 0.6f) else Color.White.copy(alpha = 0.15f),
                                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(12.dp.toPx()),
                                    style = Stroke(1f)
                                )
                            }
                            .clickable { onPresetSelected(mins) }
                            .padding(vertical = 13.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            label,
                            color = if (isLast) Color.White else Color.White.copy(alpha = 0.7f),
                            fontWeight = if (isLast) FontWeight.Bold else FontWeight.Normal,
                            fontSize = 15.sp
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            // Custom button
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        if (durationType == "custom")
                            Brush.linearGradient(listOf(AccentBlue.copy(alpha = 0.2f), AccentPurple.copy(alpha = 0.15f)))
                        else
                            Brush.linearGradient(listOf(Color.White.copy(alpha = 0.06f), Color.White.copy(alpha = 0.03f)))
                    )
                    .drawBehind {
                        drawRoundRect(
                            color = if (durationType == "custom") AccentBlue.copy(0.4f) else Color.White.copy(alpha = 0.1f),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(12.dp.toPx()),
                            style = Stroke(1f)
                        )
                    }
                    .clickable { onShowCustom() }
                    .padding(vertical = 13.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Schedule, contentDescription = null, tint = if (durationType == "custom") AccentBlue else Color.White.copy(0.4f), modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(7.dp))
                    Text(
                        "Custom Time Range",
                        color = if (durationType == "custom") AccentBlue else Color.White.copy(0.5f),
                        fontSize = 14.sp
                    )
                }
            }

            // Warn if selected mode but no apps
            AnimatedVisibility(visible = blockMode == "selected" && !hasAppsSelected) {
                Column {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Select at least one app above to start silencing.",
                        fontSize = 12.sp,
                        color = DangerRed.copy(alpha = 0.8f),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

@Composable
fun TimePickerRow(hour: Int, minute: Int, onHourChange: (Int) -> Unit, onMinChange: (Int) -> Unit) {
    val displayHour = if (hour % 12 == 0) 12 else hour % 12
    val amPm = if (hour < 12) "AM" else "PM"

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Hour scroller
        TimeScrollColumn(
            value = displayHour,
            range = 1..12,
            label = "hr",
            modifier = Modifier.weight(1f),
            onValueChange = { h ->
                val newHour = if (hour < 12) {
                    if (h == 12) 0 else h
                } else {
                    if (h == 12) 12 else h + 12
                }
                onHourChange(newHour)
            }
        )

        Text(":", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Light)

        // Minute scroller
        TimeScrollColumn(
            value = minute,
            range = 0..59,
            label = "min",
            modifier = Modifier.weight(1f),
            onValueChange = onMinChange,
            format = { "%02d".format(it) }
        )

        // AM/PM toggle
        Column(
            modifier = Modifier.weight(0.8f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            listOf("AM", "PM").forEach { period ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            if (amPm == period) AccentBlue.copy(alpha = 0.3f) else Color.White.copy(alpha = 0.06f)
                        )
                        .clickable {
                            val isCurrentlyAm = hour < 12
                            val wantAm = period == "AM"
                            if (isCurrentlyAm != wantAm) {
                                onHourChange(if (wantAm) hour - 12 else hour + 12)
                            }
                        }
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        period,
                        fontSize = 13.sp,
                        color = if (amPm == period) AccentBlue else Color.White.copy(0.4f),
                        fontWeight = if (amPm == period) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
        }
    }
}

@Composable
fun TimeScrollColumn(
    value: Int,
    range: IntRange,
    label: String,
    modifier: Modifier = Modifier,
    onValueChange: (Int) -> Unit,
    format: (Int) -> String = { it.toString() }
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Up arrow
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(Color.White.copy(alpha = 0.06f))
                .clickable {
                    val next = if (value >= range.last) range.first else value + 1
                    onValueChange(next)
                }
                .padding(vertical = 6.dp),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.KeyboardArrowUp, contentDescription = null, tint = Color.White.copy(0.5f), modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.height(4.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .glassSurface(cornerRadius = 10.dp)
                .padding(vertical = 10.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(format(value), fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White)
        }
        Spacer(Modifier.height(4.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(Color.White.copy(alpha = 0.06f))
                .clickable {
                    val prev = if (value <= range.first) range.last else value - 1
                    onValueChange(prev)
                }
                .padding(vertical = 6.dp),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.KeyboardArrowDown, contentDescription = null, tint = Color.White.copy(0.5f), modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.height(2.dp))
        Text(label, fontSize = 10.sp, color = Color.White.copy(0.3f))
    }
}

private fun calcDurationMins(fromH: Int, fromM: Int, toH: Int, toM: Int): Int {
    var from = fromH * 60 + fromM
    var to = toH * 60 + toM
    if (to <= from) to += 24 * 60
    return to - from
}

private fun formatDuration(mins: Int): String {
    val h = mins / 60
    val m = mins % 60
    return when {
        h == 0 -> "${m}m"
        m == 0 -> "${h}h"
        else -> "${h}h ${m}m"
    }
}

private fun formatTime12h(customFromHour: Int, h: Int, m: Int, fromH: Int, fromM: Int, toH: Int, toM: Int): String = ""

enum class OrbState { IDLE, TIMED, ORB }

@Composable
fun PulsingOrb(orbState: OrbState, size: Dp) {
    val isSilenced = orbState != OrbState.IDLE
    val inf = rememberInfiniteTransition(label = "orb")
    val pulse by inf.animateFloat(
        initialValue = 0.93f, targetValue = 1.07f,
        animationSpec = infiniteRepeatable(tween(2000, easing = EaseInOutSine), RepeatMode.Reverse),
        label = "pulse"
    )
    val ringAlpha by inf.animateFloat(
        initialValue = 0.25f, targetValue = 0.65f,
        animationSpec = infiniteRepeatable(tween(1500, easing = EaseInOutSine), RepeatMode.Reverse),
        label = "ring"
    )

    val coreColor = when (orbState) {
        OrbState.ORB -> DangerRed
        OrbState.TIMED -> AccentPurple
        OrbState.IDLE -> AccentBlue
    }
    val glowColor = when (orbState) {
        OrbState.ORB -> Color(0x60EF5350)
        OrbState.TIMED -> Color(0x60CE93D8)
        OrbState.IDLE -> Color(0x504FC3F7)
    }

    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(size)) {
        Box(
            modifier = Modifier
                .size(size * pulse)
                .drawBehind {
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(glowColor, Color.Transparent),
                            radius = this.size.width / 2f
                        )
                    )
                }
        )
        Box(
            modifier = Modifier
                .size(size * 0.75f)
                .drawBehind {
                    drawCircle(color = coreColor.copy(alpha = ringAlpha * 0.4f), style = Stroke(1.5f))
                }
        )
        Box(
            modifier = Modifier
                .size(size * 0.58f)
                .clip(CircleShape)
                .drawBehind {
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Color.White.copy(0.14f), coreColor.copy(0.08f), Color.Transparent
                            ),
                            center = Offset(this.size.width * 0.35f, this.size.height * 0.3f),
                            radius = this.size.width * 0.7f
                        )
                    )
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(Color.Transparent, coreColor.copy(0.35f)),
                            radius = this.size.width / 2f
                        )
                    )
                    drawCircle(
                        brush = Brush.linearGradient(
                            colors = listOf(Color.White.copy(0.6f), coreColor.copy(0.3f), Color.White.copy(0.1f))
                        ),
                        style = Stroke(1.5f)
                    )
                }
        ) {
            Box(
                modifier = Modifier.fillMaxSize().drawBehind {
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(Color.White.copy(0.3f), Color.Transparent),
                            center = Offset(this.size.width * 0.3f, this.size.height * 0.25f),
                            radius = this.size.width * 0.3f
                        )
                    )
                }
            )
        }
        Icon(
            imageVector = if (isSilenced) Icons.Default.NotificationsOff else Icons.Default.NotificationsActive,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(size * 0.24f)
        )
    }
}

@Composable
fun OrbSilenceNotificationsCard(notifications: List<SilencedNotification>) {
    val timeSdf = SimpleDateFormat("h:mm:ss a", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("America/New_York")
    }
    val listState = rememberLazyListState()

    LaunchedEffect(notifications.size) {
        if (notifications.isNotEmpty()) listState.animateScrollToItem(notifications.size - 1)
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .glassSurface(cornerRadius = 20.dp, accentColor = DangerRed.copy(alpha = 0.06f))
            .padding(16.dp)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("SILENCED", fontSize = 10.sp, color = Color.White.copy(0.35f), letterSpacing = 1.5.sp, fontWeight = FontWeight.Medium)
                if (notifications.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(DangerRed.copy(alpha = 0.2f))
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text("${notifications.size}", fontSize = 11.sp, color = DangerRed, fontWeight = FontWeight.Bold)
                    }
                }
            }
            if (notifications.isEmpty()) {
                Spacer(Modifier.height(10.dp))
                Text("Waiting for notifications...", fontSize = 13.sp, color = Color.White.copy(0.25f), modifier = Modifier.align(Alignment.CenterHorizontally))
                Spacer(Modifier.height(4.dp))
            } else {
                Spacer(Modifier.height(10.dp))
                LazyColumn(
                    state = listState,
                    modifier = Modifier.heightIn(max = 320.dp),
                    verticalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    items(notifications, key = { it.id }) { noti ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color.White.copy(alpha = 0.05f))
                                .padding(horizontal = 10.dp, vertical = 8.dp)
                        ) {
                            Column {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(noti.appName, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = DangerRed, modifier = Modifier.weight(1f))
                                    Text(timeSdf.format(Date(noti.receivedAt)), fontSize = 10.sp, color = Color.White.copy(0.25f))
                                }
                                if (noti.title.isNotBlank()) {
                                    Spacer(Modifier.height(2.dp))
                                    Text(noti.title, fontSize = 12.sp, color = Color.White, fontWeight = FontWeight.Medium, maxLines = 1)
                                }
                                if (noti.text.isNotBlank()) {
                                    Text(noti.text, fontSize = 11.sp, color = Color.White.copy(0.45f), maxLines = 1)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ActiveSilenceCard(
    activeSilence: ActiveSilence,
    notifications: List<SilencedNotification>,
    onEndEarly: () -> Unit
) {
    val sdf = SimpleDateFormat("h:mm a", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("America/New_York")
    }
    val timeSdf = SimpleDateFormat("h:mm:ss a", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("America/New_York")
    }
    var timeLeft by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    LaunchedEffect(activeSilence.endsAt) {
        while (true) {
            val rem = activeSilence.endsAt - System.currentTimeMillis()
            if (rem <= 0) {
                timeLeft = "Ending..."
                kotlinx.coroutines.delay(1200)
                onEndEarly()
                break
            }
            val h = rem / 3_600_000
            val m = (rem % 3_600_000) / 60_000
            val s = (rem % 60_000) / 1_000
            timeLeft = if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
            kotlinx.coroutines.delay(1000)
        }
    }

    // Auto-scroll to newest notification
    LaunchedEffect(notifications.size) {
        if (notifications.isNotEmpty()) listState.animateScrollToItem(notifications.size - 1)
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        // Timer card
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .glassSurface(cornerRadius = 20.dp, accentColor = AccentPurple.copy(alpha = 0.1f))
                .padding(22.dp)
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Text("SILENCE ENDS AT", fontSize = 10.sp, color = Color.White.copy(0.35f), letterSpacing = 1.5.sp)
                Spacer(Modifier.height(4.dp))
                Text(sdf.format(Date(activeSilence.endsAt)), fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Spacer(Modifier.height(14.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .background(AccentPurple.copy(alpha = 0.12f))
                        .padding(horizontal = 24.dp, vertical = 10.dp)
                ) {
                    Text(timeLeft, fontSize = 40.sp, fontWeight = FontWeight.Light, color = AccentPurple, letterSpacing = (-1).sp)
                }
                Spacer(Modifier.height(4.dp))
                Text("remaining", fontSize = 12.sp, color = Color.White.copy(0.3f))
                Spacer(Modifier.height(18.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(DangerRed.copy(alpha = 0.1f))
                        .border(BorderStroke(1.dp, DangerRed.copy(alpha = 0.4f)), RoundedCornerShape(12.dp))
                        .clickable { onEndEarly() }
                        .padding(horizontal = 28.dp, vertical = 11.dp)
                ) {
                    Text("End Early", color = DangerRed, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        // Live silenced notifications
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .glassSurface(cornerRadius = 20.dp)
                .padding(16.dp)
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "SILENCED",
                        fontSize = 10.sp,
                        color = Color.White.copy(0.35f),
                        letterSpacing = 1.5.sp,
                        fontWeight = FontWeight.Medium
                    )
                    if (notifications.isNotEmpty()) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(AccentPurple.copy(alpha = 0.2f))
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Text(
                                "${notifications.size}",
                                fontSize = 11.sp,
                                color = AccentPurple,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                if (notifications.isEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "Waiting for notifications...",
                        fontSize = 13.sp,
                        color = Color.White.copy(0.25f),
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    )
                    Spacer(Modifier.height(4.dp))
                } else {
                    Spacer(Modifier.height(10.dp))
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.heightIn(max = 240.dp),
                        verticalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        items(notifications, key = { it.id }) { noti ->
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color.White.copy(alpha = 0.05f))
                                    .padding(horizontal = 10.dp, vertical = 8.dp)
                            ) {
                                Column {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            noti.appName,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = AccentPurple,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Text(
                                            timeSdf.format(Date(noti.receivedAt)),
                                            fontSize = 10.sp,
                                            color = Color.White.copy(0.25f)
                                        )
                                    }
                                    if (noti.title.isNotBlank()) {
                                        Spacer(Modifier.height(2.dp))
                                        Text(noti.title, fontSize = 12.sp, color = Color.White, fontWeight = FontWeight.Medium, maxLines = 1)
                                    }
                                    if (noti.text.isNotBlank()) {
                                        Text(noti.text, fontSize = 11.sp, color = Color.White.copy(0.45f), maxLines = 1)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PermissionCard(context: Context) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .glassSurface(cornerRadius = 20.dp)
            .padding(22.dp)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            Text("Permission Required", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
            Spacer(Modifier.height(8.dp))
            Text(
                "Noti Control needs Notification Listener access to intercept and silence notifications.",
                fontSize = 13.sp, color = Color.White.copy(0.5f), textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(16.dp))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(Brush.linearGradient(listOf(AccentBlue, AccentPurple)))
                    .clickable {
                        context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                    }
                    .padding(horizontal = 24.dp, vertical = 12.dp)
            ) {
                Text("Grant Access", color = Color.White, fontWeight = FontWeight.Bold)
            }
        }
    }
}

fun Modifier.glassSurface(cornerRadius: Dp, accentColor: Color = Color.Transparent): Modifier = this.drawBehind {
    val cr = cornerRadius.toPx()
    drawRoundRect(
        brush = Brush.linearGradient(
            colors = listOf(Color.White.copy(0.13f), Color.White.copy(0.06f)),
            start = Offset(0f, 0f), end = Offset(size.width, size.height)
        ),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(cr)
    )
    if (accentColor != Color.Transparent) {
        drawRoundRect(color = accentColor, cornerRadius = androidx.compose.ui.geometry.CornerRadius(cr))
    }
    drawRoundRect(
        brush = Brush.linearGradient(
            colors = listOf(Color.White.copy(0.28f), Color.Transparent),
            start = Offset(0f, 0f), end = Offset(size.width * 0.6f, size.height * 0.45f)
        ),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(cr)
    )
    drawRoundRect(
        brush = Brush.linearGradient(
            colors = listOf(Color.White.copy(0.65f), Color.White.copy(0.2f), Color.White.copy(0.08f), Color.White.copy(0.3f)),
            start = Offset(0f, 0f), end = Offset(size.width, size.height)
        ),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(cr),
        style = Stroke(1.5f)
    )
}
