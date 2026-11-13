package com.focusedup.shared.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.SelfImprovement
import androidx.compose.material.icons.rounded.DoNotDisturbOn
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.focusedup.shared.model.Phase
import com.focusedup.shared.platform.Capability
import com.focusedup.shared.ui.FColors
import com.focusedup.shared.ui.GlassCard
import com.focusedup.shared.ui.Hairline
import com.focusedup.shared.ui.IconTile
import com.focusedup.shared.ui.LocalEngine
import com.focusedup.shared.ui.PillButton
import com.focusedup.shared.ui.ScreenHeader
import com.focusedup.shared.ui.rememberNow
import com.focusedup.shared.util.formatClock
import com.focusedup.shared.util.longLabel

@Composable
fun FocusScreen(onToast: (String) -> Unit, onOpenSetup: () -> Unit, onOpenPrayer: () -> Unit) {
    val engine = LocalEngine.current
    val platform = engine.platform
    val s by engine.state.collectAsState()
    val now by rememberNow(platform::now)
    val warning = platform.setupWarning()

    val warmupRemaining = (s.warmupEndsAt - now).coerceAtLeast(0L)
    val progress = when (s.phase) {
        Phase.OFF -> 0f
        Phase.WARMUP -> 1f - warmupRemaining.toFloat() / s.warmupTotalMs.coerceAtLeast(1L)
        Phase.ACTIVE -> 1f
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(bottom = 120.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ScreenHeader("Focused Up", engine.tasks.today().longLabel())

        FocusOrb(
            phase = s.phase,
            progress = progress,
            onClick = {
                if (s.phase == Phase.OFF) onToast(engine.startFocus())
                else {
                    engine.stopFocus()
                    onToast("Focus Mode off. Sound restored.")
                }
            },
        )

        Spacer(Modifier.height(16.dp))
        val (big, small) = when (s.phase) {
            Phase.OFF -> "Off" to "Tap to go heads-down and silence distractions."
            Phase.WARMUP -> formatClock(warmupRemaining) to "Wrap up. Silence starts when this hits zero."
            Phase.ACTIVE -> formatClock(now - s.activeSince, roundUp = false) to "Focused. Distractions are on hold."
        }
        Text(big, color = Color.White, fontSize = 44.sp, fontWeight = FontWeight.Light, letterSpacing = (-1).sp)
        Spacer(Modifier.height(6.dp))
        Text(small, color = FColors.TextSecondary, fontSize = 15.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 24.dp))

        if (s.phase == Phase.WARMUP) {
            Spacer(Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PillButton("Start now") { engine.skipWarmup() }
                PillButton("Cancel", primary = false) { engine.stopFocus() }
            }
        }

        Spacer(Modifier.height(32.dp))
        val active = s.phase == Phase.ACTIVE
        GlassCard(Modifier.fillMaxWidth(), padding = 20.dp) {
            CapabilityRow(Icons.Rounded.DoNotDisturbOn, platform.dnd, active)
            Hairline()
            CapabilityRow(Icons.AutoMirrored.Rounded.VolumeOff, platform.silence, active)
        }

        Spacer(Modifier.height(20.dp))
        PrayerCard(onOpenPrayer)

        if (warning != null) {
            Spacer(Modifier.height(20.dp))
            GlassCard(Modifier.fillMaxWidth(), padding = 20.dp, onClick = onOpenSetup) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconTile(Icons.Rounded.Warning, active = true)
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Finish setup", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                        Text(warning, color = FColors.TextSecondary, fontSize = 13.sp)
                    }
                    Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = FColors.TextTertiary)
                }
            }
        }

        Spacer(Modifier.height(20.dp))
        TasksCard(onToast)
    }
}

@Composable
private fun CapabilityRow(icon: ImageVector, cap: Capability, focusOn: Boolean) {
    val active = focusOn && cap.supported
    Row(Modifier.fillMaxWidth().padding(vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        IconTile(icon, active)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(cap.title, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Medium)
            Text(if (active) cap.onText else cap.offText, color = FColors.TextSecondary, fontSize = 13.sp)
        }
        Box(
            Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(if (active) FColors.BlueBright else Color.White.copy(alpha = 0.15f))
        )
    }
}

@Composable
private fun FocusOrb(phase: Phase, progress: Float, onClick: () -> Unit) {
    val pulseTransition = rememberInfiniteTransition(label = "orb")
    val pulse by pulseTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(tween(1800, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "pulse",
    )
    val glow by animateFloatAsState(
        when (phase) { Phase.OFF -> 0.18f; Phase.WARMUP -> 0.55f; Phase.ACTIVE -> 0.95f },
        tween(600), label = "glow",
    )
    val ringColor by animateColorAsState(
        if (phase == Phase.OFF) Color.White.copy(alpha = 0.25f) else FColors.BlueBright, label = "ring",
    )
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val pressScale by animateFloatAsState(if (pressed) 0.94f else 1f, label = "press")

    Box(Modifier.size(250.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val r = size.minDimension / 2f
            val g = if (phase == Phase.OFF) glow else glow * pulse
            drawCircle(Brush.radialGradient(listOf(FColors.Blue.copy(alpha = 0.6f * g), Color.Transparent), center, r), r)
            val stroke = 5.dp.toPx()
            val ringR = r * 0.76f
            drawCircle(Color.White.copy(alpha = 0.08f), ringR, style = Stroke(stroke))
            if (progress > 0f) {
                drawArc(
                    color = ringColor,
                    startAngle = -90f,
                    sweepAngle = 360f * progress.coerceIn(0f, 1f),
                    useCenter = false,
                    topLeft = Offset(center.x - ringR, center.y - ringR),
                    size = Size(ringR * 2, ringR * 2),
                    style = Stroke(stroke, cap = StrokeCap.Round),
                )
            }
        }
        Box(
            Modifier
                .size(160.dp)
                .scale(pressScale)
                .clip(CircleShape)
                .background(
                    Brush.verticalGradient(
                        if (phase == Phase.ACTIVE) listOf(FColors.BlueBright, FColors.BlueDeep)
                        else listOf(Color.White.copy(alpha = 0.15f), Color.White.copy(alpha = 0.04f))
                    )
                )
                .border(1.dp, Color.White.copy(alpha = 0.25f), CircleShape)
                .clickable(interactionSource = interaction, indication = null, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Rounded.PowerSettingsNew,
                contentDescription = if (phase == Phase.OFF) "Turn Focus Mode on" else "Turn Focus Mode off",
                tint = if (phase == Phase.OFF) Color.White.copy(alpha = 0.85f) else Color.White,
                modifier = Modifier.size(60.dp),
            )
        }
    }
}

@Composable
private fun PrayerCard(onOpen: () -> Unit) {
    val engine = LocalEngine.current
    val p by engine.prayer.collectAsState()
    val done = p.doneOn == engine.tasks.today()
    GlassCard(Modifier.fillMaxWidth(), padding = 20.dp, onClick = onOpen) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconTile(if (done) Icons.Rounded.CheckCircle else Icons.Rounded.SelfImprovement, active = done)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(if (done) "Prayed today" else "Start with prayer", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                Text(
                    if (done) "${p.streak}-day streak. Keep it going." else "15 quiet minutes before you get to work.",
                    color = FColors.TextSecondary, fontSize = 13.sp,
                )
            }
            Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = FColors.TextTertiary)
        }
    }
}
