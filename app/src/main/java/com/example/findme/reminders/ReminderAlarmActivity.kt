package com.example.findme.reminders

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.findme.data.local.AccountSettingsRepository
import com.example.findme.data.local.AppTheme
import com.example.findme.ui.theme.NearTheme

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

