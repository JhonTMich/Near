package com.example.findme

import com.example.findme.util.NotificationHelper.Channel
import com.example.findme.util.NotificationStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationStyleTextTest {
    @Test fun theAvatarUsesTheFirstLetterOfTheName() {
        assertEquals("A", NotificationStyle.initial("ana te propone un plan"))
        assertEquals("É", NotificationStyle.initial("  «Éric» está libre"))
        assertEquals("N", NotificationStyle.initial(""))
    }

    @Test fun eachKindHasItsLabelAndCount() {
        assertEquals("Mensaje", NotificationStyle.kind(Channel.Messages))
        assertEquals("1 mensaje", NotificationStyle.count(Channel.Messages, 1))
        assertEquals("3 cambios de estado", NotificationStyle.count(Channel.Statuses, 3))
        // Every kind stacks apart and has a colour of its own.
        assertEquals(Channel.entries.size, Channel.entries.map(NotificationStyle::group).toSet().size)
        assertTrue(Channel.entries.all { NotificationStyle.color(it) != 0 })
    }
}
