package com.danza.noticontrol.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.danza.noticontrol.R
import com.danza.noticontrol.receiver.TileActionReceiver
import com.danza.noticontrol.service.NotiListenerService

class SilenceWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        appWidgetIds.forEach { updateWidget(context, appWidgetManager, it) }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_WIDGET_TAP) {
            // Toggle silence
            val toggleAction = if (NotiListenerService.isSilencing)
                TileActionReceiver.ACTION_STOP
            else
                TileActionReceiver.ACTION_START
            context.sendBroadcast(Intent(toggleAction).setPackage(context.packageName))
            // Update all widget instances after a short delay for state to settle
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                refreshAllWidgets(context)
            }, 400)
        }
    }

    companion object {
        const val ACTION_WIDGET_TAP = "com.danza.noticontrol.WIDGET_TAP"

        fun refreshAllWidgets(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, SilenceWidget::class.java))
            ids.forEach { updateWidget(context, manager, it) }
        }

        fun updateWidget(context: Context, appWidgetManager: AppWidgetManager, widgetId: Int) {
            val silencing = NotiListenerService.isSilencing
            val views = RemoteViews(context.packageName, R.layout.widget_silence)

            // Swap full orb (circle + bell baked in)
            views.setImageViewResource(
                R.id.widget_orb,
                if (silencing) R.drawable.widget_orb_active else R.drawable.widget_orb_idle
            )

            // Tap intent
            val tapIntent = Intent(context, SilenceWidget::class.java).apply {
                action = ACTION_WIDGET_TAP
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context, widgetId, tapIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_root, pendingIntent)

            appWidgetManager.updateAppWidget(widgetId, views)
        }
    }
}
