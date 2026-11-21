package com.example.findme.data.model

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Work
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.example.findme.ui.theme.AccentFree
import com.example.findme.ui.theme.AccentUniversity
import com.example.findme.ui.theme.AccentWork

enum class ActivityCategory(
    val key: String,
    val label: String,
    val icon: ImageVector
) {
    UNIVERSITY(
        key = "university",
        label = "Universidad",
        icon = Icons.Default.School
    ),
    WORK(
        key = "work",
        label = "Trabajo",
        icon = Icons.Default.Work
    ),
    FREE(
        key = "free",
        label = "Tiempo Libre",
        icon = Icons.Default.Restaurant
    );

    /** Follows the active theme, so it is read from composition. */
    val accentColor: Color
        @Composable @ReadOnlyComposable get() = when (this) {
            UNIVERSITY -> AccentUniversity
            WORK -> AccentWork
            FREE -> AccentFree
        }

    companion object {
        fun fromKey(key: String?, title: String = ""): ActivityCategory {
            val keyClean = key?.lowercase()?.trim().orEmpty()
            val titleClean = title.lowercase().trim()

            return when {
                keyClean == "work" || titleClean.contains("trabajo") || titleClean.contains("oficina") -> WORK
                keyClean == "free" || keyClean == "meal" || titleClean.contains("libre") || titleClean.contains("almuerzo") || titleClean.contains("comida") || titleClean.contains("gimnasio") -> FREE
                else -> UNIVERSITY
            }
        }
    }
}
