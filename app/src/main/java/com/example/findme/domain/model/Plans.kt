package com.example.findme.domain.model

import com.example.findme.data.model.PlanProposal
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

object Plans {
    val TITLES = listOf("Vernos", "Llamada", "Comer juntos", "Estudiar")
    val DURATIONS = listOf(30L, 60L, 120L, 180L)
    private val spanish = Locale.forLanguageTag("es")
    private val day = DateTimeFormatter.ofPattern("EEE d MMM", spanish)
    private val hour = DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT)

    /** Same limits as the database: in the future, within 31 days, up to twelve hours. */
    fun validate(title: String, start: Instant, end: Instant, now: Instant): String? = when {
        title.isBlank() || title.trim().length > 120 -> "El plan necesita un nombre de 1 a 120 caracteres."
        !start.isAfter(now) -> "Elige una hora que todavía no haya pasado."
        start.isAfter(now.plus(Duration.ofDays(31))) -> "Propón planes para el próximo mes como máximo."
        !end.isAfter(start) || Duration.between(start, end) > Duration.ofHours(12) -> "Un plan dura entre unos minutos y 12 horas."
        else -> null
    }

    /** "jue 26 nov, 18:00–19:00" in [zone]. */
    fun whenText(startsAt: String, endsAt: String?, zone: ZoneId): String {
        val start = runCatching { Instant.parse(startsAt).atZone(zone) }.getOrNull() ?: return ""
        val end = endsAt?.let { runCatching { Instant.parse(it).atZone(zone) }.getOrNull() }
        return "${start.format(day)}, ${start.format(hour)}" + (end?.let { "–${it.format(hour)}" } ?: "")
    }

    fun upcoming(plans: List<PlanProposal>, now: Instant): List<PlanProposal> = plans.filter { plan ->
        runCatching { Instant.parse(plan.endsAt).isAfter(now) }.getOrDefault(false)
    }.sortedBy { it.startsAt }
}
