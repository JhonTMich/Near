package com.example.findme.ui.screen.account

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.EventBusy
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.findme.domain.model.FreeDay
import com.example.findme.domain.model.FreeSlot
import com.example.findme.ui.components.Eyebrow
import com.example.findme.ui.theme.*
import java.time.Duration
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val hours = DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT)
private val spanish = Locale.forLanguageTag("es")

/** «45 min», «2 h», «1 h 30 min». */
internal fun freeLength(slot: FreeSlot): String {
    val minutes = Duration.between(slot.start, slot.end).toMinutes()
    val h = minutes / 60
    val m = minutes % 60
    return when {
        h == 0L -> "$m min"
        m == 0L -> "$h h"
        else -> "$h h $m min"
    }
}

/** «Hoy», «Mañana» or «Jueves 19». */
internal fun dayName(date: LocalDate, today: LocalDate): String = when (date) {
    today -> "Hoy"
    today.plusDays(1) -> "Mañana"
    else -> date.format(DateTimeFormatter.ofPattern("EEEE d", spanish)).replaceFirstChar { it.titlecase(spanish) }
}

private fun FreeSlot.endLabel(day: LocalDate) = if (end.toLocalDate() > day) "24:00" else end.format(hours)

/**
 * The common free time of the next 7 days at a glance: the next gap first, a strip of days that shows
 * how free each one is, and the selected day's gaps on a bar and as rows that open a plan.
 */
@Composable
internal fun CommonFreeTimeView(days: List<FreeDay>, allHours: Boolean, onAllHours: (Boolean) -> Unit, onPropose: (FreeSlot) -> Unit) {
    if (days.isEmpty()) return
    val today = days.first().date
    var selected by rememberSaveable(allHours) {
        mutableIntStateOf(days.indexOfFirst { it.slots.isNotEmpty() }.coerceAtLeast(0))
    }
    val day = days[selected.coerceIn(days.indices)]
    Column(Modifier.animateContentSize(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        NextGap(days, today, onPropose)
        DayStrip(days, selected, today) { selected = it }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(dayName(day.date, today), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f))
            RangeToggle(allHours, onAllHours)
        }
        DayBar(day, allHours)
        if (day.slots.isEmpty()) Row(Modifier.fillMaxWidth().background(InnerBg, RoundedCornerShape(16.dp)).padding(16.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(Icons.Default.EventBusy, null, tint = TextMuted)
            Text("Este día no coinciden", color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
        }
        day.slots.forEach { slot -> SlotRow(slot, day.date, onPropose) }
        Text("En tu zona horaria · huecos de 30 min o más", color = TextMuted, style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
    }
}

@Composable
private fun NextGap(days: List<FreeDay>, today: LocalDate, onPropose: (FreeSlot) -> Unit) {
    val next = days.firstNotNullOfOrNull { day -> day.slots.firstOrNull()?.let { day.date to it } }
    Surface(shape = RoundedCornerShape(22.dp), color = FreeSurface, contentColor = FreeInk,
        border = BorderStroke(1.dp, FreeInk.copy(alpha = .18f))) {
        Row(Modifier.fillMaxWidth().then(if (next != null) Modifier.clickable { onPropose(next.second) } else Modifier)
            .padding(18.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Eyebrow(if (next != null) "Próximo hueco juntos" else "Esta semana", color = FreeInk.copy(alpha = .8f))
                if (next != null) {
                    val (date, slot) = next
                    Text("${dayName(date, today)} · ${slot.start.format(hours)}", style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold)
                    Text("${freeLength(slot)} libres, hasta las ${slot.endLabel(date)}", style = MaterialTheme.typography.bodySmall)
                } else {
                    Text("Sin huecos en común", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text("Prueba con «Todo el día»", style = MaterialTheme.typography.bodySmall)
                }
            }
            if (next != null) Surface(shape = CircleShape, color = FreeInk, contentColor = FreeSurface) {
                Row(Modifier.padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Add, null, Modifier.size(16.dp)); Spacer(Modifier.width(4.dp))
                    Text("Plan", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

/** One pill per day; the dots say how many gaps it has, so a free day stands out without reading. */
@Composable
private fun DayStrip(days: List<FreeDay>, selected: Int, today: LocalDate, select: (Int) -> Unit) {
    // The seven days share the width, so the whole week is visible on a phone.
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        days.forEachIndexed { index, day ->
            val on = index == selected
            val container by animateColorAsState(if (on) MaterialTheme.colorScheme.primary else CardBg, label = "dayPill")
            val ink = if (on) MaterialTheme.colorScheme.onPrimary else if (day.slots.isEmpty()) TextMuted else TextPrimary
            Column(Modifier.weight(1f).clip(RoundedCornerShape(16.dp)).background(container)
                .border(1.dp, if (on) container else CardBorder, RoundedCornerShape(16.dp))
                .clickable(role = Role.Tab) { select(index) }.padding(vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(if (day.date == today) "Hoy" else day.date.format(DateTimeFormatter.ofPattern("EEE", spanish))
                    .removeSuffix(".").replaceFirstChar { it.titlecase(spanish) },
                    color = ink.copy(alpha = .8f), style = MaterialTheme.typography.labelSmall)
                Text("${day.date.dayOfMonth}", color = ink, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Row(Modifier.height(6.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    repeat(day.slots.size.coerceAtMost(3)) {
                        Box(Modifier.size(5.dp).background(if (on) ink else StatusGreenText, CircleShape))
                    }
                }
            }
        }
    }
}

@Composable
private fun RangeToggle(allHours: Boolean, change: (Boolean) -> Unit) {
    Row(Modifier.background(InnerBg, CircleShape).padding(3.dp)) {
        listOf(false to "8–22 h", true to "24 h").forEach { (value, label) ->
            val on = value == allHours
            Text(label, Modifier.clip(CircleShape).background(if (on) CardBg else InnerBg)
                .clickable(role = Role.RadioButton) { change(value) }.padding(horizontal = 12.dp, vertical = 6.dp),
                color = if (on) TextPrimary else TextMuted, style = MaterialTheme.typography.labelMedium,
                fontWeight = if (on) FontWeight.Bold else FontWeight.Normal)
        }
    }
}

/** The day as a bar: green where both are free. */
@Composable
private fun DayBar(day: FreeDay, allHours: Boolean) {
    val from = if (allHours) 0 else 8
    val to = if (allHours) 24 else 22
    val span = (to - from) * 60f
    fun position(slotTime: java.time.ZonedDateTime, isEnd: Boolean): Float {
        val minutes = if (isEnd && slotTime.toLocalDate() > day.date) 24 * 60 else slotTime.hour * 60 + slotTime.minute
        return ((minutes - from * 60) / span).coerceIn(0f, 1f)
    }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        BoxWithConstraints(Modifier.fillMaxWidth().height(12.dp).clip(CircleShape).background(InnerBg)) {
            day.slots.forEach { slot ->
                val start = position(slot.start, false)
                val end = position(slot.end, true)
                Box(Modifier.offset(x = maxWidth * start).width(maxWidth * (end - start)).fillMaxHeight()
                    .background(StatusGreenText, CircleShape))
            }
        }
        Row(Modifier.fillMaxWidth()) {
            listOf(from, (from + to) / 2, to).forEachIndexed { index, hour ->
                Text("%d:00".format(hour), color = TextMuted, style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.weight(1f), textAlign = when (index) { 0 -> TextAlign.Start; 1 -> TextAlign.Center; else -> TextAlign.End })
            }
        }
    }
}

@Composable
private fun SlotRow(slot: FreeSlot, date: LocalDate, onPropose: (FreeSlot) -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(CardBg)
        .border(1.dp, CardBorder, RoundedCornerShape(18.dp)).clickable { onPropose(slot) }.padding(14.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.size(40.dp).background(FreeSurface, RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
            Icon(Icons.Default.Schedule, null, tint = FreeInk, modifier = Modifier.size(20.dp))
        }
        Column(Modifier.weight(1f)) {
            Text("${slot.start.format(hours)} – ${slot.endLabel(date)}", style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold)
            Text(freeLength(slot), color = TextSecondary, style = MaterialTheme.typography.bodySmall)
        }
        FilledTonalIconButton(onClick = { onPropose(slot) }) { Icon(Icons.Default.Add, "Proponer un plan") }
    }
}
