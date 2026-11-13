package com.focusedup.shared.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Coffee
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Storefront
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.focusedup.shared.data.COUNTERS
import com.focusedup.shared.ui.FColors
import com.focusedup.shared.ui.GlassCard
import com.focusedup.shared.ui.Hairline
import com.focusedup.shared.ui.IconTile
import com.focusedup.shared.ui.LocalEngine
import com.focusedup.shared.ui.PillButton
import com.focusedup.shared.ui.ScreenHeader
import com.focusedup.shared.ui.SectionLabel
import com.focusedup.shared.ui.fieldColors
import com.focusedup.shared.ui.rememberNow
import com.focusedup.shared.util.formatClock
import com.focusedup.shared.util.formatTime
import com.focusedup.shared.util.toLocal

/** Keeps you on task during a retail / sales-floor shift. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OnTaskScreen(onToast: (String) -> Unit) {
    val engine = LocalEngine.current
    val store = engine.onTask
    val cfg by store.settings.collectAsState()
    val s by store.state.collectAsState()
    val now by rememberNow(engine.platform::now, 1000L)

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(bottom = 120.dp),
    ) {
        ScreenHeader("On Task", if (s.active) "On shift" else "Sales floor")

        if (!s.active) {
            // ---- Shift setup (your choices are remembered) ----
            GlassCard(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconTile(Icons.Rounded.Storefront, active = true)
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Start your shift", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                        Text("Nudges, breaks and a floor checklist, all on a timer.", color = FColors.TextSecondary, fontSize = 14.sp)
                    }
                }
                OptionRow("Shift length", listOf(4f to "4 hr", 6f to "6 hr", 8f to "8 hr", 10f to "10 hr"), cfg.hours) { v -> store.updateSettings { it.copy(hours = v) } }
                OptionRow("Check-in nudges", listOf(0 to "Off", 20 to "20 min", 30 to "30 min", 60 to "1 hr"), cfg.checkInMin) { v -> store.updateSettings { it.copy(checkInMin = v) } }
                OptionRow("Break reminder", listOf(0 to "Off", 120 to "Every 2 hr", 180 to "Every 3 hr"), cfg.breakEveryMin) { v -> store.updateSettings { it.copy(breakEveryMin = v) } }
                OptionRow("Lunch", listOf(0 to "Off", 30 to "30 min", 60 to "1 hr"), cfg.lunchMin) { v -> store.updateSettings { it.copy(lunchMin = v) } }
                Spacer(Modifier.height(22.dp))
                PillButton("Start shift", icon = Icons.Rounded.PlayArrow, modifier = Modifier.fillMaxWidth()) {
                    store.startShift()
                    onToast("Shift started. Notifications are set.")
                }
            }
        } else {
            // ---- Live shift ----
            val total = (s.endsAt - s.startedAt).coerceAtLeast(1L)
            val left = (s.endsAt - now).coerceAtLeast(0L)
            val onBreak = s.breakUntil > now
            Box(Modifier.fillMaxWidth().padding(vertical = 4.dp), contentAlignment = Alignment.Center) {
                ShiftRing(1f - left.toFloat() / total, onBreak)
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(if (onBreak) "ON BREAK" else "TIME LEFT", color = FColors.TextTertiary, fontSize = 12.sp, letterSpacing = 2.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        formatClock(if (onBreak) s.breakUntil - now else left),
                        color = Color.White, fontSize = 48.sp, fontWeight = FontWeight.Light,
                    )
                    Text("Ends at ${formatTime(toLocal(s.endsAt).time)}", color = FColors.TextSecondary, fontSize = 14.sp)
                    store.nextCheckInLabel()?.takeIf { !onBreak }?.let {
                        Text("Next check-in $it", color = FColors.BlueBright, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally)) {
                if (onBreak) PillButton("Back on floor", icon = Icons.Rounded.PlayArrow) { store.endBreak() }
                else PillButton("Take a break", icon = Icons.Rounded.Coffee) { store.startBreak(); onToast("Enjoy your break. I'll tell you when it's over.") }
                PillButton("End shift", primary = false, icon = Icons.Rounded.Stop) {
                    store.endShift()
                    onToast("Shift logged to your calendar")
                }
            }
        }

        // ---- Floor checklist ----
        SectionLabel("Floor checklist")
        GlassCard(Modifier.fillMaxWidth(), padding = 8.dp) {
            val doneCount = cfg.duties.count { it in s.done }
            if (s.active) {
                Text(
                    "$doneCount of ${cfg.duties.size} done this shift",
                    color = FColors.TextTertiary, fontSize = 12.sp, modifier = Modifier.padding(start = 12.dp, top = 8.dp, bottom = 4.dp),
                )
            }
            cfg.duties.forEachIndexed { i, duty ->
                if (i > 0) Hairline(startPadding = 52.dp)
                val done = duty in s.done
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable(enabled = s.active) { store.toggleDuty(duty) }
                        .padding(horizontal = 12.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RoundCheckbox(done)
                    Spacer(Modifier.width(14.dp))
                    Text(
                        duty, color = if (done) FColors.TextTertiary else Color.White, fontSize = 15.sp,
                        textDecoration = if (done) TextDecoration.LineThrough else null, modifier = Modifier.weight(1f),
                    )
                    if (!s.active) {
                        Icon(Icons.Rounded.Close, "Remove", tint = FColors.TextTertiary, modifier = Modifier.size(18.dp).clickable { store.removeDuty(duty) })
                    }
                }
            }
            var newDuty by remember { mutableStateOf("") }
            OutlinedTextField(
                value = newDuty, onValueChange = { newDuty = it },
                placeholder = { Text("Add your own duty") },
                singleLine = true, colors = fieldColors(), shape = RoundedCornerShape(14.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { store.addDuty(newDuty); newDuty = "" }),
                modifier = Modifier.fillMaxWidth().padding(8.dp),
            )
        }

        // ---- Scoreboard ----
        SectionLabel("Scoreboard")
        GlassCard(Modifier.fillMaxWidth(), padding = 16.dp) {
            COUNTERS.forEachIndexed { i, name ->
                if (i > 0) Hairline(startPadding = 0.dp)
                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(name, color = Color.White, fontSize = 15.sp, modifier = Modifier.weight(1f))
                    CountButton(Icons.Rounded.Remove, "Less") { store.count(name, -1) }
                    Text(
                        "${s.counts[name] ?: 0}", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center, modifier = Modifier.width(48.dp),
                    )
                    CountButton(Icons.Rounded.Add, "More") { store.count(name, 1) }
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                var sales by remember(s.sales) { mutableStateOf(if (s.sales > 0) s.sales.toString() else "") }
                var goal by remember(cfg.salesGoal) { mutableStateOf(if (cfg.salesGoal > 0) cfg.salesGoal.toString() else "") }
                OutlinedTextField(
                    value = sales, onValueChange = { v -> sales = v.filter(Char::isDigit).take(7); store.setSales(sales.toIntOrNull() ?: 0) },
                    label = { Text("Sales $") }, singleLine = true, colors = fieldColors(), shape = RoundedCornerShape(14.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = goal, onValueChange = { v -> goal = v.filter(Char::isDigit).take(7); store.updateSettings { it.copy(salesGoal = goal.toIntOrNull() ?: 0) } },
                    label = { Text("Goal $") }, singleLine = true, colors = fieldColors(), shape = RoundedCornerShape(14.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f),
                )
            }
            if (cfg.salesGoal > 0) {
                val p = (s.sales.toFloat() / cfg.salesGoal).coerceIn(0f, 1f)
                Spacer(Modifier.height(12.dp))
                Box(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.08f))) {
                    Box(Modifier.fillMaxWidth(p).height(8.dp).clip(RoundedCornerShape(50)).background(FColors.Blue))
                }
                Text(
                    if (p >= 1f) "Goal hit. Amazing work!" else "${(p * 100).toInt()}% of today's goal",
                    color = FColors.TextSecondary, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp),
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun <T> OptionRow(label: String, options: List<Pair<T, String>>, current: T, onPick: (T) -> Unit) {
    Spacer(Modifier.height(18.dp))
    Text(label, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Medium)
    Spacer(Modifier.height(8.dp))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { (v, text) -> Chip(text, v == current) { onPick(v) } }
    }
}

@Composable
private fun CountButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Box(
        Modifier.size(38.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.08f)).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, label, tint = Color.White, modifier = Modifier.size(18.dp)) }
}

@Composable
private fun ShiftRing(progress: Float, onBreak: Boolean) {
    val accent = if (onBreak) Color(0xFFFBBF24) else FColors.BlueBright
    Canvas(Modifier.size(250.dp)) {
        val stroke = 8.dp.toPx()
        val r = size.minDimension / 2f * 0.86f
        drawCircle(Color.White.copy(alpha = 0.08f), r, style = Stroke(stroke))
        drawArc(
            color = accent, startAngle = -90f, sweepAngle = 360f * progress.coerceIn(0f, 1f), useCenter = false,
            topLeft = Offset(center.x - r, center.y - r), size = Size(r * 2, r * 2), style = Stroke(stroke, cap = StrokeCap.Round),
        )
    }
}
