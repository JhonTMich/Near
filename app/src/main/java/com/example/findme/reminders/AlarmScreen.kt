package com.example.findme.reminders

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AlarmOff
import androidx.compose.material.icons.filled.Snooze
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.findme.ui.theme.*
import kotlinx.coroutines.delay
import java.time.Duration
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private val clock = DateTimeFormatter.ofPattern("HH:mm")

/** «Empieza en 10 min · 15:00», «Empieza ahora», or null for a test ring with no activity. */
internal fun startsLabel(startsAt: Long, now: Instant): String? {
    if (startsAt <= 0) return null
    val start = Instant.ofEpochMilli(startsAt)
    val minutes = Duration.between(now, start).toMinutes()
    val at = start.atZone(ZoneId.systemDefault()).format(clock)
    return when {
        minutes <= 0 -> "Empieza ahora · $at"
        minutes < 60 -> "Empieza en $minutes min · $at"
        else -> "Empieza en ${minutes / 60} h${(minutes % 60).takeIf { it > 0 }?.let { " $it min" }.orEmpty()} · $at"
    }
}

/** Full screen while an activity alarm rings: a ringing clock, what starts and when, and stop or snooze. */
@Composable
internal fun AlarmScreen(ringing: ReminderAlarm.Ringing, onStop: () -> Unit, onSnooze: () -> Unit,
    now: () -> Instant = Instant::now) {
    val instant by produceState(now()) { while (true) { value = now(); delay(1_000) } }
    val time = instant.atZone(ZoneId.systemDefault()).toLocalTime()
    Box(Modifier.fillMaxSize().background(AppBackground)) {
        // A glow of the brand colour from the top, like light coming in.
        Box(Modifier.fillMaxWidth().fillMaxHeight(.6f).background(Brush.verticalGradient(listOf(BackdropGlow, Color.Transparent))))
        Column(Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 24.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.weight(.4f))
            Box(Modifier.size(260.dp), contentAlignment = Alignment.Center) {
                Ripples(MaterialTheme.colorScheme.primary)
                RingingClock(time, Modifier.size(190.dp))
            }
            Spacer(Modifier.height(20.dp))
            Text(time.format(clock), fontSize = 72.sp, fontWeight = FontWeight.Light, color = TextPrimary)
            startsLabel(ringing.startsAt, instant)?.let { label ->
                Surface(shape = CircleShape, color = AccentContainer, contentColor = MaterialTheme.colorScheme.primary) {
                    Text(label, Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                }
            }
            Spacer(Modifier.height(18.dp))
            Text(ringing.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold,
                color = TextPrimary, textAlign = TextAlign.Center)
            if (ringing.body.isNotBlank()) Text(ringing.body, Modifier.padding(top = 4.dp), style = MaterialTheme.typography.bodyLarge,
                color = TextSecondary, textAlign = TextAlign.Center)
            Spacer(Modifier.weight(.6f))
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (ringing.snoozeUntil != null) FilledTonalButton(onClick = onSnooze, Modifier.fillMaxWidth().height(58.dp)) {
                    Icon(Icons.Default.Snooze, null, Modifier.size(20.dp)); Spacer(Modifier.width(10.dp))
                    Text("Posponer ${ringing.snoozeMinutes} min", style = MaterialTheme.typography.titleMedium)
                }
                Button(onClick = onStop, Modifier.fillMaxWidth().height(64.dp)) {
                    Icon(Icons.Default.AlarmOff, null, Modifier.size(22.dp)); Spacer(Modifier.width(10.dp))
                    Text("Detener", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

/** Rings that spread out from behind the clock and fade, one after another. */
@Composable
private fun Ripples(color: Color) {
    val transition = rememberInfiniteTransition(label = "ripples")
    val phase by transition.animateFloat(0f, 1f, infiniteRepeatable(tween(2400, easing = LinearEasing)), label = "ripplePhase")
    Canvas(Modifier.fillMaxSize()) {
        val max = size.minDimension / 2
        repeat(3) { index ->
            val p = (phase + index / 3f) % 1f
            drawCircle(color.copy(alpha = (1 - p) * .28f), radius = max * (.55f + .45f * p), style = Stroke(width = 3.dp.toPx()))
        }
        drawCircle(color.copy(alpha = .10f), radius = max * .55f)
    }
}

/**
 * An alarm clock drawn on a canvas, showing [time]: it rings in bursts, shaking from side to side while the
 * hammer strikes the bells, then rests a moment.
 */
@Composable
internal fun RingingClock(time: LocalTime, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "ring")
    // One cycle: about a second ringing, then a short rest.
    val cycle by transition.animateFloat(0f, 1f, infiniteRepeatable(tween(1600, easing = LinearEasing)), label = "ringCycle")
    val ringing = (cycle / .65f).coerceAtMost(1f)
    val envelope = if (cycle < .65f) sin(ringing * PI).toFloat() else 0f
    val shake = sin(cycle * 2 * PI * 16).toFloat() * envelope
    val body = MaterialTheme.colorScheme.primary
    val face = CardBg
    val ink = TextPrimary
    val accent = BrandBlue
    val seconds by produceState(time.toSecondOfDay().toFloat()) {
        while (true) { value = LocalTime.now().toSecondOfDay() + (System.currentTimeMillis() % 1000) / 1000f; delay(50) }
    }
    Canvas(modifier.graphicsLayer {
        rotationZ = shake * 9f
        translationX = shake * 4.dp.toPx()
    }) {
        val c = Offset(size.width / 2, size.height * .56f)
        val r = size.minDimension * .34f
        val line = r * .09f
        // Legs.
        listOf(135.0, 45.0).forEach { angle ->
            val a = Math.toRadians(angle)
            val from = c + polar(r * .8f, a)
            drawLine(body, from, from + polar(r * .32f, a), line * 1.2f, StrokeCap.Round)
        }
        // Bells, each shaking against the hammer.
        listOf(-130.0, -50.0).forEachIndexed { index, angle ->
            val bell = c + polar(r * 1.02f, Math.toRadians(angle))
            rotate(degrees = (angle + 90).toFloat() + shake * (if (index == 0) -10f else 10f), pivot = bell) {
                drawArc(body, 180f, 180f, true, topLeft = bell - Offset(r * .38f, r * .3f), size = Size(r * .76f, r * .6f))
            }
        }
        // Hammer between the bells.
        val top = Offset(c.x, c.y - r)
        val head = Offset(c.x + shake * r * .3f, c.y - r * 1.28f)
        drawLine(ink, top, head, line * .7f, StrokeCap.Round)
        drawCircle(ink, line * .9f, head)
        // Body and face.
        drawCircle(body, r)
        drawCircle(face, r * .82f, c)
        repeat(12) { hour ->
            val a = hour * PI / 6
            val long = hour % 3 == 0
            drawLine(ink.copy(alpha = if (long) .8f else .35f), c + polar(r * (if (long) .62f else .68f), a - PI / 2),
                c + polar(r * .74f, a - PI / 2), line * (if (long) .55f else .4f), StrokeCap.Round)
        }
        hand(c, r * .42f, (time.hour % 12 + time.minute / 60f) / 12f, ink, line * .8f)
        hand(c, r * .6f, (time.minute + time.second / 60f) / 60f, ink, line * .6f)
        hand(c, r * .66f, (seconds % 60f) / 60f, accent, line * .3f)
        drawCircle(accent, line * .7f, c)
    }
}

private fun polar(length: Float, radians: Double) = Offset((cos(radians) * length).toFloat(), (sin(radians) * length).toFloat())

/** A hand from the centre, [turn] being 0..1 of a full turn from twelve. */
private fun DrawScope.hand(c: Offset, length: Float, turn: Float, color: Color, width: Float) {
    val a = turn * 2 * PI - PI / 2
    drawLine(color, c, c + polar(length, a), width, StrokeCap.Round)
}
