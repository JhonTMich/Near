package com.example.findme

import com.example.findme.push.MessageReplies
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MessageRepliesTest {
    @Test fun repliesFollowTheServerLimits() {
        assertEquals("Sí, todo bien", MessageReplies.clean("  Sí, todo bien \n"))
        assertNull(MessageReplies.clean("   "))
        assertNull(MessageReplies.clean(null))
        assertEquals(240, MessageReplies.clean("x".repeat(240))?.length)
        assertNull(MessageReplies.clean("x".repeat(241)))
    }
}
