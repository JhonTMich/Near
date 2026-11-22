package com.example.findme.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver

/** Both home-screen widgets share the cached data and the background work that refreshes it. */
object NearWidgets {
    suspend fun updateAll(context: Context) {
        updateWidgets(context, ConnectionWidgetReceiver::class.java, ConnectionWidget())
        updateWidgets(context, PeopleWidgetReceiver::class.java, PeopleWidget())
    }

    private suspend fun updateWidgets(
        context: Context,
        receiver: Class<out GlanceAppWidgetReceiver>,
        widget: GlanceAppWidget,
    ) {
        val manager = AppWidgetManager.getInstance(context) ?: return
        val glanceManager = GlanceAppWidgetManager(context)
        // R8 can merge the widget implementation classes. The receiver still identifies
        // the correct widget type, including after a release replaces the app.
        for (id in manager.getAppWidgetIds(ComponentName(context, receiver))) {
            val glanceId = runCatching { glanceManager.getGlanceIdBy(id) }.getOrNull() ?: continue
            widget.update(context, glanceId)
        }
    }

    /** True while at least one Near widget of either kind is on the home screen. */
    fun anyPlaced(context: Context): Boolean {
        val manager = AppWidgetManager.getInstance(context) ?: return false
        return listOf(ConnectionWidgetReceiver::class.java, PeopleWidgetReceiver::class.java).any { receiver ->
            runCatching { manager.getAppWidgetIds(ComponentName(context, receiver)).isNotEmpty() }.getOrDefault(false)
        }
    }

    /** Stops the shared refresh work only when the last widget of both kinds is removed. */
    fun cancelWorkIfNoneLeft(context: Context) {
        if (anyPlaced(context)) return
        val work = androidx.work.WorkManager.getInstance(context)
        work.cancelUniqueWork("GlanceWidgetUpdateWorker")
        work.cancelUniqueWork("WidgetManualRefresh")
        work.cancelUniqueWork("WidgetStatusBoundary")
    }
}
