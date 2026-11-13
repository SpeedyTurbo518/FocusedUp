package com.focusedup.shared.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimeInput
import androidx.compose.material3.TimePickerDefaults
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.focusedup.shared.model.CalEvent
import com.focusedup.shared.ui.Dot
import com.focusedup.shared.ui.FColors
import com.focusedup.shared.ui.GlassCard
import com.focusedup.shared.ui.Hairline
import com.focusedup.shared.ui.LocalEngine
import com.focusedup.shared.ui.PillButton
import com.focusedup.shared.ui.ScreenHeader
import com.focusedup.shared.ui.fieldColors
import com.focusedup.shared.util.formatMinutes
import com.focusedup.shared.util.formatTime
import com.focusedup.shared.util.longLabel
import com.focusedup.shared.util.monthDay
import com.focusedup.shared.util.monthYear
import com.focusedup.shared.util.toLocal
import com.focusedup.shared.util.weekdayName
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.minus
import kotlinx.datetime.plus

private fun LocalDate.firstOfMonth() = LocalDate(year, monthNumber, 1)
private fun LocalDate.daysInMonth() = firstOfMonth().plus(DatePeriod(months = 1)).minus(DatePeriod(days = 1)).dayOfMonth

@Composable
fun CalendarScreen(onToast: (String) -> Unit) {
    val engine = LocalEngine.current
    val events by engine.calendar.events.collectAsState()
    val tasks by engine.tasks.tasks.collectAsState()
    val today = engine.tasks.today()
    // Opens on the day you last looked at (back to today once that day has passed).
    val savedDay = engine.settings.ui("cal_day")?.let { runCatching { LocalDate.parse(it) }.getOrNull() }?.takeIf { it >= today }
    var month by remember { mutableStateOf((savedDay ?: today).firstOfMonth()) }
    var selected by remember { mutableStateOf(savedDay ?: today) }
    androidx.compose.runtime.LaunchedEffect(selected) { engine.settings.setUi("cal_day", selected.toString()) }
    var showAdd by remember { mutableStateOf(false) }
    val byDate = remember(events) { events.groupBy { it.date } }
    val doneByDate = remember(tasks) { tasks.filter { it.done && it.doneOn != null }.groupBy { it.doneOn!! } }
    val notionState by engine.notion.state.collectAsState()
    val clearedNotion by engine.notion.cleared.collectAsState()
    val notionByDate = remember(notionState, clearedNotion, today) { engine.notion.filtered(today).filter { it.date != null }.groupBy { it.date!! } }

    val weekStart = today.minus(DatePeriod(days = today.dayOfWeek.isoDayNumber % 7))
    val weekEnd = weekStart.plus(DatePeriod(days = 6))
    val weekMinutes = events.filter { it.isFocus && it.date >= weekStart && it.date <= weekEnd }.sumOf { it.focusMinutes }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(bottom = 120.dp),
    ) {
        ScreenHeader("Calendar", "${formatMinutes(weekMinutes)} focused this week")

        GlassCard(Modifier.fillMaxWidth(), padding = 16.dp) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { month = month.minus(DatePeriod(months = 1)) }) {
                    Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, "Previous month", tint = Color.White)
                }
                Text(
                    month.monthYear(),
                    color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f).clickable { month = today.firstOfMonth(); selected = today },
                )
                IconButton(onClick = { month = month.plus(DatePeriod(months = 1)) }) {
                    Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, "Next month", tint = Color.White)
                }
            }
            Spacer(Modifier.height(6.dp))
            Row {
                listOf("S", "M", "T", "W", "T", "F", "S").forEach {
                    Text(it, color = FColors.TextTertiary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
                }
            }
            Spacer(Modifier.height(4.dp))

            val offset = month.dayOfWeek.isoDayNumber % 7 // Sunday = 0
            val days = month.daysInMonth()
            val weeks = (offset + days + 6) / 7
            for (week in 0 until weeks) {
                Row {
                    for (col in 0..6) {
                        val dayNum = week * 7 + col - offset + 1
                        Box(Modifier.weight(1f).aspectRatio(1f), contentAlignment = Alignment.Center) {
                            if (dayNum in 1..days) {
                                val date = LocalDate(month.year, month.monthNumber, dayNum)
                                DayCell(
                                    day = dayNum,
                                    isSelected = date == selected,
                                    isToday = date == today,
                                    events = byDate[date].orEmpty(),
                                    hasDoneTasks = doneByDate[date].orEmpty().isNotEmpty(),
                                    hasNotion = notionByDate[date].orEmpty().isNotEmpty(),
                                    onClick = { selected = date },
                                )
                            }
                        }
                    }
                }
            }
        }

        NotionPanel(today, onToast)

        Spacer(Modifier.height(36.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(selected.weekdayName().uppercase(), color = FColors.TextTertiary, fontSize = 12.sp, letterSpacing = 1.4.sp, fontWeight = FontWeight.SemiBold)
                Text(selected.monthDay(), color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
            }
            Box(
                Modifier.size(46.dp).clip(CircleShape).background(FColors.Blue).clickable { showAdd = true },
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Rounded.Add, "Add event", tint = Color.White) }
        }
        Spacer(Modifier.height(16.dp))

        val dayEvents = byDate[selected].orEmpty().sortedBy { it.time ?: LocalTime(0, 0) }
        val dayDone = doneByDate[selected].orEmpty()
        val dayNotion = notionByDate[selected].orEmpty()
        dayNotion.forEach { item ->
            GlassCard(Modifier.fillMaxWidth(), padding = 6.dp) {
                NotionRow(item, notionState.filter) { if (item.url.isNotBlank()) engine.platform.openUrl(item.url) }
            }
            Spacer(Modifier.height(12.dp))
        }
        if (dayEvents.isEmpty() && dayDone.isEmpty() && dayNotion.isEmpty()) {
            GlassCard(Modifier.fillMaxWidth(), padding = 18.dp) {
                Text("Nothing planned.", color = FColors.TextSecondary, fontSize = 14.sp)
            }
        } else {
            val focused = dayEvents.sumOf { it.focusMinutes }
            if (focused > 0) {
                Text("${formatMinutes(focused)} of focus", color = FColors.BlueBright, fontSize = 13.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(start = 6.dp, bottom = 8.dp))
            }
            dayEvents.forEach { e ->
                EventRow(e) { engine.removeEvent(e.id) }
                Spacer(Modifier.height(12.dp))
            }
            if (dayDone.isNotEmpty()) {
                GlassCard(Modifier.fillMaxWidth(), padding = 16.dp) {
                    Text("Tasks completed · ${dayDone.size}", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(6.dp))
                    dayDone.forEachIndexed { i, task ->
                        if (i > 0) Hairline(startPadding = 38.dp)
                        Row(Modifier.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            RoundCheckbox(true)
                            Spacer(Modifier.width(12.dp))
                            Text(task.title, color = FColors.TextSecondary, fontSize = 15.sp, textDecoration = TextDecoration.LineThrough)
                        }
                    }
                }
            }
        }
    }

    if (showAdd) {
        AddEventDialog(
            date = selected,
            initialHour = toLocal(engine.platform.now()).hour,
            onDismiss = { showAdd = false },
            onSave = { title, time, notify ->
                engine.addEvent(selected, title, time, notify)
                if (notify >= 0) onToast("You'll get a notification for \"$title\"")
                showAdd = false
            },
        )
    }
}

@Composable
private fun DayCell(day: Int, isSelected: Boolean, isToday: Boolean, events: List<CalEvent>, hasDoneTasks: Boolean, hasNotion: Boolean, onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(3.dp)
            .clip(CircleShape)
            .background(if (isSelected) FColors.Blue else Color.Transparent)
            .then(if (isToday && !isSelected) Modifier.border(1.dp, Color.White.copy(alpha = 0.55f), CircleShape) else Modifier)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            "$day",
            color = if (isSelected) Color.White else Color.White.copy(alpha = 0.9f),
            fontSize = 15.sp,
            fontWeight = if (isSelected || isToday) FontWeight.SemiBold else FontWeight.Normal,
        )
        Row(Modifier.height(6.dp).padding(top = 2.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            if (events.any { it.isFocus }) Dot(if (isSelected) Color.White else FColors.BlueBright)
            if (events.any { !it.isFocus } || hasDoneTasks) Dot(Color.White.copy(alpha = 0.8f))
            if (hasNotion) NotionDot(isSelected)
        }
    }
}

@Composable
private fun EventRow(e: CalEvent, onDelete: () -> Unit) {
    GlassCard(Modifier.fillMaxWidth(), padding = 14.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.width(4.dp).height(38.dp).clip(RoundedCornerShape(2.dp))
                    .background(if (e.isFocus) FColors.BlueBright else Color.White.copy(alpha = 0.7f))
            )
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(e.title, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                val detail = listOfNotNull(
                    e.time?.let(::formatTime),
                    if (e.isFocus) "${formatMinutes(e.focusMinutes)} focus session" else null,
                ).joinToString(" · ").ifEmpty { "All day" }
                Text(detail, color = FColors.TextSecondary, fontSize = 13.sp)
            }
            if (e.notifyMin >= 0) {
                Icon(Icons.Rounded.NotificationsActive, "Notification set", tint = FColors.BlueBright, modifier = Modifier.size(18.dp))
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Rounded.Close, "Delete", tint = FColors.TextTertiary, modifier = Modifier.size(20.dp))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun AddEventDialog(date: LocalDate, initialHour: Int, onDismiss: () -> Unit, onSave: (String, LocalTime?, Int) -> Unit) {
    val engine = LocalEngine.current
    // Remembers your last choices for the next event.
    var notify by remember { mutableStateOf(engine.settings.eventNotify) }
    var title by remember { mutableStateOf("") }
    var withTime by remember { mutableStateOf(engine.settings.ui("event_with_time") == "true") }
    val timeState = rememberTimePickerState(initialHour = initialHour, initialMinute = 0, is24Hour = false)

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = FColors.Dialog,
        shape = RoundedCornerShape(28.dp),
        title = {
            Column {
                Text("New event", color = Color.White, fontWeight = FontWeight.SemiBold)
                Text(date.longLabel(), color = FColors.TextSecondary, fontSize = 14.sp)
            }
        },
        text = {
            Column {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Title") },
                    singleLine = true,
                    colors = fieldColors(),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(14.dp))
                if (withTime) {
                    TimeInput(
                        state = timeState,
                        colors = TimePickerDefaults.colors(
                            timeSelectorSelectedContainerColor = FColors.Blue,
                            timeSelectorUnselectedContainerColor = Color.White.copy(alpha = 0.08f),
                            timeSelectorSelectedContentColor = Color.White,
                            timeSelectorUnselectedContentColor = Color.White,
                            periodSelectorSelectedContainerColor = FColors.Blue,
                            periodSelectorUnselectedContainerColor = Color.Transparent,
                            periodSelectorSelectedContentColor = Color.White,
                            periodSelectorUnselectedContentColor = FColors.TextSecondary,
                            periodSelectorBorderColor = Color.White.copy(alpha = 0.2f),
                        ),
                    )
                    TextButton(onClick = { withTime = false }) { Text("Remove time", color = FColors.TextSecondary) }
                } else {
                    PillButton(text = "Add a time (optional)", primary = false, icon = Icons.Rounded.Schedule) { withTime = true }
                }
                Spacer(Modifier.height(16.dp))
                Text("NOTIFY ME", color = FColors.TextTertiary, fontSize = 12.sp, letterSpacing = 1.4.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(10.dp))
                val choices = if (withTime) listOf(-1 to "Off", 0 to "At start", 15 to "15 min before", 60 to "1 hr before")
                else listOf(-1 to "Off", 0 to "That morning")
                androidx.compose.foundation.layout.FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    choices.forEach { (v, label) ->
                        val sel = if (withTime) notify == v else (notify >= 0) == (v >= 0)
                        Chip(label, sel) { notify = v }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = title.isNotBlank(),
                onClick = {
                    engine.settings.setUi("event_with_time", withTime.toString())
                    onSave(title.trim(), if (withTime) LocalTime(timeState.hour, timeState.minute) else null, notify)
                },
            ) {
                Text("Add", color = if (title.isNotBlank()) FColors.BlueBright else FColors.TextTertiary, fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = FColors.TextSecondary) }
        },
    )
}
