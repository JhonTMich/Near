package com.example.findme.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.findme.ui.theme.*

val CardShape = RoundedCornerShape(22.dp)

/** The screen background: the base colour with a violet glow over the top, as in the brand screens. */
fun Modifier.nearBackdrop(base: Color, glow: Color): Modifier = background(base).drawBehind {
    drawRect(Brush.radialGradient(listOf(glow.copy(alpha = .9f), glow.copy(alpha = .35f), Color.Transparent),
        center = Offset(size.width * .15f, 0f), radius = size.width * 1.3f))
    drawRect(Brush.radialGradient(listOf(glow.copy(alpha = .45f), Color.Transparent),
        center = Offset(size.width, size.height * .35f), radius = size.width * .9f))
}

@Composable
fun Modifier.nearBackdrop(): Modifier = nearBackdrop(AppBackground, BackdropGlow)

/**
 * The card every screen is built from. Dark cards are drawn by their border; light ones float on a soft
 * shadow with a barely visible edge.
 */
@Composable
fun NearCard(
    modifier: Modifier = Modifier,
    shape: Shape = CardShape,
    color: Color = CardBg,
    border: Color = CardBorder,
    padding: PaddingValues = PaddingValues(18.dp),
    spacing: Dp = 12.dp,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(modifier.fillMaxWidth(), shape = shape, color = color, contentColor = TextPrimary,
        border = BorderStroke(1.dp, border), shadowElevation = if (IsLightTheme) 6.dp else 0.dp) {
        Column(Modifier.fillMaxWidth().padding(padding), verticalArrangement = Arrangement.spacedBy(spacing), content = content)
    }
}

/** A small rounded square holding an icon, at the start of a card title. */
@Composable
fun IconBubble(icon: ImageVector, tint: Color = MaterialTheme.colorScheme.primary, modifier: Modifier = Modifier,
    container: Color = InnerBg, size: Dp = 38.dp) {
    Box(modifier.size(size).background(container, RoundedCornerShape(12.dp))
        .border(1.dp, CardBorder, RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(size * .5f))
    }
}

/** A pill with a dot, for what someone is doing now: mint when free, rose when busy. */
@Composable
fun StateChip(text: String, ink: Color, background: Color, modifier: Modifier = Modifier, dot: Boolean = true) {
    Surface(modifier, color = background, contentColor = ink, shape = CircleShape) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            if (dot) Box(Modifier.size(6.dp).background(ink, CircleShape))
            Text(text, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** A room, a time or a counter in a small outlined box, set in monospace. */
@Composable
fun MonoTag(text: String, modifier: Modifier = Modifier, color: Color = TextSecondary) {
    Box(modifier.border(1.dp, CardBorder, RoundedCornerShape(8.dp)).padding(horizontal = 8.dp, vertical = 3.dp)) {
        Text(text, style = MonoLabel, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** The uppercase label over a group of cards or at the top of a card. */
@Composable
fun Eyebrow(text: String, modifier: Modifier = Modifier, color: Color = TextSecondary) {
    Text(text.uppercase(), modifier, color = color, style = MaterialTheme.typography.labelSmall)
}
