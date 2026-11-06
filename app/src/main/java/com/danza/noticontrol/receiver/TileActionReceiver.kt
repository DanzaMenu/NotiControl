package com.danza.noticontrol.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.danza.noticontrol.data.AppDatabase
import com.danza.noticontrol.data.SilenceSession
import com.danza.noticontrol.service.NotiListenerService
import com.danza.noticontrol.widget.SilenceWidget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class TileActionReceiver : BroadcastReceiver() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_START -> {
                NotiListenerService.blockedPackages = null
                scope.launch {
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
                }
            }
            ACTION_STOP -> {
                val sessionId = NotiListenerService.currentSessionId
                NotiListenerService.isSilencing = false
                NotiListenerService.currentSessionId = -1L
                NotiListenerService.blockedPackages = null
                if (sessionId >= 0) {
                    scope.launch {
                        val dao = AppDatabase.get(context).dao()
                        val session = dao.getSessionById(sessionId)
                        session?.let {
                            dao.updateSession(it.copy(endTime = System.currentTimeMillis(), endedEarly = true))
                        }
                    }
                }
            }
        }
        // Refresh widget state after any toggle
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            SilenceWidget.refreshAllWidgets(context)
        }, 300)
    }

    companion object {
        const val ACTION_START = "com.danza.noticontrol.TILE_START"
        const val ACTION_STOP = "com.danza.noticontrol.TILE_STOP"
    }
}
