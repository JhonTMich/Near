package com.example.findme

import com.example.findme.data.local.SkipUnchanged
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SkipUnchangedTest {
    private var now = 0L
    private val writes = SkipUnchanged<String>(maxAgeMs = 1_000, clock = { now })

    @Test
    fun theFirstWriteAlwaysHappens() {
        assertFalse(writes.unchanged("a"))
    }

    @Test
    fun theSameContentIsSkippedUntilItGetsOld() {
        writes.wrote("a")
        now = 999
        assertTrue(writes.unchanged("a"))
        now = 1_000
        assertFalse(writes.unchanged("a"))
    }

    @Test
    fun newContentIsWritten() {
        writes.wrote("a")
        assertFalse(writes.unchanged("b"))
    }

    @Test
    fun afterResetTheSameContentIsWrittenAgain() {
        writes.wrote("a")
        writes.reset()
        assertFalse(writes.unchanged("a"))
    }
}
