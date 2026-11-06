package com.danza.noticontrol.viewmodel

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.danza.noticontrol.data.AppDatabase
import com.danza.noticontrol.data.PrefsStore
import com.danza.noticontrol.data.SilenceSession
import com.danza.noticontrol.data.SilencedNotification
import com.danza.noticontrol.receiver.SilenceEndReceiver
import com.danza.noticontrol.service.NotiListenerService
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
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

    fun init(context: Context) {
        val dao = AppDatabase.get(context).dao()
        val prefs = PrefsStore(context)

        viewModelScope.launch {
            dao.getAllSessions().collect { _sessions.value = it }
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

        // Sync live detections from service into ViewModel
        viewModelScope.launch {
            NotiListenerService.highImportancePackages.collect { _highImportancePackages.value = it }
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
