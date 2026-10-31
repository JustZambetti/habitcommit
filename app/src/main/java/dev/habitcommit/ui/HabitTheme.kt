package dev.habitcommit.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val HabitColors = lightColorScheme(
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

// Material 3 shape scale, kept at the standard token values.
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
        colorScheme = HabitColors,
        typography = Typography(),
        shapes = HabitShapes,
        content = content,
    )
}
