package com.example.findme.ui.components

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.findme.domain.model.UserCurrentStatus
import com.example.findme.data.model.ActivityCategory
import com.example.findme.ui.theme.*

fun formatDurationMinutes(minutes: Long): String {
    val value = minutes.coerceAtLeast(0)
    return if (value >= 60) "${value / 60} h ${value % 60} min" else "$value min"
}

/**
 * What a person is doing now, on the lit violet card: who, where the status comes from, the activity and,
 * under a rule, what comes next.
 */
@Composable
fun HeroStatusCard(
    status: UserCurrentStatus,
    modifier: Modifier = Modifier,
    heading: String = "Estado actual",
    footer: String? = null,
    actions: (@Composable () -> Unit)? = null
) {
    val accent = when (status) {
        is UserCurrentStatus.InClass -> ActivityCategory.fromKey(status.currentSchedule.category, status.currentSchedule.title).accentColor
        is UserCurrentStatus.Override -> MaterialTheme.colorScheme.primary
        is UserCurrentStatus.Error, is UserCurrentStatus.Loading -> TextSecondary
        else -> StatusGreenText
    }
    NearCard(modifier.animateContentSize(), shape = RoundedCornerShape(24.dp), color = HeroGradient.last(), border = HeroBorder,
        padding = PaddingValues(0.dp)) {
        Column(Modifier.fillMaxWidth().background(Brush.linearGradient(HeroGradient)).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ProfileAvatar(status.displayName, Modifier.size(46.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    Eyebrow(heading, color = MaterialTheme.colorScheme.primary)
                    Text(status.displayName, style = MaterialTheme.typography.headlineSmall, color = TextPrimary)
                }
            }
            Surface(shape = CircleShape, color = TextPrimary.copy(alpha = .08f), contentColor = TextPrimary,
                border = BorderStroke(1.dp, CardBorder)) {
                Row(Modifier.padding(horizontal = 10.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (status is UserCurrentStatus.InClass || status is UserCurrentStatus.Override) {
                        Box(Modifier.size(6.dp).background(accent, CircleShape))
                    }
                    Text(when (status) {
                        is UserCurrentStatus.InClass -> if (status.onTheWay) "En camino" else "Actividad en curso"
                        is UserCurrentStatus.Override -> "Estado compartido"
                        is UserCurrentStatus.Loading -> "Consultando"
                        is UserCurrentStatus.Error -> "Sin información"
                        else -> "Según el horario"
                    }, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                }
            }
            val title = when (status) {
                is UserCurrentStatus.InClass -> status.headline
                is UserCurrentStatus.Override -> status.customLocation
                is UserCurrentStatus.Loading -> "Consultando actividad…"
                is UserCurrentStatus.Error -> "Estado no disponible"
                else -> "Sin actividad en curso"
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = TextPrimary)
                Text(listOfNotNull(status.place, when (status) {
                    is UserCurrentStatus.InClass -> listOf(status.currentSchedule.location,
                        "Termina en ${formatDurationMinutes(status.remainingMinutes)}").filter { it.isNotBlank() }.joinToString(" · ")
                    is UserCurrentStatus.Override -> status.customMessage ?: "Al terminar este estado, se mostrará el horario."
                    is UserCurrentStatus.Loading -> "Preparando la información de hoy"
                    is UserCurrentStatus.Error -> status.message
                    else -> if (status.place != null) "Según el horario de hoy" else "Según el horario de hoy. La ubicación no se comparte en tiempo real."
                }).joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            }
            if (status is UserCurrentStatus.InClass) {
                val progress by animateFloatAsState(status.progress.coerceIn(0f, 1f), tween(800), label = "activityProgress")
                LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().height(5.dp),
                    color = accent, trackColor = TextPrimary.copy(alpha = .12f), gapSize = 0.dp, drawStopIndicator = {})
            }
            footer?.let {
                HorizontalDivider(color = CardBorder)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Default.Schedule, null, Modifier.size(15.dp), tint = TextSecondary)
                    Text(it, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                }
            }
            actions?.invoke()
        }
    }
}
