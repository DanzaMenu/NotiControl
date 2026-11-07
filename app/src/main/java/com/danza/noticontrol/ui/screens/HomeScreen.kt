package com.danza.noticontrol.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.border
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.geometry.Size
import kotlin.math.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.nativeCanvas
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
import androidx.compose.runtime.CompositionLocalProvider
import com.danza.noticontrol.ui.theme.*
import com.danza.noticontrol.updater.AppUpdater
import com.danza.noticontrol.updater.UpdateInfo
import com.danza.noticontrol.viewmodel.ActiveSilence
import com.danza.noticontrol.viewmodel.MainViewModel
import com.danza.noticontrol.widget.SilenceWidget
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
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
    val savedBgColor by viewModel.bgColor.collectAsState()
    val bgMediaUri by viewModel.bgMediaUri.collectAsState()
    val bgMediaType by viewModel.bgMediaType.collectAsState()
    val bgMediaDim by viewModel.bgMediaDim.collectAsState()
    val dynamicAccentLong by viewModel.dynamicAccent.collectAsState()
    val accent = Color(dynamicAccentLong.toULong())
    val secondaryAccentLong by viewModel.secondaryAccent.collectAsState()
    val secondaryAccent = Color(secondaryAccentLong.toULong())
    val alwaysSilencedPackages by viewModel.alwaysSilencedPackages.collectAsState()
    val alwaysSilenceEnabled by viewModel.alwaysSilenceEnabled.collectAsState()
    var showSettings by remember { mutableStateOf(false) }
    var showAlwaysSilencePicker by remember { mutableStateOf(false) }

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

    CompositionLocalProvider(LocalAccentColor provides accent) {

    // Settings bottom sheet
    if (showSettings) {
        val settingsSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showSettings = false },
            sheetState = settingsSheetState,
            containerColor = Color(0xFF0D1B2E)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 32.dp)
            ) {
                Text(
                    "Settings",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Spacer(Modifier.height(20.dp))
                var bgColorExpanded by remember { mutableStateOf(false) }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.White.copy(0.06f))
                        .clickable { bgColorExpanded = !bgColorExpanded }
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Background Color",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White.copy(0.9f)
                    )
                    Icon(
                        if (bgColorExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null,
                        tint = Color.White.copy(0.5f),
                        modifier = Modifier.size(20.dp)
                    )
                }
                AnimatedVisibility(visible = bgColorExpanded) {
                Column(modifier = Modifier.fillMaxWidth()) {
                Spacer(Modifier.height(12.dp))

                // Initialize wheel state from saved color
                val initHsv = remember(savedBgColor) {
                    if (savedBgColor != null) {
                        val c = Color(savedBgColor!!.toULong())
                        val hsv = FloatArray(3)
                        android.graphics.Color.RGBToHSV(
                            (c.red * 255).toInt(), (c.green * 255).toInt(), (c.blue * 255).toInt(), hsv
                        )
                        hsv
                    } else {
                        floatArrayOf(210f, 0.7f, 0.24f)
                    }
                }
                var hue by remember(savedBgColor) { mutableStateOf(initHsv[0]) }
                var saturation by remember(savedBgColor) { mutableStateOf(initHsv[1]) }
                var brightness by remember(savedBgColor) { mutableStateOf(initHsv[2]) }
                var hexText by remember(savedBgColor) { mutableStateOf(
                    if (savedBgColor != null) {
                        val c = Color(savedBgColor!!.toULong())
                        String.format("%02X%02X%02X", (c.red * 255).toInt(), (c.green * 255).toInt(), (c.blue * 255).toInt())
                    } else ""
                ) }

                fun hsvToColor(h: Float, s: Float, v: Float): Color {
                    val rgb = android.graphics.Color.HSVToColor(floatArrayOf(h, s, v))
                    return Color(rgb).copy(alpha = 1f)
                }

                fun applyColor(h: Float, s: Float, v: Float) {
                    val c = hsvToColor(h, s, v)
                    val hex = String.format("%02X%02X%02X", (c.red * 255).toInt(), (c.green * 255).toInt(), (c.blue * 255).toInt())
                    hexText = hex
                    viewModel.setBgColor(context, c.value.toLong())
                }

                val currentColor = hsvToColor(hue, saturation, brightness)

                // Color wheel
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .padding(horizontal = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    val wheelSize = 240.dp
                    Canvas(
                        modifier = Modifier
                            .size(wheelSize)
                            .pointerInput(Unit) {
                                detectTapGestures { offset ->
                                    val cx = size.width / 2f
                                    val cy = size.height / 2f
                                    val dx = offset.x - cx
                                    val dy = offset.y - cy
                                    val radius = size.width / 2f
                                    val dist = sqrt(dx * dx + dy * dy)
                                    if (dist <= radius) {
                                        val angle = (Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())) + 360) % 360
                                        hue = angle.toFloat()
                                        saturation = (dist / radius).coerceIn(0f, 1f)
                                        applyColor(hue, saturation, brightness)
                                    }
                                }
                            }
                            .pointerInput(Unit) {
                                var dragging = false
                                awaitPointerEventScope {
                                    while (true) {
                                        val event = awaitPointerEvent()
                                        val cx = size.width / 2f
                                        val cy = size.height / 2f
                                        val radius = size.width / 2f
                                        for (change in event.changes) {
                                            if (change.pressed) {
                                                val dx = change.position.x - cx
                                                val dy = change.position.y - cy
                                                val dist = sqrt(dx * dx + dy * dy)
                                                if (dist <= radius || dragging) {
                                                    dragging = true
                                                    val clampedDist = dist.coerceAtMost(radius)
                                                    val angle = (Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())) + 360) % 360
                                                    hue = angle.toFloat()
                                                    saturation = (clampedDist / radius).coerceIn(0f, 1f)
                                                    applyColor(hue, saturation, brightness)
                                                    change.consume()
                                                }
                                            } else {
                                                dragging = false
                                            }
                                        }
                                    }
                                }
                            }
                    ) {
                        val radius = size.minDimension / 2f
                        val cx = size.width / 2f
                        val cy = size.height / 2f
                        val step = 2
                        for (angle in 0 until 360 step step) {
                            val sweep = step.toFloat() + 0.5f
                            for (ring in 0..20) {
                                val s = ring / 20f
                                val innerR = radius * (ring / 21f)
                                val outerR = radius * ((ring + 1) / 21f)
                                val paint = android.graphics.Paint().apply {
                                    color = android.graphics.Color.HSVToColor(floatArrayOf(angle.toFloat(), s, 1f))
                                    style = android.graphics.Paint.Style.FILL
                                }
                                drawContext.canvas.nativeCanvas.drawArc(
                                    cx - outerR, cy - outerR, cx + outerR, cy + outerR,
                                    angle.toFloat(), sweep, true, paint
                                )
                            }
                        }

                        // Draw thumb
                        val thumbAngle = Math.toRadians(hue.toDouble())
                        val thumbDist = saturation * radius
                        val thumbX = cx + (thumbDist * cos(thumbAngle)).toFloat()
                        val thumbY = cy + (thumbDist * sin(thumbAngle)).toFloat()
                        drawCircle(
                            color = Color.White,
                            radius = 14f,
                            center = Offset(thumbX, thumbY),
                            style = Stroke(width = 3f)
                        )
                        drawCircle(
                            color = currentColor,
                            radius = 11f,
                            center = Offset(thumbX, thumbY)
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))

                // Brightness slider
                Text("Brightness", fontSize = 12.sp, color = Color.White.copy(0.6f))
                Spacer(Modifier.height(6.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(32.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .drawBehind {
                            for (x in 0..size.width.toInt()) {
                                val v = x / size.width
                                val rgb = android.graphics.Color.HSVToColor(floatArrayOf(hue, saturation, v))
                                drawRect(
                                    color = Color(rgb).copy(alpha = 1f),
                                    topLeft = Offset(x.toFloat(), 0f),
                                    size = Size(1f, size.height)
                                )
                            }
                        }
                        .pointerInput(Unit) {
                            awaitPointerEventScope {
                                while (true) {
                                    val event = awaitPointerEvent()
                                    for (change in event.changes) {
                                        if (change.pressed) {
                                            brightness = (change.position.x / size.width).coerceIn(0.05f, 0.5f)
                                            applyColor(hue, saturation, brightness)
                                            change.consume()
                                        }
                                    }
                                }
                            }
                        }
                ) {
                    // Thumb on brightness slider
                    val thumbX = brightness / 0.5f * (1f)
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(thumbX.coerceIn(0.02f, 0.98f))
                    ) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .size(20.dp)
                                .clip(CircleShape)
                                .background(Color.White)
                                .border(2.dp, currentColor, CircleShape)
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))

                // Hex input + preview
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Color preview circle
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(currentColor)
                            .border(1.dp, Color.White.copy(0.3f), CircleShape)
                    )
                    Spacer(Modifier.width(12.dp))
                    // Hex input
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.White.copy(0.08f))
                            .padding(horizontal = 12.dp, vertical = 10.dp)
                    ) {
                        Text("#", color = Color.White.copy(0.4f), fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        BasicTextField(
                            value = hexText,
                            onValueChange = { input ->
                                val filtered = input.filter { it in "0123456789ABCDEFabcdef" }.take(6)
                                hexText = filtered.uppercase()
                                if (filtered.length == 6) {
                                    try {
                                        val rgb = android.graphics.Color.parseColor("#$filtered")
                                        val c = Color(rgb).copy(alpha = 1f)
                                        val hsv = FloatArray(3)
                                        android.graphics.Color.RGBToHSV(
                                            (c.red * 255).toInt(), (c.green * 255).toInt(), (c.blue * 255).toInt(), hsv
                                        )
                                        hue = hsv[0]; saturation = hsv[1]; brightness = hsv[2]
                                        viewModel.setBgColor(context, c.value.toLong())
                                    } catch (_: Exception) {}
                                }
                            },
                            textStyle = androidx.compose.ui.text.TextStyle(
                                color = Color.White,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Medium
                            ),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))
                // Reset button
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color.White.copy(0.06f))
                        .clickable {
                            viewModel.resetBgColor(context)
                            hue = 210f; saturation = 0.7f; brightness = 0.24f
                            hexText = ""
                        }
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Reset to Default", color = LocalAccentColor.current, fontWeight = FontWeight.Medium, fontSize = 14.sp)
                }
                } // Column
                } // AnimatedVisibility

                Spacer(Modifier.height(12.dp))

                // ── Background Media section ──────────────────────────────────
                var bgMediaExpanded by remember { mutableStateOf(false) }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.White.copy(0.06f))
                        .clickable { bgMediaExpanded = !bgMediaExpanded }
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Background Media",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White.copy(0.9f)
                    )
                    Icon(
                        if (bgMediaExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null,
                        tint = Color.White.copy(0.5f),
                        modifier = Modifier.size(20.dp)
                    )
                }
                AnimatedVisibility(visible = bgMediaExpanded) {
                Column(modifier = Modifier.fillMaxWidth()) {
                Spacer(Modifier.height(12.dp))

                // Type selector: None / Video / Image
                val mediaTypes = listOf("none", "video", "image")
                val mediaLabels = listOf("None", "Video", "Image")
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    mediaTypes.forEachIndexed { i, type ->
                        val selected = bgMediaType == type
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (selected) LocalAccentColor.current.copy(0.25f) else Color.White.copy(0.06f))
                                .border(1.dp, if (selected) LocalAccentColor.current else Color.Transparent, RoundedCornerShape(8.dp))
                                .clickable {
                                    if (type == "none") {
                                        viewModel.resetBgMedia(context)
                                    } else {
                                        viewModel.setBgMediaType(context, type)
                                    }
                                }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                mediaLabels[i],
                                fontSize = 13.sp,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                color = if (selected) LocalAccentColor.current else Color.White.copy(0.6f)
                            )
                        }
                    }
                }

                // File picker launcher (only shown when video or image selected)
                if (bgMediaType != "none") {
                    val mimeType = if (bgMediaType == "video") "video/*" else "image/*"
                    val mediaPicker = rememberLauncherForActivityResult(
                        ActivityResultContracts.GetContent()
                    ) { uri: Uri? ->
                        if (uri != null) {
                            // Take persistable permission so URI survives app restarts
                            context.contentResolver.takePersistableUriPermission(
                                uri,
                                Intent.FLAG_GRANT_READ_URI_PERMISSION
                            )
                            viewModel.setBgMedia(context, uri.toString(), bgMediaType)
                        }
                    }

                    Spacer(Modifier.height(10.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color.White.copy(0.06f))
                            .clickable { mediaPicker.launch(mimeType) }
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                Icons.Default.FolderOpen,
                                contentDescription = null,
                                tint = LocalAccentColor.current,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                if (bgMediaUri.isNullOrEmpty()) "Pick ${if (bgMediaType == "video") "Video" else "Image"}"
                                else "Change ${if (bgMediaType == "video") "Video" else "Image"}",
                                color = LocalAccentColor.current,
                                fontWeight = FontWeight.Medium,
                                fontSize = 14.sp
                            )
                        }
                    }

                    // Show current file name if one is set
                    if (!bgMediaUri.isNullOrEmpty()) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            Uri.parse(bgMediaUri).lastPathSegment ?: "File selected",
                            fontSize = 11.sp,
                            color = Color.White.copy(0.35f),
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center,
                            maxLines = 1
                        )
                    }

                    Spacer(Modifier.height(16.dp))

                    // Dimness slider
                    var localDim by remember(bgMediaDim) { mutableStateOf(bgMediaDim) }
                    Text(
                        "Dimness  ${(localDim * 100).toInt()}%",
                        fontSize = 12.sp,
                        color = Color.White.copy(0.6f)
                    )
                    Slider(
                        value = localDim,
                        onValueChange = { localDim = it },
                        onValueChangeFinished = { viewModel.setBgMediaDim(context, localDim) },
                        valueRange = 0f..1f,
                        modifier = Modifier.fillMaxWidth(),
                        colors = SliderDefaults.colors(
                            thumbColor = Color.White,
                            activeTrackColor = LocalAccentColor.current,
                            inactiveTrackColor = Color.White.copy(0.15f)
                        )
                    )
                }

                Spacer(Modifier.height(12.dp))
                // Clear media button
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color.White.copy(0.06f))
                        .clickable { viewModel.resetBgMedia(context) }
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Clear Media", color = LocalAccentColor.current, fontWeight = FontWeight.Medium, fontSize = 14.sp)
                }
                } // Column
                } // AnimatedVisibility

                Spacer(Modifier.height(16.dp))
                Text(
                    "v${AppUpdater.getCurrentVersion(context)}",
                    fontSize = 11.sp,
                    color = Color.White.copy(0.3f),
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
            }
        }
    }

    if (showUpdateDialog && updateInfo != null) {
        AlertDialog(
            onDismissRequest = { },
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
                            color = LocalAccentColor.current
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
                        }
                    },
                    enabled = !isDownloading
                ) {
                    Text("Update", color = if (isDownloading) Color.Gray else LocalAccentColor.current, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = { }
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
                if (AppUpdater.hasCachedApk(context)) {
                    AppUpdater.installCachedApk(context)
                }
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

    Box(modifier = Modifier.fillMaxSize()) {
        // Background layer: media (video/image) OR color gradient
        if (bgMediaType == "video" && !bgMediaUri.isNullOrEmpty()) {
            val exoPlayer = remember(bgMediaUri) {
                ExoPlayer.Builder(context.applicationContext).build().apply {
                    setMediaItem(MediaItem.fromUri(Uri.parse(bgMediaUri)))
                    repeatMode = Player.REPEAT_MODE_ALL
                    volume = 0f
                    videoScalingMode = androidx.media3.common.C.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING
                    prepare()
                    playWhenReady = true
                }
            }
            DisposableEffect(exoPlayer) {
                onDispose { exoPlayer.release() }
            }
            AndroidView(
                factory = { ctx ->
                    android.view.SurfaceView(ctx).also { sv ->
                        exoPlayer.setVideoSurfaceView(sv)
                    }
                },
                update = { sv ->
                    exoPlayer.setVideoSurfaceView(sv as android.view.SurfaceView)
                },
                modifier = Modifier.fillMaxSize()
            )
        } else if (bgMediaType == "image" && bgMediaUri != null) {
            androidx.compose.foundation.Image(
                painter = coil.compose.rememberAsyncImagePainter(bgMediaUri),
                contentDescription = null,
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            // Default color gradient
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .drawBehind {
                        val bgCenter = if (savedBgColor != null) Color(savedBgColor!!.toULong()) else Color(0xFF0B1E3D)
                        val bgEdge = if (savedBgColor != null) {
                            Color(bgCenter.red * 0.4f, bgCenter.green * 0.4f, bgCenter.blue * 0.4f)
                        } else Color(0xFF050A14)
                        drawRect(
                            brush = Brush.radialGradient(
                                colors = listOf(bgCenter, bgEdge),
                                center = Offset(size.width * 0.5f, size.height * (0.3f + bgShift * 0.08f)),
                                radius = size.height * 0.9f
                            )
                        )
                    }
            )
        }

        // Dim overlay for media
        if (bgMediaType != "none" && bgMediaUri != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = bgMediaDim))
            )
        }

        // Main content Box (all existing UI sits here)
        Box(modifier = Modifier.fillMaxSize()) {
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
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .glassSurface(cornerRadius = 12.dp)
                            .clickable { showSettings = true }
                            .padding(horizontal = 10.dp, vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Menu, contentDescription = "Settings", tint = Color.White, modifier = Modifier.size(17.dp))
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
                        }) { Text("Add", color = LocalAccentColor.current, fontWeight = FontWeight.Bold) }
                    },
                    dismissButton = {
                        TextButton(onClick = { showPinDialog = false }) {
                            Text("Cancel", color = Color.White.copy(0.5f))
                        }
                    }
                )
            }

            // Always-silence app picker
            if (showAlwaysSilencePicker) {
                val pickerSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
                val installedApps = remember {
                    context.packageManager.getInstalledApplications(android.content.pm.PackageManager.GET_META_DATA)
                        .filter { it.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM == 0 }
                        .map { it.packageName to context.packageManager.getApplicationLabel(it).toString() }
                        .sortedBy { it.second }
                }
                ModalBottomSheet(
                    onDismissRequest = { showAlwaysSilencePicker = false },
                    sheetState = pickerSheetState,
                    containerColor = Color(0xFF0D1B2E),
                    dragHandle = null
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text("Always Silence", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        Text("These apps are always silenced, even when session silence is off.", fontSize = 12.sp, color = Color.White.copy(0.5f))
                        Spacer(Modifier.height(14.dp))
                        LazyColumn(modifier = Modifier.heightIn(max = 480.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            items(installedApps, key = { it.first }) { (pkg, name) ->
                                val selected = pkg in alwaysSilencedPackages
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(if (selected) LocalAccentColor.current.copy(0.15f) else Color.White.copy(0.04f))
                                        .clickable { viewModel.toggleAlwaysSilencedPackage(context, pkg) }
                                        .padding(horizontal = 14.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(name, fontSize = 14.sp, color = Color.White, modifier = Modifier.weight(1f))
                                    if (selected) {
                                        Icon(Icons.Default.Check, contentDescription = null, tint = LocalAccentColor.current, modifier = Modifier.size(18.dp))
                                    }
                                }
                            }
                        }
                        Spacer(Modifier.height(16.dp))
                        TextButton(onClick = { showAlwaysSilencePicker = false }, modifier = Modifier.align(Alignment.End)) {
                            Text("Done", color = LocalAccentColor.current, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // Orb row: session-silence bell (left) + always-silence bell (right)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                // Left bell — session silence orb
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

                Spacer(Modifier.width(4.dp))

                // Right bell — always-silence toggle (tap = on/off, long-press = pick apps)
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .clip(CircleShape)
                        .pointerInput(alwaysSilenceEnabled) {
                            detectTapGestures(
                                onTap = { viewModel.toggleAlwaysSilenceEnabled(context) },
                                onLongPress = { showAlwaysSilencePicker = true }
                            )
                        }
                ) {
                    PulsingOrb(
                        orbState = if (alwaysSilenceEnabled) OrbState.TIMED else OrbState.IDLE,
                        size = 190.dp,
                        colorOverride = if (alwaysSilenceEnabled) secondaryAccent else Color(0xFF1A2A3A)
                    )
                }
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
                            alwaysSilenceEnabled -> LocalAccentColor.current
                            else -> Color(0xFF4CAF50)
                        })
                )
                Spacer(Modifier.width(7.dp))
                Text(
                    when {
                        isOrbMode -> "Silencing All — Tap to Stop"
                        isSilenced -> "Silence Mode On"
                        alwaysSilenceEnabled -> "Always Silence Active"
                        else -> "All notifications allowed"
                    },
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White.copy(alpha = 0.9f)
                )
            }

            if (!isSilenced && !alwaysSilenceEnabled) {
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
                OrbSilenceNotificationsCard(notifications = liveNotifications)
            } else if (isSilenced && activeSilence != null) {
                ActiveSilenceCard(
                    activeSilence = activeSilence!!,
                    notifications = liveNotifications,
                    onEndEarly = { viewModel.endSilenceEarly(context) }
                )
            } else if (alwaysSilenceEnabled) {
                // Always silence active — show which apps are being silenced
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .glassSurface(cornerRadius = 20.dp, accentColor = secondaryAccent.copy(alpha = 0.06f))
                        .padding(16.dp)
                ) {
                    Column {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("ALWAYS SILENCED", fontSize = 10.sp, color = Color.White.copy(0.35f), letterSpacing = 1.5.sp, fontWeight = FontWeight.Medium)
                            Box(modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(secondaryAccent.copy(0.2f)).padding(horizontal = 8.dp, vertical = 3.dp)) {
                                Text("${alwaysSilencedPackages.size} apps", fontSize = 11.sp, color = secondaryAccent, fontWeight = FontWeight.Bold)
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        if (alwaysSilencedPackages.isEmpty()) {
                            Text("No apps selected", fontSize = 13.sp, color = Color.White.copy(0.25f))
                            Text("Long-press the right bell to pick apps.", fontSize = 12.sp, color = Color.White.copy(0.18f))
                        } else {
                            Text("Silencing ${alwaysSilencedPackages.size} app${if (alwaysSilencedPackages.size == 1) "" else "s"} permanently.", fontSize = 13.sp, color = Color.White.copy(0.6f))
                            Spacer(Modifier.height(4.dp))
                            Text("Long-press the right bell to manage.", fontSize = 12.sp, color = Color.White.copy(0.25f))
                        }
                    }
                }
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
                                Icon(Icons.Default.Apps, contentDescription = null, tint = LocalAccentColor.current, modifier = Modifier.size(20.dp))
                                Spacer(Modifier.width(10.dp))
                                Column {
                                    Text("Select Apps to Silence", fontSize = 14.sp, color = Color.White, fontWeight = FontWeight.Medium)
                                    Text(
                                        if (blockedPackages.isEmpty()) "No apps selected" else "${blockedPackages.size} app${if (blockedPackages.size != 1) "s" else ""} selected",
                                        fontSize = 12.sp,
                                        color = if (blockedPackages.isEmpty()) DangerRed.copy(alpha = 0.8f) else LocalAccentColor.current
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
        } // inner content Box
    } // outer Box

    } // CompositionLocalProvider
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
                            if (selected) Brush.linearGradient(listOf(LocalAccentColor.current.copy(alpha = 0.4f), AccentPurple.copy(alpha = 0.3f)))
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
    val accent = LocalAccentColor.current

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
                Text("Duration: ${formatDuration(durationMins)}", fontSize = 13.sp, color = LocalAccentColor.current)

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
                                    Brush.linearGradient(listOf(LocalAccentColor.current, AccentPurple))
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
                                            listOf(accent.copy(alpha = 0.4f), AccentPurple.copy(alpha = 0.3f))
                                        else
                                            listOf(Color.White.copy(alpha = 0.1f), Color.White.copy(alpha = 0.05f))
                                    ),
                                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(12.dp.toPx())
                                )
                                drawRoundRect(
                                    color = if (isLast) accent.copy(alpha = 0.6f) else Color.White.copy(alpha = 0.15f),
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
                            Brush.linearGradient(listOf(accent.copy(alpha = 0.2f), AccentPurple.copy(alpha = 0.15f)))
                        else
                            Brush.linearGradient(listOf(Color.White.copy(alpha = 0.06f), Color.White.copy(alpha = 0.03f)))
                    )
                    .drawBehind {
                        drawRoundRect(
                            color = if (durationType == "custom") accent.copy(0.4f) else Color.White.copy(alpha = 0.1f),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(12.dp.toPx()),
                            style = Stroke(1f)
                        )
                    }
                    .clickable { onShowCustom() }
                    .padding(vertical = 13.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Schedule, contentDescription = null, tint = if (durationType == "custom") LocalAccentColor.current else Color.White.copy(0.4f), modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(7.dp))
                    Text(
                        "Custom Time Range",
                        color = if (durationType == "custom") LocalAccentColor.current else Color.White.copy(0.5f),
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
                            if (amPm == period) LocalAccentColor.current.copy(alpha = 0.3f) else Color.White.copy(alpha = 0.06f)
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
                        color = if (amPm == period) LocalAccentColor.current else Color.White.copy(0.4f),
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

@Composable
fun AppBackground(viewModel: MainViewModel, modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val context = LocalContext.current
    val savedBgColor by viewModel.bgColor.collectAsState()
    val bgMediaUri by viewModel.bgMediaUri.collectAsState()
    val bgMediaType by viewModel.bgMediaType.collectAsState()
    val bgMediaDim by viewModel.bgMediaDim.collectAsState()
    val inf = rememberInfiniteTransition(label = "bg2")
    val bgShift by inf.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(8000, easing = LinearEasing), RepeatMode.Reverse),
        label = "bgShift2"
    )
    Box(modifier = modifier.fillMaxSize()) {
        if (bgMediaType == "video" && !bgMediaUri.isNullOrEmpty()) {
            val exoPlayer = remember(bgMediaUri) {
                ExoPlayer.Builder(context.applicationContext).build().apply {
                    setMediaItem(MediaItem.fromUri(Uri.parse(bgMediaUri)))
                    repeatMode = Player.REPEAT_MODE_ALL
                    volume = 0f
                    videoScalingMode = androidx.media3.common.C.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING
                    prepare()
                    playWhenReady = true
                }
            }
            DisposableEffect(exoPlayer) { onDispose { exoPlayer.release() } }
            AndroidView(
                factory = { ctx -> android.view.SurfaceView(ctx).also { sv -> exoPlayer.setVideoSurfaceView(sv) } },
                update = { sv -> exoPlayer.setVideoSurfaceView(sv as android.view.SurfaceView) },
                modifier = Modifier.fillMaxSize()
            )
        } else if (bgMediaType == "image" && bgMediaUri != null) {
            androidx.compose.foundation.Image(
                painter = coil.compose.rememberAsyncImagePainter(bgMediaUri),
                contentDescription = null,
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Box(modifier = Modifier.fillMaxSize().drawBehind {
                val bgCenter = if (savedBgColor != null) Color(savedBgColor!!.toULong()) else Color(0xFF0B1E3D)
                val bgEdge = if (savedBgColor != null) Color(bgCenter.red * 0.4f, bgCenter.green * 0.4f, bgCenter.blue * 0.4f) else Color(0xFF050A14)
                drawRect(brush = Brush.radialGradient(colors = listOf(bgCenter, bgEdge), center = Offset(size.width * 0.5f, size.height * (0.3f + bgShift * 0.08f)), radius = size.height * 0.9f))
            })
        }
        if (bgMediaType != "none" && bgMediaUri != null) {
            Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = bgMediaDim)))
        }
        content()
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
fun PulsingOrb(orbState: OrbState, size: Dp, colorOverride: Color? = null) {
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

    val coreColor = colorOverride ?: when (orbState) {
        OrbState.ORB -> DangerRed
        OrbState.TIMED -> AccentPurple
        OrbState.IDLE -> LocalAccentColor.current
    }
    val glowColor = coreColor.copy(alpha = if (isSilenced) 0.72f else 0.38f)

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
                    .background(Brush.linearGradient(listOf(LocalAccentColor.current, AccentPurple)))
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
