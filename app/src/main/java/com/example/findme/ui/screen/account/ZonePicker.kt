package com.example.findme.ui.screen.account

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.Circle
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.CameraPositionState
import androidx.compose.runtime.remember

/** Where the map opens with nothing chosen and no location yet: Lima, the app's default zone. */
private val FALLBACK = LatLng(-12.0464, -77.0428)

/**
 * A map to place a zone: tapping moves the point, and the circle shows the size it will have. It follows
 * [point] when it changes (a found address or a tap), opens on [center] (the current location) while nothing
 * is chosen, and never zooms out from what the person chose. Zoom buttons are on because the sheet around
 * it may take vertical drags.
 */
@Composable
internal fun ZonePicker(point: LatLng?, radius: Float, onPick: (LatLng) -> Unit, modifier: Modifier = Modifier,
    center: LatLng? = null) {
    // Built directly: the remember* helpers are inline and compiled for Java 17, newer than this project.
    val camera = remember { CameraPositionState(CameraPosition.fromLatLngZoom(point ?: FALLBACK, if (point == null) 11f else 16f)) }
    LaunchedEffect(point ?: center) {
        val target = point ?: center ?: return@LaunchedEffect
        camera.animate(CameraUpdateFactory.newLatLngZoom(target, maxOf(camera.position.zoom, 15f)))
    }
    val accent = MaterialTheme.colorScheme.primary
    GoogleMap(
        modifier = modifier.fillMaxWidth().height(280.dp).clip(RoundedCornerShape(16.dp)),
        cameraPositionState = camera,
        uiSettings = MapUiSettings(zoomControlsEnabled = true, mapToolbarEnabled = false, myLocationButtonEnabled = false),
        onMapClick = onPick
    ) {
        if (point != null) {
            Marker(state = remember(point) { MarkerState(point) })
            Circle(center = point, radius = radius.toDouble(), strokeColor = accent, strokeWidth = 4f,
                fillColor = accent.copy(alpha = .18f))
        }
    }
}
