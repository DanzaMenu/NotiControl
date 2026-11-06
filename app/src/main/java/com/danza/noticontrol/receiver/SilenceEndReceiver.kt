package com.danza.noticontrol.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.danza.noticontrol.data.AppDatabase
import com.danza.noticontrol.service.NotiListenerService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SilenceEndReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val sessionId = NotiListenerService.currentSessionId
        NotiListenerService.isSilencing = false
        NotiListenerService.currentSessionId = -1L

        if (sessionId >= 0) {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val dao = AppDatabase.get(context).dao()
                    val session = dao.getSessionById(sessionId)
                    session?.let {
                        dao.updateSession(it.copy(endTime = System.currentTimeMillis()))
                    }
                } catch (e: Exception) {
                    // ignore
                }
            }
        }
    }
}
