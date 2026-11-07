package com.danza.noticontrol.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
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
import com.danza.noticontrol.data.AppDatabase
import com.danza.noticontrol.data.SilenceSession
import com.danza.noticontrol.data.SilencedNotification
import com.danza.noticontrol.ui.theme.*
import com.danza.noticontrol.ui.screens.glassSurface
import com.danza.noticontrol.viewmodel.MainViewModel
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun LogScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val sessions by viewModel.sessions.collectAsState()

    AppBackground(viewModel = viewModel) {

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp)
        ) {
            Spacer(Modifier.height(16.dp))
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
                Spacer(Modifier.width(16.dp))
                Column {
                    Text("Session History", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    Text("${sessions.size} session${if (sessions.size != 1) "s" else ""}", fontSize = 13.sp, color = GlassWhiteDim)
                }
            }

            Spacer(Modifier.height(24.dp))

            if (sessions.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        "No sessions yet.\nStart silencing to see history here.",
                        color = GlassWhiteDim,
                        fontSize = 15.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(sessions, key = { it.id }) { session ->
                        SessionCard(
                            session = session,
                            viewModel = viewModel,
                            onDelete = { viewModel.deleteSession(context, session) }
                        )
                    }
                    item { Spacer(Modifier.height(16.dp)) }
                }
            }
        }
    }
}

@Composable
fun SessionCard(
    session: SilenceSession,
    viewModel: MainViewModel,
    onDelete: () -> Unit
) {
    val context = LocalContext.current
    var expanded by remember { mutableStateOf(false) }
    val notifications by viewModel.selectedSessionNotifications.collectAsState()
    val notiCount by remember {
        AppDatabase.get(context).dao().getNotificationCountForSessionFlow(session.id)
    }.collectAsState(initial = 0)

    val sdf = SimpleDateFormat("MMM d, h:mm a", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("America/New_York")
    }

    val durationLabel = run {
        val mins = session.durationMinutes
        when {
            mins < 0 -> "Until stopped"
            mins >= 60 -> "${mins / 60}h ${if (mins % 60 > 0) "${mins % 60}m" else ""}".trim()
            else -> "${mins}m"
        }
    }

    LaunchedEffect(expanded, session.id) {
        if (expanded) viewModel.loadNotificationsForSession(context, session.id)
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .glassSurface(cornerRadius = 18.dp)
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            sdf.format(Date(session.startTime)),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White
                        )
                        if (session.endedEarly) {
                            Spacer(Modifier.width(8.dp))
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(DangerRed.copy(alpha = 0.2f))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text("Ended early", fontSize = 10.sp, color = DangerRed)
                            }
                        }
                    }
                    Spacer(Modifier.height(3.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Duration: $durationLabel", fontSize = 12.sp, color = GlassWhiteDim)
                        Spacer(Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(AccentPurple.copy(alpha = 0.18f))
                                .padding(horizontal = 7.dp, vertical = 2.dp)
                        ) {
                            Text(
                                "$notiCount silenced",
                                fontSize = 10.sp,
                                color = AccentPurple,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = DangerRed.copy(alpha = 0.7f), modifier = Modifier.size(18.dp))
                }
                Icon(
                    if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = GlassWhiteDim,
                    modifier = Modifier.size(20.dp)
                )
            }

            AnimatedVisibility(visible = expanded, enter = expandVertically(), exit = shrinkVertically()) {
                Column(modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 16.dp)) {
                    HorizontalDivider(color = GlassBorder, thickness = 0.5.dp)
                    Spacer(Modifier.height(12.dp))

                    val sessionNotis = notifications.filter { it.sessionId == session.id }

                    if (sessionNotis.isEmpty()) {
                        Text("No notifications were silenced.", fontSize = 13.sp, color = GlassWhiteDim)
                    } else {
                        Text(
                            "${sessionNotis.size} notification${if (sessionNotis.size != 1) "s" else ""} silenced",
                            fontSize = 12.sp,
                            color = AccentBlue,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        sessionNotis.forEach { noti ->
                            NotificationRow(noti)
                            Spacer(Modifier.height(6.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun NotificationRow(noti: SilencedNotification) {
    val sdf = SimpleDateFormat("h:mm:ss a", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("America/New_York")
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(Color.White.copy(alpha = 0.05f))
            .padding(10.dp)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Text(
                    noti.appName,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = AccentPurple,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    sdf.format(Date(noti.receivedAt)),
                    fontSize = 10.sp,
                    color = GlassWhiteDim
                )
            }
            if (noti.title.isNotBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(noti.title, fontSize = 13.sp, color = Color.White, fontWeight = FontWeight.Medium)
            }
            if (noti.text.isNotBlank()) {
                Spacer(Modifier.height(1.dp))
                Text(noti.text, fontSize = 12.sp, color = GlassWhiteDim, maxLines = 2)
            }
        }
    }
}
