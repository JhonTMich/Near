package com.example.findme

import com.example.findme.data.repository.AccountLiveUpdates
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveUpdatesPollTest {
    @Test
    fun withoutRealtimeItPollsEveryMinute() {
        assertTrue(AccountLiveUpdates.pollDue(live = false, sinceLastMs = 60_000))
    }

    @Test
    fun withRealtimeItWaitsFiveMinutes() {
        assertFalse(AccountLiveUpdates.pollDue(live = true, sinceLastMs = 60_000))
        assertFalse(AccountLiveUpdates.pollDue(live = true, sinceLastMs = 240_000))
        assertTrue(AccountLiveUpdates.pollDue(live = true, sinceLastMs = 300_000))
    }

    @Test
    fun losingRealtimeMidwayPollsAtOnce() {
        // Three quiet minutes while live, then the socket drops: the next tick already reloads.
        assertTrue(AccountLiveUpdates.pollDue(live = false, sinceLastMs = 240_000))
    }
}
