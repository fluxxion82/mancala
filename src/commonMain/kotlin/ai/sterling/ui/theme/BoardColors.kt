package ai.sterling.ui.theme

import androidx.compose.ui.graphics.Color

object BoardColors {
    val WoodLight = Color(0xFF8B5A2B)
    val WoodMid = Color(0xFF6B421F)
    val WoodDark = Color(0xFF5A3A1A)
    val WoodGrain = Color(0xFF3A2410)

    val PitDeep = Color(0xFF1F1208)
    val PitMid = Color(0xFF3A2410)
    val PitRim = Color(0xFF5A3A1A)

    val MarblePalette = listOf(
        Color(0xFFE0A050), // amber
        Color(0xFF118AB2), // cobalt
        Color(0xFF06D6A0), // jade
        Color(0xFFEF476F), // ruby
        Color(0xFF26A69A), // teal
        Color(0xFFF5F5F5), // milky
        Color(0xFF8E44AD), // plum
        Color(0xFFB87333), // copper
    )

    val TableFelt = Color(0xFF2A1F14)

    // Warm cream/parchment for legible labels on dark wood.
    val Parchment = Color(0xFFF5E6C8)
    val ParchmentDim = Color(0xFFD9C7A0)
    val GlowAmber = Color(0xFFFFD27F)
}

fun Color.lighten(factor: Float): Color = Color(
    red = red + (1f - red) * factor,
    green = green + (1f - green) * factor,
    blue = blue + (1f - blue) * factor,
    alpha = alpha,
)

fun Color.darken(factor: Float): Color = Color(
    red = red * (1f - factor),
    green = green * (1f - factor),
    blue = blue * (1f - factor),
    alpha = alpha,
)
