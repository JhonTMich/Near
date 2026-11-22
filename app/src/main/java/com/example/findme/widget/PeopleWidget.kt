package com.example.findme.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.*
import androidx.glance.action.clickable
import androidx.glance.appwidget.*
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.layout.*
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.example.findme.MainActivity
import com.example.findme.R
import com.example.findme.data.local.dataStore
import com.example.findme.util.NotificationHelper
import kotlinx.coroutines.flow.first
import java.time.Instant

class PeopleWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = PeopleWidget()
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        GlanceWidgetWorker.enqueuePeriodicUpdate(context)
        GlanceWidgetWorker.enqueueRefresh(context)
    }
    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        GlanceWidgetWorker.enqueuePeriodicUpdate(context)
        GlanceWidgetWorker.enqueueRefresh(context)
    }
    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        NearWidgets.cancelWorkIfNoneLeft(context)
    }
}

/** "Near · Tu gente": what each connected person is doing now, busy people first. */
class PeopleWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val initial = context.dataStore.data.first()
        provideContent {
            val prefs by context.dataStore.data.collectAsState(initial = initial)
            val data = WidgetStore.decode(prefs[WidgetStore.cacheKey])
            val size = LocalSize.current
            if (data == null) {
                SignedOutWidget(context, size.width.value, size.height.value)
                return@provideContent
            }
            val rows = PeopleRows.build(data.owner, data.profiles, data.schedules, data.statuses, Instant.now())
            val shown = rows.take(PeopleRows.capacity(size.height.value, context.resources.configuration.fontScale))
            val ink = Color(0xFF1F2A44)
            val openPeople = actionStartActivity(Intent(context, MainActivity::class.java).setAction(NotificationHelper.OPEN_PEOPLE))
            Column(GlanceModifier.fillMaxSize().cornerRadius(24.dp).background(ImageProvider(R.drawable.widget_surface_signed_out))
                .padding(horizontal = 14.dp, vertical = 10.dp).clickable(openPeople)) {
                Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.Vertical.CenterVertically) {
                    Text("Tu gente", GlanceModifier.defaultWeight(),
                        style = TextStyle(color = ColorProvider(ink), fontSize = 16.sp, fontWeight = FontWeight.Bold), maxLines = 1)
                    Image(ImageProvider(R.drawable.widget_ic_refresh), "Actualizar",
                        GlanceModifier.size(28.dp).padding(5.dp).clickable(actionRunCallback<RefreshConnectionWidget>()),
                        colorFilter = ColorFilter.tint(ColorProvider(ink)))
                }
                Spacer(GlanceModifier.height(6.dp))
                if (shown.isEmpty()) {
                    Text("Conecta con alguien en Near para verle aquí.",
                        style = TextStyle(color = ColorProvider(ink), fontSize = 13.sp), maxLines = 2)
                }
                shown.forEach { row ->
                    Row(GlanceModifier.fillMaxWidth().padding(vertical = 3.dp).background(ImageProvider(R.drawable.widget_card))
                        .padding(horizontal = 10.dp, vertical = 5.dp), verticalAlignment = Alignment.Vertical.CenterVertically) {
                        Column(GlanceModifier.defaultWeight()) {
                            Text(row.name, style = TextStyle(color = ColorProvider(ink), fontSize = 14.sp, fontWeight = FontWeight.Bold), maxLines = 1)
                            Text(row.title, style = TextStyle(color = ColorProvider(Color(0xFF2B3550)), fontSize = 12.5.sp), maxLines = 1)
                        }
                        Spacer(GlanceModifier.width(8.dp))
                        Box(GlanceModifier.background(ImageProvider(chip(row.scene))).padding(horizontal = 8.dp, vertical = 2.dp)) {
                            Text(row.label, style = TextStyle(color = ColorProvider(Color.White), fontSize = 11.sp, fontWeight = FontWeight.Bold), maxLines = 1)
                        }
                    }
                }
                if (rows.size > shown.size) Text("y ${rows.size - shown.size} más en Near",
                    style = TextStyle(color = ColorProvider(Color(0xFF2B3550)), fontSize = 11.sp), maxLines = 1)
            }
        }
    }

    private fun chip(scene: WidgetScene) = when (scene) {
        WidgetScene.WORK -> R.drawable.widget_chip_work
        WidgetScene.UNIVERSITY -> R.drawable.widget_chip_university
        WidgetScene.FREE -> R.drawable.widget_chip_free
        WidgetScene.STREET -> R.drawable.widget_chip_street
    }
}
