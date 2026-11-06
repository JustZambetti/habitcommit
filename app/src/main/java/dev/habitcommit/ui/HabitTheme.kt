package dev.habitcommit.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val HabitLightColors = lightColorScheme(
    primary = Color(0xFF2F6B4F),
    onPrimary = Color(0xFFF5FFF8),
    primaryContainer = Color(0xFFDCEBDF),
    onPrimaryContainer = Color(0xFF123322),
    secondary = Color(0xFF9A4E28),
    onSecondary = Color(0xFFFFF7EF),
    secondaryContainer = Color(0xFFF3E0D4),
    onSecondaryContainer = Color(0xFF4A2410),
    background = Color(0xFFF7F2E8),
    onBackground = Color(0xFF241F19),
    surface = Color(0xFFFDFAF4),
    onSurface = Color(0xFF241F19),
    surfaceVariant = Color(0xFFECE4D6),
    onSurfaceVariant = Color(0xFF6E6255),
    outline = Color(0xFFC9BFAF),
    outlineVariant = Color(0xFFE2D9CB),
)

private val HabitDarkColors = darkColorScheme(
    primary = Color(0xFF8FCBA8),
    onPrimary = Color(0xFF0C2A1B),
    primaryContainer = Color(0xFF234A35),
    onPrimaryContainer = Color(0xFFC6EBD0),
    secondary = Color(0xFFE5A87F),
    onSecondary = Color(0xFF3A1A08),
    secondaryContainer = Color(0xFF57301A),
    onSecondaryContainer = Color(0xFFF8DCC4),
    background = Color(0xFF151310),
    onBackground = Color(0xFFEDE5DA),
    surface = Color(0xFF1E1A16),
    onSurface = Color(0xFFEDE5DA),
    surfaceVariant = Color(0xFF2B2621),
    onSurfaceVariant = Color(0xFFC6B9A9),
    outline = Color(0xFF8A7E70),
    outlineVariant = Color(0xFF3D3630),
)

// Material 3 shape scale, kept at the standard token values.
/** 14-day heatmap palette, matching the widget drawables. */
internal val HeatColors = listOf(
    Color(0xFFEAE4D8),
    Color(0xFFC9DCCB),
    Color(0xFF9BC7A6),
    Color(0xFF66A57F),
    Color(0xFF2F6B4F),
)

private val HeatColorsDark = listOf(
    Color(0xFF2B2621),
    Color(0xFF2F4A3A),
    Color(0xFF3F6B50),
    Color(0xFF5E9473),
    Color(0xFF8FCBA8),
)

@Composable
internal fun heatColors(): List<Color> =
    if (isSystemInDarkTheme()) HeatColorsDark else HeatColors

private val HabitShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun HabitCommitTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) HabitDarkColors else HabitLightColors,
        typography = Typography(),
        shapes = HabitShapes,
        content = content,
    )
}
