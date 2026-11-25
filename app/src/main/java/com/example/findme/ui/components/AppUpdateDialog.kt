package com.example.findme.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.findme.domain.model.ReleaseNotes
import com.example.findme.ui.screen.UpdateState
import com.example.findme.ui.theme.*

/** How many new things show before «Ver N más»: enough to tempt, not a wall of text. */
private const val SHOWN_ITEMS = 3

/**
 * The update offer: the version on a lit header and its news as short rows with an icon, each opening to
 * its detail on a tap. While downloading, only the progress.
 */
@Composable
fun AppUpdateDialog(state: UpdateState, onUpdate: () -> Unit, onLater: () -> Unit, onCancelDownload: () -> Unit) {
    val release = state.release
    if (!state.showDialog || release == null) return
    val version = release.version.removePrefix("v")
    val items = remember(release.notes) { ReleaseNotes.items(release.notes) }
    Dialog(onDismissRequest = { if (!state.downloading) onLater() },
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = !state.downloading)) {
        Surface(Modifier.padding(horizontal = 24.dp).widthIn(max = 420.dp).fillMaxWidth().animateContentSize(),
            shape = RoundedCornerShape(28.dp), color = CardBg, contentColor = TextPrimary, border = BorderStroke(1.dp, HeroBorder)) {
            Column {
                Header(version, if (state.downloading) "Descargando…" else when (items.size) {
                    0 -> "Mejoras y arreglos"
                    1 -> "1 novedad"
                    else -> "${items.size} novedades"
                })
                Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (state.downloading) Progress(state.progress)
                    else if (items.isEmpty()) Text(state.message, color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
                    else News(items)
                    Actions(state.downloading, onUpdate, onLater, onCancelDownload)
                }
            }
        }
    }
}

@Composable
private fun Header(version: String, subtitle: String) {
    Box(Modifier.fillMaxWidth().background(Brush.linearGradient(HeroGradient)).padding(20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(Modifier.size(52.dp).background(MaterialTheme.colorScheme.primary, RoundedCornerShape(16.dp)),
                contentAlignment = Alignment.Center) {
                Icon(Icons.Default.RocketLaunch, null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(28.dp))
            }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Eyebrow("Nueva versión", color = MaterialTheme.colorScheme.primary)
                Text("Near $version", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            }
        }
    }
}

@Composable
private fun News(items: List<ReleaseNotes.Item>) {
    var all by rememberSaveable { mutableStateOf(false) }
    val shown = if (all) items else items.take(SHOWN_ITEMS)
    Column(Modifier.heightIn(max = 340.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        shown.forEachIndexed { index, item -> NewsRow(item, index) }
        if (!all && items.size > SHOWN_ITEMS) TextButton(onClick = { all = true }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
            Text("Ver ${items.size - SHOWN_ITEMS} más")
        }
    }
}

@Composable
private fun NewsRow(item: ReleaseNotes.Item, index: Int) {
    var open by rememberSaveable(item.headline) { mutableStateOf(false) }
    val (icon, tint) = look(item.headline + " " + item.detail.orEmpty(), index)
    val turn by animateFloatAsState(if (open) 180f else 0f, tween(200), label = "newsChevron")
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(InnerBg)
        .then(if (item.detail != null) Modifier.clickable { open = !open } else Modifier)
        .padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        IconBubble(icon, tint = tint, container = tint.copy(alpha = .14f), size = 36.dp)
        Column(Modifier.weight(1f).padding(top = 2.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(item.headline, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold,
                maxLines = if (open) Int.MAX_VALUE else 2, overflow = TextOverflow.Ellipsis)
            AnimatedVisibility(open && item.detail != null) {
                Text(item.detail.orEmpty(), style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            }
        }
        if (item.detail != null) Icon(Icons.Default.ExpandMore, if (open) "Ver menos" else "Ver más",
            tint = TextMuted, modifier = Modifier.size(20.dp).rotate(turn))
    }
}

/** An icon that hints at what the note is about, in one of the brand colours. */
@Composable
private fun look(text: String, index: Int): Pair<ImageVector, Color> {
    val words = text.lowercase()
    val icon = when {
        listOf("lugar", "ubicación", "calle", "camino").any { it in words } -> Icons.Default.Place
        listOf("sesión", "entrar", "contraseña", "cuenta").any { it in words } -> Icons.Default.Lock
        listOf("aviso", "notific", "recordatorio", "alarma").any { it in words } -> Icons.Default.NotificationsActive
        listOf("plan", "sincroniz").any { it in words } -> Icons.Default.Event
        listOf("horario", "actividad", "clase", "asistencia").any { it in words } -> Icons.Default.Schedule
        listOf("widget").any { it in words } -> Icons.Default.Widgets
        listOf("persona", "grupo", "contacto").any { it in words } -> Icons.Default.Group
        listOf("arregl", "error", "falla", "corrig").any { it in words } -> Icons.Default.Build
        else -> Icons.Default.AutoAwesome
    }
    val tints = listOf(BrandAccent, BrandSky, BrandBlue, BrandSlate)
    return icon to tints[index % tints.size]
}

@Composable
private fun Progress(progress: Float) {
    val shown by animateFloatAsState(progress.coerceIn(0f, 1f), tween(300), label = "updateProgress")
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Preparando la instalación", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
            Text("${(shown * 100).toInt()}%", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
        }
        LinearProgressIndicator(progress = { shown }, modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape),
            trackColor = InnerBg, gapSize = 0.dp, drawStopIndicator = {})
    }
}

@Composable
private fun Actions(downloading: Boolean, onUpdate: () -> Unit, onLater: () -> Unit, onCancel: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        if (downloading) OutlinedButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) { Text("Cancelar") }
        else {
            Button(onClick = onUpdate, modifier = Modifier.fillMaxWidth().height(48.dp)) {
                Icon(Icons.Default.Download, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Actualizar ahora")
            }
            TextButton(onClick = onLater, modifier = Modifier.fillMaxWidth()) { Text("Más tarde", color = TextSecondary) }
        }
    }
}
