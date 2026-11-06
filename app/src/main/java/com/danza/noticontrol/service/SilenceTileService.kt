package com.danza.noticontrol.service

import android.content.Intent
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.danza.noticontrol.receiver.TileActionReceiver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class SilenceTileService : TileService() {

    private var scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onStartListening() {
        super.onStartListening()
        // Collect live state from the service so the tile always reflects reality
        scope.launch {
            NotiListenerService.isSilencingFlow.collect { updateTile(it) }
        }
    }

    override fun onStopListening() {
        super.onStopListening()
        scope.cancel()
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    }

    override fun onClick() {
        super.onClick()
        val action = if (NotiListenerService.isSilencing)
            TileActionReceiver.ACTION_STOP
        else
            TileActionReceiver.ACTION_START
        sendBroadcast(Intent(action).setPackage(packageName))
    }

    private fun updateTile(silencing: Boolean) {
        val tile = qsTile ?: return
        if (silencing) {
            tile.state = Tile.STATE_ACTIVE
            tile.label = "Silencing"
            tile.subtitle = "Tap to stop"
        } else {
            tile.state = Tile.STATE_INACTIVE
            tile.label = "Noti Control"
            tile.subtitle = "Tap to silence"
        }
        tile.updateTile()
    }
}
