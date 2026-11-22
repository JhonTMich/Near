package com.example.findme.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.findme.ui.theme.TextPrimary
import com.example.findme.ui.theme.TextSecondary
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val weekDayLetters = listOf("L", "M", "X", "J", "V", "S", "D")
private val accessibleDate = DateTimeFormatter.ofPattern("EEEE d 'de' MMMM", Locale.forLanguageTag("es"))

@Composable
fun WeeklyScheduleHeader(dates: List<LocalDate>, selectedDayOfWeek: Int, onDaySelected: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().selectableGroup(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        dates.forEachIndexed { index, date ->
            val selected = selectedDayOfWeek == index + 1
            Surface(
                selected = selected,
                onClick = { onDaySelected(index + 1) },
                modifier = Modifier.weight(1f).semantics { contentDescription = date.format(accessibleDate) },
                shape = RoundedCornerShape(12.dp),
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.background,
                contentColor = if (selected) MaterialTheme.colorScheme.onPrimary else TextPrimary,
            ) {
                Column(Modifier.fillMaxWidth().padding(vertical = 9.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(weekDayLetters[index], style = MaterialTheme.typography.labelMedium,
                        color = if (selected) MaterialTheme.colorScheme.onPrimary else TextSecondary)
                    Text(date.dayOfMonth.toString(), style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}
