package com.example.findme.ui.screen.account

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.keyframes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp

/** Keeps failed sign-in feedback beside the submit button and above the open keyboard. */
@Composable
internal fun LoginError(message: String) {
    val shake = remember { Animatable(0f) }
    val visibility = remember { BringIntoViewRequester() }
    LaunchedEffect(message) {
        // Wait until the error has a measured rectangle before scrolling its parent.
        withFrameNanos { }
        visibility.bringIntoView()
        shake.animateTo(0f, keyframes {
            durationMillis = 400
            0f at 0
            -8f at 50
            8f at 100
            -6f at 150
            6f at 200
            -3f at 250
            3f at 300
            0f at 400
        })
    }
    Row(Modifier.fillMaxWidth().bringIntoViewRequester(visibility)
        .offset { IntOffset(shake.value.dp.roundToPx(), 0) }
        .testTag("login-error").semantics { liveRegion = LiveRegionMode.Assertive },
        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(Icons.Default.Info, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.error)
        Text(message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
    }
}
