package com.example.findme.domain.model

import com.example.findme.data.model.ScheduleDto
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID

/** One event of a calendar file that Near can turn into an activity, in the account's zone. */
data class IcsCandidate(
    val uid: String,
    val title: String,
    val location: String,
    /** Weekdays (1 = Monday) of a weekly event; empty for a one-off event. */
    val days: List<Int>,
    /** The date of a one-off event. */
    val date: LocalDate?,
    val start: String,
    val end: String,
    val endsNextDay: Boolean
) {
    val weekly: Boolean get() = days.isNotEmpty()

    /** Same ids when the same file is imported again, so it updates instead of duplicating. */
    fun toSchedules(category: String): List<ScheduleDto> =
        if (weekly) days.map { day ->
            ScheduleDto(UUID.nameUUIDFromBytes("ics:$uid:$day".toByteArray()).toString(), dayOfWeek = day,
                startTime = start, endTime = end, title = title, location = location, category = category, endsNextDay = endsNextDay)
        } else listOf(ScheduleDto(UUID.nameUUIDFromBytes("ics:$uid:${date}".toByteArray()).toString(),
            dayOfWeek = date!!.dayOfWeek.value, startTime = start, endTime = end, title = title, location = location,
            category = category, endsNextDay = endsNextDay, oneOffDate = date.toString()))
}

data class IcsResult(val candidates: List<IcsCandidate>, val skipped: Int)

/**
 * Reads iCalendar (.ics) files as exported by Google Calendar, Outlook or a university portal.
 * Supported: timed events, weekly or daily repetition (BYDAY), time zones (TZID, UTC or floating).
 * Skipped and counted: all-day events, other repetitions, events longer than a day, past or far one-off events.
 */
object IcsImport {
    const val MAX_EVENTS = 100
    const val MAX_BYTES = 1_000_000
    private val dateTime = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss", Locale.ROOT)
    private val date = DateTimeFormatter.ofPattern("yyyyMMdd", Locale.ROOT)
    private val hour = DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT)
    private val byDay = mapOf("MO" to 1, "TU" to 2, "WE" to 3, "TH" to 4, "FR" to 5, "SA" to 6, "SU" to 7)

    private data class Property(val name: String, val params: Map<String, String>, val value: String)

    private fun unfold(text: String): List<String> {
        val lines = mutableListOf<String>()
        text.replace("\r\n", "\n").replace('\r', '\n').split('\n').forEach { line ->
            if ((line.startsWith(" ") || line.startsWith("\t")) && lines.isNotEmpty()) lines[lines.lastIndex] += line.substring(1)
            else lines += line
        }
        return lines
    }

    private fun property(line: String): Property? {
        val colon = line.indexOf(':').takeIf { it > 0 } ?: return null
        val head = line.substring(0, colon).split(';')
        val params = head.drop(1).mapNotNull { part ->
            part.split('=', limit = 2).takeIf { it.size == 2 }?.let { (key, value) -> key.uppercase() to value.trim('"') }
        }.toMap()
        return Property(head.first().uppercase(), params, line.substring(colon + 1))
    }

    private fun text(value: String) = value.replace("\\n", " ").replace("\\N", " ").replace("\\,", ",")
        .replace("\\;", ";").replace("\\\\", "\\").trim()

    /** Null for all-day values and anything unreadable. */
    private fun instant(property: Property, zone: ZoneId): ZonedDateTime? {
        if (property.params["VALUE"]?.uppercase() == "DATE" || property.value.length == 8) return null
        val value = property.value.trim()
        return runCatching {
            when {
                value.endsWith("Z") -> LocalDateTime.parse(value.dropLast(1), dateTime).atZone(ZoneOffset.UTC)
                property.params["TZID"] != null -> LocalDateTime.parse(value, dateTime)
                    .atZone(runCatching { ZoneId.of(property.params.getValue("TZID")) }.getOrDefault(zone))
                else -> LocalDateTime.parse(value, dateTime).atZone(zone)
            }.withZoneSameInstant(zone)
        }.getOrNull()
    }

    private fun duration(value: String): Duration? = runCatching { Duration.parse(value.trim().replace("P0D", "PT0S")) }.getOrNull()

    fun parse(content: String, zone: ZoneId, today: LocalDate): IcsResult {
        val candidates = mutableListOf<IcsCandidate>()
        var skipped = 0
        var event: MutableList<Property>? = null
        for (line in unfold(content)) {
            val trimmed = line.trim()
            when {
                trimmed.equals("BEGIN:VEVENT", ignoreCase = true) -> event = mutableListOf()
                trimmed.equals("END:VEVENT", ignoreCase = true) -> {
                    val properties = event ?: continue
                    event = null
                    val candidate = candidate(properties, zone, today)
                    if (candidate == null || candidates.size >= MAX_EVENTS) skipped++ else candidates += candidate
                }
                event != null -> property(line)?.let { event!!.add(it) }
            }
        }
        return IcsResult(candidates, skipped)
    }

    private fun candidate(properties: List<Property>, zone: ZoneId, today: LocalDate): IcsCandidate? {
        fun first(name: String) = properties.firstOrNull { it.name == name }
        if (first("STATUS")?.value?.uppercase() == "CANCELLED") return null
        val start = first("DTSTART")?.let { instant(it, zone) } ?: return null
        val end = first("DTEND")?.let { instant(it, zone) }
            ?: first("DURATION")?.let { duration(it.value) }?.let { start.plus(it) }
            ?: start.plusHours(1)
        val length = Duration.between(start, end)
        if (length <= Duration.ZERO || length > Duration.ofHours(24)) return null
        val endsNextDay = end.toLocalDate().isAfter(start.toLocalDate())
        if (endsNextDay && end.toLocalTime() > start.toLocalTime()) return null
        val title = text(first("SUMMARY")?.value.orEmpty()).ifBlank { "Evento importado" }.take(120)
        val location = text(first("LOCATION")?.value.orEmpty()).take(160)
        val uid = first("UID")?.value?.trim()?.ifBlank { null } ?: "${start.toEpochSecond()}:$title"
        val rule = first("RRULE")?.value?.uppercase()?.split(';')?.mapNotNull { part ->
            part.split('=', limit = 2).takeIf { it.size == 2 }?.let { it[0] to it[1] }
        }?.toMap()
        val startText = start.toLocalTime().format(hour)
        val endText = end.toLocalTime().format(hour)
        if (rule == null) {
            val day = start.toLocalDate()
            if (day.isBefore(today) || day.isAfter(today.plusDays(366))) return null
            return IcsCandidate(uid, title, location, emptyList(), day, startText, endText, endsNextDay)
        }
        if ((rule["INTERVAL"] ?: "1") != "1") return null
        // A series that already ended is not part of the current schedule.
        rule["UNTIL"]?.let { until ->
            val last = runCatching { LocalDate.parse(until.take(8), date) }.getOrNull()
            if (last != null && last.isBefore(today)) return null
        }
        val days = when (rule["FREQ"]) {
            "WEEKLY" -> rule["BYDAY"]?.split(',')?.mapNotNull { byDay[it.takeLast(2)] }?.distinct()?.sorted()
                ?.ifEmpty { null } ?: listOf(start.dayOfWeek.value)
            "DAILY" -> rule["BYDAY"]?.split(',')?.mapNotNull { byDay[it.takeLast(2)] }?.distinct()?.sorted()
                ?.ifEmpty { null } ?: DayOfWeek.entries.map { it.value }
            else -> return null
        }
        return IcsCandidate(uid, title, location, days, null, startText, endText, endsNextDay)
    }
}
