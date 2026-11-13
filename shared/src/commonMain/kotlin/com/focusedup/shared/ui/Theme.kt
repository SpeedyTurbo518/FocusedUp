package com.focusedup.shared.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import com.focusedup.shared.engine.FocusEngine
import com.focusedup.shared.model.Appearance
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

data class NamedColor(val name: String, val color: Color)

/** Accent colors for the app (Setup → App colors) and the profile. */
val ACCENTS = listOf(
    NamedColor("Blue", Color(0xFF3B82F6)),
    NamedColor("Red", Color(0xFFEF4444)),
    NamedColor("Green", Color(0xFF22C55E)),
    NamedColor("Purple", Color(0xFF8B5CF6)),
    NamedColor("Gold", Color(0xFFF59E0B)),
    NamedColor("Teal", Color(0xFF14B8A6)),
    NamedColor("Rose", Color(0xFFEC4899)),
)

/** Background tones. "Tinted" is a deep shade of the accent color. */
data class BaseTone(val name: String, val night: Color, val black: Color)

val BASES = listOf(
    BaseTone("Midnight", Color(0xFF07122A), Color(0xFF03050A)),
    BaseTone("Black", Color(0xFF0A0A0B), Color(0xFF000000)),
    BaseTone("Graphite", Color(0xFF24272D), Color(0xFF0F1013)),
    BaseTone("Tinted", Color.Unspecified, Color.Unspecified),
)

/** App icon colors and styles (Setup → App icon). Must match the Android launcher aliases. */
val ICON_COLORS = listOf(
    NamedColor("Blue", Color(0xFF2563EB)),
    NamedColor("Red", Color(0xFFDC2626)),
    NamedColor("Green", Color(0xFF16A34A)),
    NamedColor("Purple", Color(0xFF7C3AED)),
    NamedColor("Gold", Color(0xFFD97706)),
    NamedColor("Silver", Color(0xFF94A3B8)),
)
val ICON_STYLES = listOf("Glow", "Solid", "Outline")

/** Every color the UI uses, worked out from the user's Appearance settings. */
data class Palette(
    val accent: Color,
    val accentBright: Color,
    val accentDeep: Color,
    val night: Color,
    val black: Color,
    val dialog: Color,
    val t2: Float,
    val t3: Float,
    val hair: Float,
    val glass: Float,
)

object FColors {
    var palette by mutableStateOf(buildPalette(Appearance()))

    val Black get() = palette.black
    val Night get() = palette.night
    /** Accent color (named Blue for history; follows the chosen app color). */
    val Blue get() = palette.accent
    val BlueBright get() = palette.accentBright
    val BlueDeep get() = palette.accentDeep
    val Dialog get() = palette.dialog
    val Text = Color.White
    val TextSecondary get() = Color.White.copy(alpha = palette.t2)
    val TextTertiary get() = Color.White.copy(alpha = palette.t3)
    val Hairline get() = Color.White.copy(alpha = palette.hair)
    /** Multiplier for glass card brightness. */
    val Glass get() = palette.glass
}

fun buildPalette(a: Appearance): Palette {
    val c = a.contrast.coerceIn(0.6f, 1.4f)
    val sat = a.saturation.coerceIn(0f, 1.6f)
    val accent = adjust(ACCENTS.getOrElse(a.accent) { ACCENTS[0] }.color, a.hue, sat)
    val tone = BASES.getOrElse(a.base) { BASES[0] }
    var night: Color
    var black: Color
    if (tone.night == Color.Unspecified) {
        night = lerp(accent, Color.Black, 0.84f)
        black = lerp(accent, Color.Black, 0.96f)
    } else {
        night = adjust(tone.night, a.hue, sat)
        black = adjust(tone.black, a.hue, sat)
    }
    // More contrast pushes the backdrop darker; less lifts it.
    if (c > 1f) {
        night = lerp(night, Color.Black, (c - 1f) * 0.9f)
        black = lerp(black, Color.Black, (c - 1f) * 1.5f)
    } else {
        night = lerp(night, Color(0xFF2A2E38), (1f - c) * 0.6f)
        black = lerp(black, Color(0xFF181B22), (1f - c) * 0.6f)
    }
    return Palette(
        accent = accent,
        accentBright = lerp(accent, Color.White, 0.3f),
        accentDeep = lerp(accent, Color.Black, 0.35f),
        night = night,
        black = black,
        dialog = lerp(night, Color.White, 0.04f),
        t2 = (0.6f * c).coerceIn(0.4f, 0.92f),
        t3 = (0.36f * c).coerceIn(0.22f, 0.7f),
        hair = (0.10f * c).coerceIn(0.05f, 0.2f),
        glass = c,
    )
}

/** Rotate hue by [hueShift] degrees and scale saturation by [satScale]. */
fun adjust(color: Color, hueShift: Float, satScale: Float): Color {
    val r = color.red
    val g = color.green
    val b = color.blue
    val mx = max(r, max(g, b))
    val mn = min(r, min(g, b))
    val d = mx - mn
    var h = when {
        d == 0f -> 0f
        mx == r -> 60f * (((g - b) / d) % 6f)
        mx == g -> 60f * ((b - r) / d + 2f)
        else -> 60f * ((r - g) / d + 4f)
    }
    val s = if (mx == 0f) 0f else d / mx
    h = ((h + hueShift) % 360f + 360f) % 360f
    return hsv(h, (s * satScale).coerceIn(0f, 1f), mx, color.alpha)
}

private fun hsv(h: Float, s: Float, v: Float, alpha: Float): Color {
    val c = v * s
    val x = c * (1 - abs((h / 60f) % 2f - 1))
    val m = v - c
    val (r, g, b) = when {
        h < 60 -> Triple(c, x, 0f)
        h < 120 -> Triple(x, c, 0f)
        h < 180 -> Triple(0f, c, x)
        h < 240 -> Triple(0f, x, c)
        h < 300 -> Triple(x, 0f, c)
        else -> Triple(c, 0f, x)
    }
    return Color(r + m, g + m, b + m, alpha)
}

val LocalEngine = staticCompositionLocalOf<FocusEngine> { error("FocusEngine not provided") }

@Composable
fun FocusedUpTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = FColors.Blue,
            onPrimary = Color.White,
            primaryContainer = FColors.BlueDeep,
            onPrimaryContainer = Color.White,
            secondary = Color.White,
            background = FColors.Black,
            onBackground = Color.White,
            surface = FColors.Dialog,
            onSurface = Color.White,
            surfaceVariant = FColors.Night,
            onSurfaceVariant = FColors.TextSecondary,
        ),
        content = content,
    )
}
