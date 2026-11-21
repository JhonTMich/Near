package com.example.findme.domain.model

import java.time.Duration
import java.time.LocalTime
import java.time.ZonedDateTime

/** A status shared "until 16:30": its length in minutes from [now], at most a day like any status. */
object StatusUntil {
    /** Minutes until the next [until] after [now]; a time that already passed today means tomorrow. */
    fun minutes(until: LocalTime, now: ZonedDateTime): Long {
        var end = now.toLocalDate().atTime(until).atZone(now.zone)
        if (!end.isAfter(now)) end = end.plusDays(1)
        return Duration.between(now, end).toMinutes().coerceIn(1, 1440)
    }

    /** The next round half hour at least 30 minutes away, to start the time picker on something useful. */
    fun suggestion(now: ZonedDateTime): LocalTime {
        val base = now.plusMinutes(30)
        val extra = (30 - base.minute % 30) % 30
        return base.plusMinutes(extra.toLong()).toLocalTime().withSecond(0).withNano(0)
    }

    fun lengthLabel(minutes: Long): String = when {
        minutes < 60 -> "$minutes min"
        minutes % 60 == 0L -> "${minutes / 60} h"
        else -> "${minutes / 60} h ${minutes % 60} min"
    }
}
