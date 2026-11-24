package com.example.findme.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.findme.ui.theme.TextPrimary
import com.example.findme.ui.theme.TextSecondary
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val weekDayLetters = listOf("L", "M", "X", "J", "V", "S", "D")
private val accessibleDate = DateTimeFormatter.ofPattern("EEEE d 'de' MMMM", Locale.forLanguageTag("es"))

/**
 * The week as one strip: the chosen day is a filled pill and the dots under each number count its
 * activities (up to three).
 */
@Composable
fun WeeklyScheduleHeader(dates: List<LocalDate>, selectedDayOfWeek: Int, activities: List<Int> = emptyList(),
    onDaySelected: (Int) -> Unit) {
    NearCard(shape = RoundedCornerShape(22.dp), padding = PaddingValues(5.dp), spacing = 0.dp) {
        Row(Modifier.fillMaxWidth().selectableGroup(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            dates.forEachIndexed { index, date ->
                val selected = selectedDayOfWeek == index + 1
                val ink = if (selected) MaterialTheme.colorScheme.onPrimary else TextPrimary
                val count = activities.getOrElse(index) { 0 }
                Surface(
                    selected = selected,
                    onClick = { onDaySelected(index + 1) },
                    modifier = Modifier.weight(1f).semantics {
                        contentDescription = date.format(accessibleDate) + when (count) { 0 -> ""; 1 -> ", 1 actividad"; else -> ", $count actividades" }
                    },
                    shape = RoundedCornerShape(16.dp),
                    color = if (selected) MaterialTheme.colorScheme.primary else androidx.compose.ui.graphics.Color.Transparent,
                    contentColor = ink,
                ) {
                    Column(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 7.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(weekDayLetters[index], style = MaterialTheme.typography.labelSmall,
                            color = if (selected) ink else TextSecondary)
                        Text(date.dayOfMonth.toString(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Row(Modifier.height(5.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                            repeat(count.coerceAtMost(3)) {
                                Box(Modifier.size(4.dp).background(if (selected) ink else TextSecondary, CircleShape))
                            }
                        }
                    }
                }
            }
        }
    }
}
