package com.example.findme.reminders

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.findme.data.local.AccountSettingsRepository
import com.example.findme.data.local.AppTheme
import com.example.findme.ui.theme.NearTheme
import kotlinx.coroutines.delay
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/** Full screen over the lock screen while an activity alarm rings: stop it or snooze it. */
class ReminderAlarmActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val settings = AccountSettingsRepository(applicationContext)
        setContent {
            val theme by settings.theme.collectAsStateWithLifecycle(initialValue = AppTheme.Dark)
            val ringing by ReminderAlarm.ringing.collectAsStateWithLifecycle()
            // Stopped from the notification, by the timeout or never started: nothing left to show.
            LaunchedEffect(ringing) { if (ringing == null) finish() }
            NearTheme(theme = theme) {
                ringing?.let { AlarmScreen(it, onStop = { ReminderAlarm.stop(this) }, onSnooze = { ReminderAlarm.snooze(this) }) }
            }
        }
    }

    companion object {
        fun intent(context: Context): Intent = Intent(context, ReminderAlarmActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION)    }
}

@Composable
private fun AlarmScreen(ringing: ReminderAlarm.Ringing, onStop: () -> Unit, onSnooze: () -> Unit) {
    val format = DateTimeFormatter.ofPattern("HH:mm")
    val now by produceState(LocalTime.now()) {
        while (true) { value = LocalTime.now(); delay(1_000) }
    }
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 24.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.SpaceBetween) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Spacer(Modifier.heightIn(min = 24.dp))
                Icon(Icons.Default.Alarm, null, Modifier.size(56.dp), tint = MaterialTheme.colorScheme.primary)
                Text(now.format(format), fontSize = 64.sp, fontWeight = FontWeight.Light)
                Text(ringing.title, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
                if (ringing.body.isNotBlank()) Text(ringing.body, style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            }
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (ringing.snoozeUntil != null) OutlinedButton(onClick = onSnooze, Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                    Text("Posponer ${ringing.snoozeMinutes} min")
                }
                Button(onClick = onStop, Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text("Detener") }
            }
        }
    }
}
