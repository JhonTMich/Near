package com.example.findme.ui.screen.account

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.EventBusy
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.findme.data.model.ActivityCategory
import com.example.findme.data.model.ScheduleDto
import com.example.findme.domain.model.formatScheduleTime
import com.example.findme.ui.components.Eyebrow
import com.example.findme.ui.theme.*
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

private val spanish = Locale.forLanguageTag("es")

/** What applying [draft] changes from what is saved now: the activities to add and to remove. */
internal fun followedChanges(saved: Set<String>, draft: Set<String>): Pair<Set<String>, Set<String>> =
    (draft - saved) to (saved - draft)

/** «Se añadieron 2 actividades y se quitó 1.» */
internal fun followedChangeNotice(added: Int, removed: Int): String {
    fun count(n: Int) = if (n == 1) "1 actividad" else "$n actividades"
    return when {
        added > 0 && removed > 0 -> "Horario actualizado: ${count(added)} añadida${if (added == 1) "" else "s"} y ${count(removed)} quitada${if (removed == 1) "" else "s"}."
        added > 0 -> "Añadiste ${count(added)} a tu horario."
        else -> "Quitaste ${count(removed)} de tu horario."
    }
}

/** The activities by day, Monday first, and the one-off ones by their date after the weekly ones. */
internal fun followedGroups(activities: List<ScheduleDto>): List<Pair<String, List<ScheduleDto>>> {
    val weekly = activities.filter { it.oneOffDate == null }.groupBy { it.dayOfWeek }.toSortedMap()
        .map { (day, list) ->
            DayOfWeek.of(day).getDisplayName(TextStyle.FULL, spanish).replaceFirstChar { it.titlecase(spanish) } to
                list.sortedBy { it.startTime }
        }
    val dated = activities.filter { it.oneOffDate != null }.groupBy { it.oneOffDate!! }.toSortedMap()
        .map { (date, list) ->
            val label = runCatching { LocalDate.parse(date).format(DateTimeFormatter.ofPattern("EEEE d 'de' MMMM", spanish)) }
                .getOrDefault(date).replaceFirstChar { it.titlecase(spanish) }
            label to list.sortedBy { it.startTime }
        }
    return weekly + dated
}

/**
 * Choosing which activities of a followed schedule to show. Taps only change a draft on the phone; nothing
 * is saved until «Aplicar», which sends the whole selection in one go.
 */
@Composable
internal fun FollowActivitiesView(activities: List<ScheduleDto>, saved: Set<String>, busy: Boolean, error: String?,
    onApply: (add: Set<String>, remove: Set<String>) -> Unit, onDone: () -> Unit) {
    var draft by rememberSaveable(saved) { mutableStateOf(saved.filter { id -> activities.any { it.id == id } }.toSet()) }
    var applying by remember { mutableStateOf(false) }
    // The save is seen starting before it is seen ending: busy reaches the screen a frame after the tap.
    var started by remember { mutableStateOf(false) }
    // Closes once the save finishes well; on an error it stays open with the draft intact.
    LaunchedEffect(busy, applying) {
        if (applying && busy) started = true
        if (applying && started && !busy) { applying = false; started = false; if (error == null) onDone() }
    }
    if (activities.isEmpty()) {
        Row(Modifier.fillMaxWidth().background(InnerBg, RoundedCornerShape(16.dp)).padding(16.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(Icons.Default.EventBusy, null, tint = TextMuted)
            Text("No tiene actividades para sincronizar", color = TextSecondary)
        }
        return
    }
    val (add, remove) = followedChanges(saved, draft)
    val allIds = activities.map { it.id }.toSet()
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("${draft.size} de ${activities.size} elegidas", color = TextSecondary,
                style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
            TextButton(onClick = { draft = if (draft == allIds) emptySet() else allIds }, enabled = !busy) {
                Text(if (draft == allIds) "Quitar todas" else "Elegir todas")
            }
        }
        followedGroups(activities).forEach { (day, list) ->
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Eyebrow(day)
                list.forEach { activity ->
                    ActivityChoice(activity, activity.id in draft, enabled = !busy) { on ->
                        draft = if (on) draft + activity.id else draft - activity.id
                    }
                }
            }
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        Button(onClick = { applying = true; onApply(add, remove) }, enabled = !busy && (add.isNotEmpty() || remove.isNotEmpty()),
            modifier = Modifier.fillMaxWidth().height(50.dp)) {
            if (busy && applying) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = LocalContentColor.current)
            else Text(when {
                add.isEmpty() && remove.isEmpty() -> "Sin cambios"
                else -> listOfNotNull(add.size.takeIf { it > 0 }?.let { "+$it" }, remove.size.takeIf { it > 0 }?.let { "−$it" })
                    .joinToString(" ", prefix = "Aplicar  ")
            })
        }
    }
}

@Composable
private fun ActivityChoice(activity: ScheduleDto, checked: Boolean, enabled: Boolean, change: (Boolean) -> Unit) {
    val category = ActivityCategory.fromKey(activity.category, activity.title)
    val accent = category.accentColor
    val border by animateColorAsState(if (checked) accent else CardBorder, label = "choiceBorder")
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(if (checked) accent.copy(alpha = .10f) else CardBg)
        .border(if (checked) 1.5.dp else 1.dp, border, RoundedCornerShape(16.dp))
        .clickable(enabled = enabled, role = Role.Checkbox) { change(!checked) }.padding(12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.size(40.dp).background(accent.copy(alpha = .16f), RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
            Icon(category.icon, null, tint = accent, modifier = Modifier.size(20.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(activity.title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(listOf("${formatScheduleTime(activity.startTime)} – ${formatScheduleTime(activity.endTime)}", activity.location)
                .filter { it.isNotBlank() }.joinToString(" · "),
                color = TextSecondary, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Box(Modifier.size(26.dp).clip(CircleShape).background(if (checked) accent else androidx.compose.ui.graphics.Color.Transparent)
            .border(2.dp, if (checked) accent else TextMuted, CircleShape), contentAlignment = Alignment.Center) {
            if (checked) Icon(Icons.Default.Check, null, tint = CardBg, modifier = Modifier.size(16.dp))
        }
    }
}
