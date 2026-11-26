package com.example.findme

import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.test.platform.app.InstrumentationRegistry
import com.example.findme.util.NotificationHelper
import com.example.findme.util.NotificationHelper.Channel
import com.example.findme.util.NotificationStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Posts a realistic mix, as a phone would see it, and checks the look and the stacks. */
class NotificationStyleTest {
    private val context: Context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val manager get() = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    @Before fun grant() {
        if (Build.VERSION.SDK_INT >= 33) InstrumentationRegistry.getInstrumentation().uiAutomation
            .grantRuntimePermission(context.packageName, android.Manifest.permission.POST_NOTIFICATIONS)
        manager.cancelAll()
    }

    @Test fun postsAMixWithItsOwnLookAndStacksEachKind() {
        val reply = NotificationHelper.Reply("me", "ana", listOf("Voy", "En 5 min"))
        NotificationHelper.show(context, Channel.Reminders, 1, "IoT empieza en 10 min", "A las 15:00 · Aula 204")
        NotificationHelper.show(context, Channel.Statuses, 2, "Luis", "Ahora está «En la biblioteca»")
        NotificationHelper.show(context, Channel.Messages, 3, "Ana", "¿Vamos al almuerzo?", reply)
        NotificationHelper.show(context, Channel.Messages, 4, "Carla", "Ya llegué a la U", reply.copy(recipient = "carla"))
        NotificationHelper.show(context, Channel.Requested, 5, "Diego está libre", "Hasta las 18:00")
        Thread.sleep(500)
        val active = manager.activeNotifications
        val messages = active.filter { it.notification.group == NotificationStyle.group(Channel.Messages) }
        // Two messages and their summary.
        assertEquals(3, messages.size)
        assertTrue(messages.any { it.notification.flags and android.app.Notification.FLAG_GROUP_SUMMARY != 0 })
        assertEquals(NotificationStyle.color(Channel.Reminders), active.first { it.id == 1 }.notification.color)
        // A picture of the shade while the notifications are still there: they go when the test ends.
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        automation.executeShellCommand("cmd statusbar expand-notifications").close()
        Thread.sleep(5_000)
        automation.takeScreenshot()?.let { TestScreenshots.save("notifications", it) }
        automation.executeShellCommand("cmd statusbar collapse").close()
    }
}
