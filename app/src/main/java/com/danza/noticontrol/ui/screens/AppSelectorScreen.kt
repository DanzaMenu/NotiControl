package com.danza.noticontrol.ui.screens

import android.graphics.drawable.Drawable
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.danza.noticontrol.ui.theme.*
import com.danza.noticontrol.viewmodel.AppInfo
import com.danza.noticontrol.viewmodel.MainViewModel

@Composable
fun AppSelectorScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val installedApps by viewModel.installedApps.collectAsState()
    val blockedPackages by viewModel.blockedPackages.collectAsState()
    val highImportancePackages by viewModel.highImportancePackages.collectAsState()
    var query by remember { mutableStateOf("") }

    val filtered = remember(query, installedApps) {
        if (query.isBlank()) installedApps
        else installedApps.filter { it.label.contains(query, ignoreCase = true) }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(Color(0xFF0A1628), Color(0xFF050A14)),
                    center = Offset.Unspecified,
                    radius = 1200f
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
        ) {
            Spacer(Modifier.height(16.dp))

            // Header
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .glassSurface(cornerRadius = 12.dp)
                        .clickable { onBack() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                }
                Spacer(Modifier.width(14.dp))
                Column {
                    Text("Select Apps", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    Text(
                        "${blockedPackages.size} app${if (blockedPackages.size != 1) "s" else ""} selected",
                        fontSize = 13.sp,
                        color = AccentBlue
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            // Search bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .glassSurface(cornerRadius = 14.dp)
            ) {
                TextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Search apps...", color = Color.White.copy(alpha = 0.3f)) },
                    leadingIcon = {
                        Icon(Icons.Default.Search, contentDescription = null, tint = Color.White.copy(alpha = 0.4f))
                    },
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        cursorColor = AccentBlue
                    ),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(Modifier.height(10.dp))

            if (installedApps.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = AccentBlue)
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(filtered, key = { it.packageName }) { app ->
                        AppRow(
                            app = app,
                            isSelected = app.packageName in blockedPackages,
                            isHighImportance = app.packageName in highImportancePackages,
                            onToggle = { viewModel.toggleBlockedPackage(context, app.packageName) }
                        )
                    }
                    item { Spacer(Modifier.height(16.dp)) }
                }
            }
        }
    }
}

@Composable
fun AppRow(app: AppInfo, isSelected: Boolean, isHighImportance: Boolean = false, onToggle: () -> Unit) {
    val context = LocalContext.current

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .drawBehind {
                val cr = 14.dp.toPx()
                // Fill
                drawRoundRect(
                    brush = Brush.linearGradient(
                        colors = if (isSelected)
                            listOf(AccentBlue.copy(0.15f), AccentPurple.copy(0.08f))
                        else
                            listOf(Color.White.copy(0.08f), Color.White.copy(0.04f))
                    ),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(cr)
                )
                // Border
                drawRoundRect(
                    color = if (isSelected) AccentBlue.copy(0.5f) else Color.White.copy(0.1f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(cr),
                    style = Stroke(1f)
                )
            }
            .clickable { onToggle() }
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            // Real app icon via Coil
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(context.packageManager.getApplicationIcon(app.packageName))
                    .crossfade(true)
                    .build(),
                contentDescription = app.label,
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(10.dp))
            )

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        app.label,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.White
                    )
                    if (isHighImportance) {
                        Spacer(Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color(0xFFEF5350).copy(alpha = 0.2f))
                                .padding(horizontal = 5.dp, vertical = 2.dp)
                        ) {
                            Text("BLEEDS", fontSize = 8.sp, color = Color(0xFFEF5350), fontWeight = FontWeight.Bold)
                        }
                    }
                }
                Text(
                    app.packageName,
                    fontSize = 10.sp,
                    color = Color.White.copy(alpha = 0.25f),
                    maxLines = 1
                )
            }

            Spacer(Modifier.width(10.dp))

            // Checkbox
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(
                        if (isSelected)
                            Brush.linearGradient(listOf(AccentBlue, AccentPurple))
                        else
                            Brush.linearGradient(listOf(Color.White.copy(0.1f), Color.White.copy(0.05f)))
                    )
                    .drawBehind {
                        if (!isSelected) {
                            drawRoundRect(
                                color = Color.White.copy(0.2f),
                                cornerRadius = androidx.compose.ui.geometry.CornerRadius(6.dp.toPx()),
                                style = Stroke(1f)
                            )
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                if (isSelected) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }
    }
}
