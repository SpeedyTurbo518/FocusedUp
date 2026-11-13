package com.focusedup.shared.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.focusedup.shared.model.PRESETS
import com.focusedup.shared.model.Preset
import com.focusedup.shared.ui.FColors
import com.focusedup.shared.ui.FSwitch
import com.focusedup.shared.ui.GlassCard
import com.focusedup.shared.ui.LocalEngine
import com.focusedup.shared.ui.PillButton
import com.focusedup.shared.ui.ScreenHeader
import com.focusedup.shared.ui.SectionLabel
import com.focusedup.shared.ui.fieldColors
import com.focusedup.shared.ui.rememberNow
import com.focusedup.shared.util.formatClock
import com.focusedup.shared.util.formatMinutes
import com.focusedup.shared.util.formatTime
import com.focusedup.shared.util.toLocal

@Composable
fun TimerScreen(onToast: (String) -> Unit) {
    val engine = LocalEngine.current
    val s by engine.state.collectAsState()
    val now by rememberNow(engine.platform::now)
    var controlsFocus by remember { mutableStateOf(engine.settings.timerControlsFocus) }
    val t = s.timer
    val preset = t.preset

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(bottom = 120.dp),
    ) {
        ScreenHeader("Timer", if (preset != null) "In session" else "Choose a session")

        if (preset != null) {
            val total = preset.minutes * 60_000L
            val remaining = if (t.running) (t.endsAt - now).coerceAtLeast(0L) else t.pausedRemaining
            val endsText = formatTime(toLocal(t.endsAt).time)

            Box(Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                TimerRing(progress = 1f - remaining.toFloat() / total)
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(preset.name.uppercase(), color = FColors.TextTertiary, fontSize = 12.sp, letterSpacing = 2.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(6.dp))
                    Text(formatClock(remaining), color = Color.White, fontSize = 54.sp, fontWeight = FontWeight.Light, letterSpacing = (-1).sp)
                    Spacer(Modifier.height(4.dp))
                    Text(if (t.running) "Ends at $endsText" else "Paused", color = FColors.TextSecondary, fontSize = 15.sp)
                }
            }
            Spacer(Modifier.height(20.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally)) {
                if (t.running) PillButton("Pause", icon = Icons.Rounded.Pause) { engine.pauseTimer() }
                else PillButton("Resume", icon = Icons.Rounded.PlayArrow) { engine.resumeTimer() }
                PillButton("End", primary = false, icon = Icons.Rounded.Stop) {
                    engine.cancelTimer()
                    onToast("Session ended")
                }
            }
        } else {
            PRESETS.forEachIndexed { index, p ->
                PresetCard(p) { engine.startTimer(index)?.let(onToast) }
                Spacer(Modifier.height(16.dp))
            }
            SectionLabel("Custom time")
            CustomTimerCard(engine.settings.customMinutes) { minutes ->
                engine.startCustomTimer(minutes)?.let(onToast)
            }
        }

        Spacer(Modifier.height(16.dp))
        GlassCard(Modifier.fillMaxWidth(), padding = 20.dp) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f).padding(end = 12.dp)) {
                    Text("Focus Mode with timer", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "Starting a session also turns on Focus Mode. It switches off when time's up.",
                        color = FColors.TextSecondary, fontSize = 13.sp,
                    )
                }
                FSwitch(controlsFocus) {
                    controlsFocus = it
                    engine.settings.timerControlsFocus = it
                }
            }
        }
    }
}

@Composable
private fun PresetCard(preset: Preset, onStart: () -> Unit) {
    GlassCard(Modifier.fillMaxWidth(), onClick = onStart) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(preset.code, color = FColors.BlueBright, fontSize = 40.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(preset.name, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                Text(preset.tagline, color = FColors.TextSecondary, fontSize = 13.sp)
                Spacer(Modifier.height(6.dp))
                Text(
                    formatMinutes(preset.minutes),
                    color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(FColors.Blue.copy(alpha = 0.22f))
                        .padding(horizontal = 10.dp, vertical = 3.dp),
                )
            }
            Box(
                Modifier.size(52.dp).clip(CircleShape).background(FColors.Blue),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.PlayArrow, contentDescription = "Start ${preset.name}", tint = Color.White, modifier = Modifier.size(28.dp))
            }
        }
    }
}

/** Type hours and minutes (or nudge by 5 minutes), then start. */
@Composable
private fun CustomTimerCard(initialMinutes: Int, onStart: (Int) -> Unit) {
    var hours by remember { mutableStateOf((initialMinutes / 60).toString()) }
    var mins by remember { mutableStateOf((initialMinutes % 60).toString()) }
    val total = (hours.toIntOrNull() ?: 0) * 60 + (mins.toIntOrNull() ?: 0)
    val valid = total in 1..600
    val engine = LocalEngine.current
    // Remember what you typed, so it's still there next time you open the app.
    androidx.compose.runtime.LaunchedEffect(total) { if (valid) engine.settings.customMinutes = total }
    fun setTotal(m: Int) {
        val v = m.coerceIn(5, 600)
        hours = (v / 60).toString()
        mins = (v % 60).toString()
    }

    GlassCard(Modifier.fillMaxWidth(), padding = 20.dp) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            RoundIcon(Icons.Rounded.Remove, "5 minutes less") { setTotal(total - 5) }
            NumberField(hours, "Hours", Modifier.weight(1f)) { hours = it }
            Text(":", color = FColors.TextSecondary, fontSize = 26.sp)
            NumberField(mins, "Mins", Modifier.weight(1f)) { mins = it }
            RoundIcon(Icons.Rounded.Add, "5 minutes more") { setTotal(total + 5) }
        }
        Spacer(Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (valid) formatMinutes(total) else "Pick 1 minute to 10 hours",
                color = if (valid) FColors.TextSecondary else FColors.BlueBright, fontSize = 14.sp,
                modifier = Modifier.weight(1f),
            )
            PillButton("Start", icon = Icons.Rounded.PlayArrow) { if (valid) onStart(total) }
        }
    }
}

@Composable
private fun NumberField(value: String, label: String, modifier: Modifier, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = { v -> onChange(v.filter { it.isDigit() }.take(3)) },
        label = { Text(label) },
        singleLine = true,
        textStyle = TextStyle(color = Color.White, fontSize = 22.sp, textAlign = TextAlign.Center),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
        colors = fieldColors(),
        shape = RoundedCornerShape(14.dp),
        modifier = modifier,
    )
}

@Composable
private fun RoundIcon(icon: ImageVector, label: String, onClick: () -> Unit) {
    Box(
        Modifier.size(40.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.08f)).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = label, tint = Color.White, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun TimerRing(progress: Float) {
    Canvas(Modifier.size(290.dp)) {
        val r = size.minDimension / 2f
        drawCircle(Brush.radialGradient(listOf(FColors.Blue.copy(alpha = 0.28f), Color.Transparent), center, r), r)
        val stroke = 8.dp.toPx()
        val ringR = r * 0.82f
        drawCircle(Color.White.copy(alpha = 0.08f), ringR, style = Stroke(stroke))
        drawArc(
            color = FColors.BlueBright,
            startAngle = -90f,
            sweepAngle = 360f * progress.coerceIn(0f, 1f),
            useCenter = false,
            topLeft = Offset(center.x - ringR, center.y - ringR),
            size = Size(ringR * 2, ringR * 2),
            style = Stroke(stroke, cap = StrokeCap.Round),
        )
    }
}
