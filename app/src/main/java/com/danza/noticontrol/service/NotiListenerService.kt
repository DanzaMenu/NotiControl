package com.danza.noticontrol.service

import android.content.pm.PackageManager
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.danza.noticontrol.data.AppDatabase
import com.danza.noticontrol.data.SilencedNotification
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class NotiListenerService : NotificationListenerService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    companion object {
        var isSilencing = false
        var currentSessionId: Long = -1L
        var blockedPackages: Set<String>? = null
        var instance: NotiListenerService? = null
        val isConnected = kotlinx.coroutines.flow.MutableStateFlow(false)
        val highImportancePackages = kotlinx.coroutines.flow.MutableStateFlow<Set<String>>(emptySet())
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        instance = this
        isConnected.value = true
        Log.d("NotiControl", "Listener connected")
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        instance = null
        isConnected.value = false
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        sbn ?: return
        Log.d("NotiControl", "onNotificationPosted: pkg=${sbn.packageName} isSilencing=$isSilencing sessionId=$currentSessionId")
        if (!isSilencing || currentSessionId < 0) return

        val pkg = sbn.packageName ?: return
        if (pkg == packageName) return

        // Skip group summary notifications (the invisible "container" Android uses to group notis)
        val flags = sbn.notification?.flags ?: 0
        Log.d("NotiControl", "pkg=$pkg flags=$flags isGroupSummary=${flags and android.app.Notification.FLAG_GROUP_SUMMARY != 0}")
        if (flags and android.app.Notification.FLAG_GROUP_SUMMARY != 0) return

        val shouldBlock = when (val blocked = blockedPackages) {
            null -> true               // All Apps mode — block everything
            else -> pkg in blocked     // Selected Apps mode — only block listed packages
        }

        Log.d("NotiControl", "shouldBlock=$shouldBlock blockedPackages=$blockedPackages")

        // Detect high-importance (heads-up) via Ranking and persist for badge display
        if (android.os.Build.VERSION.SDK_INT >= 28) {
            val ranking = Ranking()
            if (currentRanking.getRanking(sbn.key, ranking) &&
                ranking.importance >= android.app.NotificationManager.IMPORTANCE_HIGH) {
                val current = highImportancePackages.value
                if (pkg !in current) {
                    val updated = current + pkg
                    highImportancePackages.value = updated
                    scope.launch {
                        com.danza.noticontrol.data.PrefsStore(applicationContext)
                            .saveHighImportancePackages(updated)
                    }
                }
            }
        }

        if (!shouldBlock) return

        // Cancel (dismiss) the notification
        try {
            cancelNotification(sbn.key)
        } catch (e: Exception) {
            // ignore
        }

        // Log it
        val extras = sbn.notification?.extras
        val title = extras?.getCharSequence("android.title")?.toString() ?: ""
        val text = extras?.getCharSequence("android.text")?.toString() ?: ""
        val appName = getAppName(pkg)

        val sessionId = currentSessionId
        scope.launch {
            try {
                AppDatabase.get(applicationContext).dao().insertNotification(
                    SilencedNotification(
                        sessionId = sessionId,
                        appName = appName,
                        appPackage = pkg,
                        title = title,
                        text = text,
                        receivedAt = System.currentTimeMillis()
                    )
                )
                Log.d("NotiControl", "Logged noti for session $sessionId: $appName / $title")
            } catch (e: Exception) {
                Log.e("NotiControl", "DB insert failed: ${e.message}", e)
            }
        }
    }

    private fun getAppName(packageName: String): String {
        return try {
            val info = packageManager.getApplicationInfo(packageName, 0)
            packageManager.getApplicationLabel(info).toString()
        } catch (e: PackageManager.NameNotFoundException) {
            packageName
        }
    }
}
