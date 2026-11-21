package com.example.findme.widget

import com.example.findme.data.model.AccountProfile
import com.example.findme.data.model.ScheduleDto
import com.example.findme.data.model.StatusOverrideDto
import java.time.Instant
import java.time.ZoneId

/** One line of the "Tu gente" widget. */
data class PersonRow(val id: String, val name: String, val label: String, val title: String, val scene: WidgetScene, val busy: Boolean)

object PeopleRows {
    /**
     * Everyone connected except the owner, busy people first (they are the ones worth a glance), then by name.
     * Each person's status is computed in their own zone, as in the single-person widget.
     */
    fun build(owner: String, profiles: List<AccountProfile>, schedules: List<ScheduleDto>, statuses: List<StatusOverrideDto>,
              now: Instant): List<PersonRow> = profiles.filter { it.id != owner }.map { person ->
        val zone = runCatching { ZoneId.of(person.timezone) }.getOrDefault(ZoneId.systemDefault())
        val status = widgetStatus(person.displayName, schedules.filter { it.userId == person.id },
            statuses.filter { it.userId == person.id }, now.atZone(zone))
        PersonRow(person.id, person.displayName, status.label, status.title, status.scene, !status.free)
    }.sortedWith(compareBy<PersonRow>({ !it.busy }, { it.name.lowercase() }))

    /** How many rows fit: a 48dp header and about 46dp per person, at the system font scale. */
    fun capacity(heightDp: Float, fontScale: Float): Int =
        ((heightDp - 48f) / (46f * fontScale.coerceAtLeast(1f))).toInt().coerceIn(1, 6)
}
