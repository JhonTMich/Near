package com.example.findme.data.repository

import com.example.findme.data.network.SupabaseClientProvider
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.Realtime
import io.github.jan.supabase.realtime.RealtimeChannel
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.realtime.realtime
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import java.util.UUID

/** Only collected while the account UI is visible. Event payloads never bypass RLS reads. */
internal object AccountLiveUpdates {
    /** Reload interval while the realtime channel is down. */
    const val POLL_OFFLINE_MS = 60_000L
    /**
     * Reload interval while the channel is live: only for the tables it does not listen to (connection
     * preferences, pauses, groups…) changed from another device.
     */
    const val POLL_LIVE_MS = 300_000L

    /** Whether a poll is due after [sinceLastMs] without a reload, given whether realtime is [live]. */
    fun pollDue(live: Boolean, sinceLastMs: Long) = sinceLastMs >= if (live) POLL_LIVE_MS else POLL_OFFLINE_MS

    fun observe(userId: String): Flow<Unit> = channelFlow {
        val live = MutableStateFlow(false)
        trySend(Unit)
        launch {
            var since = 0L
            while (isActive) {
                delay(POLL_OFFLINE_MS)
                since += POLL_OFFLINE_MS
                if (pollDue(live.value, since)) { since = 0; trySend(Unit) }
            }
        }
        launch {
            while (isActive) {
                val client = SupabaseClientProvider.client
                val channel = client.realtime.channel("account-$userId-${UUID.randomUUID()}") {}
                try {
                    // connection_preferences is left out on purpose: one unavailable table makes the whole
                    // channel fail. Own changes reload after saving; other devices sync with the slow poll.
                    coroutineScope {
                        listOf("connection_signals", "personal_schedule", "personal_status", "current_place", "person_connections", "account_profiles", "quick_messages",
                            "plan_proposals", "schedule_follow_requests").forEach { source ->
                            val changes = channel.postgresChangeFlow<PostgresAction>(schema = "public") { table = source }
                            launch { changes.collect { trySend(Unit) } }
                        }
                        // Live only while both the socket and this channel are up; a silent drop falls back to polling.
                        launch {
                            combine(client.realtime.status, channel.status) { socket, joined ->
                                socket == Realtime.Status.CONNECTED && joined == RealtimeChannel.Status.SUBSCRIBED
                            }.collect { now ->
                                // Back after a silent reconnect: catch up on what changed while it was down.
                                if (now && !live.value) trySend(Unit)
                                live.value = now
                            }
                        }
                        channel.subscribe(blockUntilSubscribed = true)
                        // Close the snapshot/subscription race, including a reconnect.
                        trySend(Unit)
                        awaitCancellation()
                    }
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { /* Bounded polling continues when the socket is unavailable. */ }
                finally {
                    live.value = false
                    withContext(NonCancellable) { withTimeoutOrNull(2_000) { client.realtime.removeChannel(channel) } }
                }
                delay(5_000)
            }
        }
    }.buffer(Channel.CONFLATED)
}
