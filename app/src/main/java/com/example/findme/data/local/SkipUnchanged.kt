package com.example.findme.data.local

import android.os.SystemClock

/**
 * Remembers the last content written somewhere, so a reload that brings the same data can skip the write.
 * After [maxAgeMs] it writes again anyway, to keep the stored sync time from going stale.
 */
class SkipUnchanged<T>(private val maxAgeMs: Long = 5 * 60_000L, private val clock: () -> Long = SystemClock::elapsedRealtime) {
    private var last: T? = null
    private var at = 0L

    @Synchronized fun unchanged(content: T): Boolean = last == content && clock() - at < maxAgeMs

    @Synchronized fun wrote(content: T) { last = content; at = clock() }

    /** After the stored copy was removed (sign out), the next write must happen. */
    @Synchronized fun reset() { last = null }
}
