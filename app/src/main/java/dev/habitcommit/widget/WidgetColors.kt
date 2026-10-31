package dev.habitcommit.widget

import dev.habitcommit.R

/** ARGB colors shared by the RemoteViews widget renderer and the app previews. */
internal object WidgetColors {
    const val Background = 0xFFFDFAF4.toInt()
    const val Text = 0xFF241F19.toInt()
    const val Muted = 0x8A241F19.toInt()
    const val Faint = 0x55241F19.toInt()
    const val Green = 0xFF2F6B4F.toInt()
    const val GreenDeep = 0xFF24543D.toInt()
    const val Terracotta = 0xFF9A4E28.toInt()
    const val White = 0xFFFFFFFF.toInt()
    const val PrimaryContainer = 0xFFDCEBDF.toInt()
    const val OnPrimaryContainer = 0xFF123322.toInt()
    const val OnSurfaceVariant = 0xFF6E6255.toInt()
    const val Outline = 0xFFC9BFAF.toInt()

    /** Drawable for each heat level; the index is the level 0..4. */
    val heatDrawables = intArrayOf(
        R.drawable.widget_heat_0,
        R.drawable.widget_heat_1,
        R.drawable.widget_heat_2,
        R.drawable.widget_heat_3,
        R.drawable.widget_heat_4,
    )
}
