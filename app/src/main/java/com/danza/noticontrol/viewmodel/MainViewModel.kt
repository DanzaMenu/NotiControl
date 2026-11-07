package com.danza.noticontrol.viewmodel

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.palette.graphics.Palette
import com.danza.noticontrol.data.AppDatabase
import com.danza.noticontrol.data.PrefsStore
import com.danza.noticontrol.data.SilenceSession
import com.danza.noticontrol.data.SilencedNotification
import com.danza.noticontrol.receiver.SilenceEndReceiver
import com.danza.noticontrol.service.NotiListenerService
import com.danza.noticontrol.ui.theme.AccentBlue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar

data class ActiveSilence(
    val sessionId: Long,
    val endsAt: Long,
    val durationMinutes: Int
)

data class AppInfo(
    val packageName: String,
    val label: String,
    val hasHighImportance: Boolean = false
)

class MainViewModel : ViewModel() {

    private val _activeSilence = MutableStateFlow<ActiveSilence?>(null)
    val activeSilence: StateFlow<ActiveSilence?> = _activeSilence.asStateFlow()

    private val _sessions = MutableStateFlow<List<SilenceSession>>(emptyList())
    val sessions: StateFlow<List<SilenceSession>> = _sessions.asStateFlow()

    private val _selectedSessionNotifications = MutableStateFlow<List<SilencedNotification>>(emptyList())
    val selectedSessionNotifications: StateFlow<List<SilencedNotification>> = _selectedSessionNotifications.asStateFlow()

    // Settings state
    private val _blockMode = MutableStateFlow("all") // "all" or "selected"
    val blockMode: StateFlow<String> = _blockMode.asStateFlow()

    private val _blockedPackages = MutableStateFlow<Set<String>>(emptySet())
    val blockedPackages: StateFlow<Set<String>> = _blockedPackages.asStateFlow()

    private val _durationType = MutableStateFlow("preset")
    val durationType: StateFlow<String> = _durationType.asStateFlow()

    private val _presetMinutes = MutableStateFlow(60)
    val presetMinutes: StateFlow<Int> = _presetMinutes.asStateFlow()

    private val _customFromHour = MutableStateFlow(22)
    val customFromHour: StateFlow<Int> = _customFromHour.asStateFlow()

    private val _customFromMin = MutableStateFlow(0)
    val customFromMin: StateFlow<Int> = _customFromMin.asStateFlow()

    private val _customToHour = MutableStateFlow(7)
    val customToHour: StateFlow<Int> = _customToHour.asStateFlow()

    private val _customToMin = MutableStateFlow(0)
    val customToMin: StateFlow<Int> = _customToMin.asStateFlow()

    private val _installedApps = MutableStateFlow<List<AppInfo>>(emptyList())
    val installedApps: StateFlow<List<AppInfo>> = _installedApps.asStateFlow()

    private val _highImportancePackages = MutableStateFlow<Set<String>>(emptySet())
    val highImportancePackages: StateFlow<Set<String>> = _highImportancePackages.asStateFlow()

    private val _bgColor = MutableStateFlow<Long?>(null)
    val bgColor: StateFlow<Long?> = _bgColor.asStateFlow()

    private val _bgMediaUri = MutableStateFlow<String?>(null)
    val bgMediaUri: StateFlow<String?> = _bgMediaUri.asStateFlow()

    private val _bgMediaType = MutableStateFlow("none")
    val bgMediaType: StateFlow<String> = _bgMediaType.asStateFlow()

    private val _bgMediaDim = MutableStateFlow(0.4f)
    val bgMediaDim: StateFlow<Float> = _bgMediaDim.asStateFlow()

    private val _dynamicAccent = MutableStateFlow(AccentBlue.value.toLong())
    val dynamicAccent: StateFlow<Long> = _dynamicAccent.asStateFlow()

    fun init(context: Context) {
        val dao = AppDatabase.get(context).dao()
        val prefs = PrefsStore(context)

        viewModelScope.launch {
            dao.getAllSessions().collect { _sessions.value = it.sortedByDescending { s -> s.startTime } }
        }

        // Load persisted prefs
        viewModelScope.launch { prefs.blockMode.collect { _blockMode.value = it } }
        viewModelScope.launch { prefs.blockedPackages.collect { _blockedPackages.value = it } }
        viewModelScope.launch { prefs.durationType.collect { _durationType.value = it } }
        viewModelScope.launch { prefs.presetMinutes.collect { _presetMinutes.value = it } }
        viewModelScope.launch { prefs.customFromHour.collect { _customFromHour.value = it } }
        viewModelScope.launch { prefs.customFromMin.collect { _customFromMin.value = it } }
        viewModelScope.launch { prefs.customToHour.collect { _customToHour.value = it } }
        viewModelScope.launch { prefs.customToMin.collect { _customToMin.value = it } }
        viewModelScope.launch { prefs.highImportancePackages.collect { _highImportancePackages.value = it } }
        viewModelScope.launch {
            prefs.bgColor.collect { c ->
                _bgColor.value = c
                if (c != null && _bgMediaType.value == "none") computeAccentFromColor(c)
            }
        }
        viewModelScope.launch { prefs.bgMediaUri.collect { _bgMediaUri.value = it } }
        viewModelScope.launch {
            prefs.bgMediaType.collect { t ->
                _bgMediaType.value = t
                val uri = _bgMediaUri.value
                if (t != "none" && !uri.isNullOrEmpty()) computeAccentFromMedia(context, uri, t)
            }
        }
        viewModelScope.launch { prefs.bgMediaDim.collect { _bgMediaDim.value = it } }

        // Sync live detections from service into ViewModel
        viewModelScope.launch {
            NotiListenerService.highImportancePackages.collect { _highImportancePackages.value = it }
        }

        // Sync tile/widget-triggered silence into UI and refresh widgets
        viewModelScope.launch {
            NotiListenerService.isSilencingFlow.collect { silencing ->
                com.danza.noticontrol.widget.SilenceWidget.refreshAllWidgets(context)
                if (silencing && _activeSilence.value == null) {
                    // Tile just started a session — find it in DB and show it
                    val active = dao.getActiveSession(System.currentTimeMillis())
                    if (active != null) {
                        _activeSilence.value = ActiveSilence(active.id, active.endTime, active.durationMinutes)
                    }
                } else if (!silencing && _activeSilence.value != null) {
                    _activeSilence.value = null
                }
            }
        }

        // Restore active silence session if the process was killed mid-session
        viewModelScope.launch {
            val active = dao.getActiveSession(System.currentTimeMillis())
            if (active != null) {
                val blockedPkgs = if (_blockMode.value == "selected") _blockedPackages.value else null
                NotiListenerService.blockedPackages = blockedPkgs
                NotiListenerService.isSilencing = true
                NotiListenerService.currentSessionId = active.id
                _activeSilence.value = ActiveSilence(active.id, active.endTime, active.durationMinutes)
            }
        }

        loadInstalledApps(context)
    }

    private fun loadInstalledApps(context: Context) {
        viewModelScope.launch {
            val pm = context.packageManager
            val launcherIntent = android.content.Intent(android.content.Intent.ACTION_MAIN)
                .addCategory(android.content.Intent.CATEGORY_LAUNCHER)
            val apps = pm.queryIntentActivities(launcherIntent, 0)
                .filter { it.activityInfo.packageName != context.packageName }
                .mapNotNull { ri ->
                    try {
                        AppInfo(
                            packageName = ri.activityInfo.packageName,
                            label = ri.loadLabel(pm).toString()
                        )
                    } catch (e: Exception) { null }
                }
                .distinctBy { it.packageName }
                .sortedBy { it.label.lowercase() }
            _installedApps.value = apps
        }
    }

    fun setBlockMode(context: Context, mode: String) {
        _blockMode.value = mode
        viewModelScope.launch { PrefsStore(context).saveBlockMode(mode) }
    }

    fun setBgColor(context: Context, color: Long) {
        _bgColor.value = color
        computeAccentFromColor(color)
        viewModelScope.launch { PrefsStore(context).saveBgColor(color) }
    }

    fun resetBgColor(context: Context) {
        _bgColor.value = null
        _dynamicAccent.value = AccentBlue.value.toLong()
        viewModelScope.launch { PrefsStore(context).clearBgColor() }
    }

    fun setBgMediaType(context: Context, type: String) {
        _bgMediaType.value = type
        viewModelScope.launch { PrefsStore(context).saveBgMedia(_bgMediaUri.value ?: "", type) }
    }

    fun setBgMedia(context: Context, uri: String, type: String) {
        _bgMediaUri.value = uri
        _bgMediaType.value = type
        if (uri.isNotEmpty()) computeAccentFromMedia(context, uri, type)
        viewModelScope.launch { PrefsStore(context).saveBgMedia(uri, type) }
    }

    fun setBgMediaDim(context: Context, dim: Float) {
        _bgMediaDim.value = dim
        viewModelScope.launch { PrefsStore(context).saveBgMediaDim(dim) }
    }

    fun resetBgMedia(context: Context) {
        _bgMediaUri.value = null
        _bgMediaType.value = "none"
        _dynamicAccent.value = AccentBlue.value.toLong()
        viewModelScope.launch { PrefsStore(context).clearBgMedia() }
    }

    fun computeAccentFromColor(colorLong: Long) {
        val c = Color(colorLong.toULong())
        val hsv = FloatArray(3)
        android.graphics.Color.RGBToHSV(
            (c.red * 255).toInt(), (c.green * 255).toInt(), (c.blue * 255).toInt(), hsv
        )
        // Boost saturation and brightness so the accent is vibrant and readable
        hsv[1] = hsv[1].coerceAtLeast(0.5f)
        hsv[2] = hsv[2].coerceAtLeast(0.75f).coerceAtMost(1f)
        val rgb = android.graphics.Color.HSVToColor(hsv)
        _dynamicAccent.value = Color(rgb).copy(alpha = 1f).value.toLong()
    }

    fun computeAccentFromMedia(context: Context, uri: String, type: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val bitmap = if (type == "image") {
                    context.contentResolver.openInputStream(Uri.parse(uri))?.use { stream ->
                        val opts = BitmapFactory.Options().apply { inSampleSize = 4 }
                        BitmapFactory.decodeStream(stream, null, opts)
                    }
                } else {
                    MediaMetadataRetriever().use { r ->
                        r.setDataSource(context, Uri.parse(uri))
                        r.getFrameAtTime(0)
                    }
                } ?: return@launch

                val palette = Palette.from(bitmap).generate()
                val argb = palette.getVibrantColor(
                    palette.getLightVibrantColor(
                        palette.getDominantColor(AccentBlue.toArgb())
                    )
                )
                // Ensure the accent is bright enough to read on a dark background
                val hsv = FloatArray(3)
                android.graphics.Color.colorToHSV(argb, hsv)
                hsv[1] = hsv[1].coerceAtLeast(0.4f)
                hsv[2] = hsv[2].coerceAtLeast(0.7f)
                val boosted = android.graphics.Color.HSVToColor(hsv)
                _dynamicAccent.value = Color(boosted).copy(alpha = 1f).value.toLong()
            } catch (_: Exception) {
                _dynamicAccent.value = AccentBlue.value.toLong()
            }
        }
    }

    fun toggleBlockedPackage(context: Context, pkg: String) {
        val current = _blockedPackages.value.toMutableSet()
        if (pkg in current) current.remove(pkg) else current.add(pkg)
        _blockedPackages.value = current
        viewModelScope.launch { PrefsStore(context).saveBlockedPackages(current) }
    }

    fun startSilencePreset(context: Context, minutes: Int) {
        viewModelScope.launch {
            PrefsStore(context).saveDurationPreset(minutes)
            _durationType.value = "preset"
            _presetMinutes.value = minutes
        }
        val endTime = System.currentTimeMillis() + minutes * 60_000L
        startSilenceAt(context, endTime, minutes)
    }

    fun startSilenceCustom(context: Context, fromHour: Int, fromMin: Int, toHour: Int, toMin: Int) {
        viewModelScope.launch {
            PrefsStore(context).saveDurationCustom(fromHour, fromMin, toHour, toMin)
            _durationType.value = "custom"
            _customFromHour.value = fromHour
            _customFromMin.value = fromMin
            _customToHour.value = toHour
            _customToMin.value = toMin
        }

        val now = Calendar.getInstance()
        val from = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, fromHour)
            set(Calendar.MINUTE, fromMin)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= now.timeInMillis) add(Calendar.DAY_OF_YEAR, 1)
        }
        val to = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, toHour)
            set(Calendar.MINUTE, toMin)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            // End must be after start
            while (timeInMillis <= from.timeInMillis) add(Calendar.DAY_OF_YEAR, 1)
        }

        val durationMins = ((to.timeInMillis - from.timeInMillis) / 60_000).toInt()
        startSilenceAt(context, to.timeInMillis, durationMins)
    }

    private fun startSilenceAt(context: Context, endTime: Long, durationMins: Int) {
        val blockedPkgs = if (_blockMode.value == "selected") _blockedPackages.value else null
        NotiListenerService.blockedPackages = blockedPkgs

        viewModelScope.launch {
            val dao = AppDatabase.get(context).dao()
            val sessionId = dao.insertSession(
                SilenceSession(
                    startTime = System.currentTimeMillis(),
                    endTime = endTime,
                    durationMinutes = durationMins
                )
            )
            NotiListenerService.isSilencing = true
            NotiListenerService.currentSessionId = sessionId
            _activeSilence.value = ActiveSilence(sessionId, endTime, durationMins)
            scheduleAlarm(context, endTime)
        }
    }

    fun startOrbSilence(context: Context) {
        NotiListenerService.blockedPackages = null // always all apps
        viewModelScope.launch {
            val dao = AppDatabase.get(context).dao()
            val sessionId = dao.insertSession(
                SilenceSession(
                    startTime = System.currentTimeMillis(),
                    endTime = Long.MAX_VALUE,
                    durationMinutes = -1
                )
            )
            NotiListenerService.isSilencing = true
            NotiListenerService.currentSessionId = sessionId
            _activeSilence.value = ActiveSilence(sessionId, Long.MAX_VALUE, -1)
        }
    }

    fun endSilenceEarly(context: Context) {
        cancelAlarm(context)
        val sessionId = NotiListenerService.currentSessionId
        NotiListenerService.isSilencing = false
        NotiListenerService.currentSessionId = -1L
        NotiListenerService.blockedPackages = null

        viewModelScope.launch {
            if (sessionId >= 0) {
                val dao = AppDatabase.get(context).dao()
                val session = dao.getSessionById(sessionId)
                session?.let {
                    dao.updateSession(it.copy(endTime = System.currentTimeMillis(), endedEarly = true))
                }
            }
            _activeSilence.value = null
        }
    }

    fun loadNotificationsForSession(context: Context, sessionId: Long) {
        val dao = AppDatabase.get(context).dao()
        viewModelScope.launch {
            dao.getNotificationsForSession(sessionId).collect {
                _selectedSessionNotifications.value = it
            }
        }
    }

    fun deleteSession(context: Context, session: SilenceSession) {
        viewModelScope.launch {
            val dao = AppDatabase.get(context).dao()
            dao.deleteNotificationsForSession(session.id)
            dao.deleteSession(session)
        }
    }

    private fun scheduleAlarm(context: Context, triggerAt: Long) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, SilenceEndReceiver::class.java).apply {
            action = "com.danza.noticontrol.SILENCE_END"
        }
        val pi = PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
    }

    private fun cancelAlarm(context: Context) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, SilenceEndReceiver::class.java).apply {
            action = "com.danza.noticontrol.SILENCE_END"
        }
        val pi = PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        am.cancel(pi)
    }
}
