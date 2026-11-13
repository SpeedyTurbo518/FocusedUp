package com.focusedup.shared.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** The Focused Up power-symbol mark, centered in the current draw area. */
fun DrawScope.drawFocusSymbol(color: Color, strokeWidth: Float) {
    val r = size.minDimension * 0.27f
    val c = center + Offset(0f, r * 0.15f)
    drawArc(
        color = color,
        startAngle = -50f,
        sweepAngle = 280f,
        useCenter = false,
        topLeft = Offset(c.x - r, c.y - r),
        size = Size(r * 2, r * 2),
        style = Stroke(strokeWidth, cap = StrokeCap.Round),
    )
    drawLine(color, Offset(c.x, c.y - r * 1.3f), Offset(c.x, c.y - r * 0.2f), strokeWidth, StrokeCap.Round)
}

/** Background brush and symbol color for one icon color + style. */
private data class IconLook(val background: Brush, val symbol: Color, val border: Color)

private fun iconLook(colorIndex: Int, style: Int, w: Float, h: Float): IconLook {
    val c = ICON_COLORS.getOrElse(colorIndex) { ICON_COLORS[0] }.color
    return when (style) {
        1 -> IconLook(
            Brush.linearGradient(listOf(lerp(c, Color.White, 0.12f), lerp(c, Color.Black, 0.12f)), Offset.Zero, Offset(w, h)),
            Color.White, Color.White.copy(alpha = 0.22f),
        )
        2 -> IconLook(
            Brush.linearGradient(listOf(Color(0xFF15171C), Color(0xFF050608)), Offset.Zero, Offset(w, h)),
            lerp(c, Color.White, 0.15f), c.copy(alpha = 0.6f),
        )
        else -> IconLook(
            Brush.linearGradient(listOf(c, lerp(c, Color(0xFF02040A), 0.75f), Color(0xFF02040A)), Offset.Zero, Offset(w, h)),
            Color.White, Color.White.copy(alpha = 0.18f),
        )
    }
}

/** App icon tile in the chosen color and style (or an explicit one, for the picker). */
@Composable
fun FocusLogo(size: Dp, modifier: Modifier = Modifier, colorIndex: Int? = null, style: Int? = null) {
    val appearance by LocalEngine.current.appearance.value.collectAsState()
    val ci = colorIndex ?: appearance.iconColor
    val st = style ?: appearance.iconStyle
    val shape = RoundedCornerShape(size * 0.27f)
    Box(modifier.size(size).clip(shape)) {
        Canvas(Modifier.fillMaxSize()) {
            val look = iconLook(ci, st, this.size.width, this.size.height)
            drawRect(look.background)
            drawFocusSymbol(look.symbol, this.size.minDimension * 0.065f)
        }
        val look = iconLook(ci, st, 1f, 1f)
        Box(Modifier.fillMaxSize().border(1.dp, look.border, shape).background(Color.Transparent))
    }
}

/** Same logo as a Painter, for desktop window, taskbar and tray icons. */
class FocusLogoPainter(private val colorIndex: Int = 0, private val style: Int = 0) : Painter() {
    override val intrinsicSize: Size = Size(256f, 256f)
    override fun DrawScope.onDraw() {
        val look = iconLook(colorIndex, style, size.width, size.height)
        drawRoundRect(look.background, cornerRadius = CornerRadius(size.minDimension * 0.22f))
        if (style == 2) {
            val sw = size.minDimension * 0.03f
            drawRoundRect(
                look.border,
                topLeft = Offset(sw / 2, sw / 2),
                size = Size(size.width - sw, size.height - sw),
                cornerRadius = CornerRadius(size.minDimension * 0.22f),
                style = Stroke(sw),
            )
        }
        drawFocusSymbol(look.symbol, size.minDimension * 0.085f)
    }
}
