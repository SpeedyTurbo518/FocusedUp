package com.focusedup.shared.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CloudSync
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material.icons.rounded.FilterList
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.focusedup.shared.data.JOB_TYPES
import com.focusedup.shared.data.NotionFilter
import com.focusedup.shared.data.NotionItem
import com.focusedup.shared.data.NotionStore
import com.focusedup.shared.ui.FColors
import com.focusedup.shared.ui.FSwitch
import com.focusedup.shared.ui.GlassCard
import com.focusedup.shared.ui.Hairline
import com.focusedup.shared.ui.IconTile
import com.focusedup.shared.ui.LocalEngine
import com.focusedup.shared.ui.PillButton
import com.focusedup.shared.ui.SectionLabel
import com.focusedup.shared.ui.fieldColors
import com.focusedup.shared.util.MONTHS
import com.focusedup.shared.util.formatTime
import com.focusedup.shared.util.toLocal
import com.focusedup.shared.util.weekdayName
import com.focusedup.shared.model.Reminder
import androidx.compose.material.icons.rounded.NotificationAdd
import androidx.compose.material.icons.rounded.NotificationsActive
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus

private val SHORT_MONTHS = MONTHS.map { it.take(3) }

/** Calendar tab: connect Notion and see upcoming shoots / projects that match the filter. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NotionPanel(today: LocalDate, onToast: (String) -> Unit) {
    val engine = LocalEngine.current
    val notion = engine.notion
    val s by notion.state.collectAsState()
    val clearedIds by notion.cleared.collectAsState()
    var busy by remember { mutableStateOf(false) }
    var showFilter by remember { mutableStateOf(false) }
    var showLogin by remember { mutableStateOf(false) }
    var showShootReminder by remember { mutableStateOf(false) }
    var showAll by remember { mutableStateOf(false) }

    // Refresh when the Calendar tab opens (if the last sync is older than 5 minutes).
    LaunchedEffect(s.connection != null) {
        if (s.connection != null && engine.platform.now() - s.lastSync > 5 * 60_000L) engine.syncNotion()
    }

    SectionLabel("Notion")
    val conn = s.connection
    if (conn == null) {
        GlassCard(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconTile(Icons.Rounded.CloudSync, active = true)
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text("Your Notion feed", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(2.dp))
                    Text("Shoots, reviews and finished content, read-only.", color = FColors.TextSecondary, fontSize = 14.sp)
                }
            }
            Spacer(Modifier.height(18.dp))
            if (engine.platform.supportsNotionLogin) {
                // Your own Notion login, on Notion's real page. Works for guests too.
                PillButton(if (busy) "Loading your Notion…" else "Sign in to Notion", modifier = Modifier.fillMaxWidth()) {
                    if (!busy) showLogin = true
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    "Sign in with your email (Notion sends a code to your Gmail). The app only reads.",
                    color = FColors.TextTertiary, fontSize = 12.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
                )
            } else if (NotionStore.loginConfigured) {
                // For people who are full members of their Notion workspace.
                PillButton(if (busy) "Waiting for Notion…" else "Continue with Notion", modifier = Modifier.fillMaxWidth()) {
                    if (busy) return@PillButton
                    busy = true
                    engine.launch {
                        val err = notion.signIn()
                        busy = false
                        if (err != null) onToast(err) else { onToast("Notion connected"); engine.syncNotion() }
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    "Sign in with Gmail, email or your Notion login.",
                    color = FColors.TextTertiary, fontSize = 12.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
                )
            } else {
                Text(
                    "Sign in to Notion on your phone to see your feed there. On this computer, paste a connection link if you have one.",
                    color = FColors.TextSecondary, fontSize = 14.sp, lineHeight = 20.sp,
                )
            }
            Text(
                "Hear the alert chime",
                color = FColors.BlueBright, fontSize = 13.sp, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().clickable { engine.platform.notionAlert("Test chime", "This is how Notion updates will sound.") }.padding(top = 14.dp),
            )
            if (engine.platform.pastesLinks) {
                Text(
                    "Paste a connection link",
                    color = FColors.BlueBright, fontSize = 13.sp, textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().clickable {
                        val clip = engine.platform.readClipboard().orEmpty().trim()
                        if (clip.startsWith("focusedup://notion") && engine.receiveLink(clip)) onToast("Connecting to Notion…")
                        else onToast("Copy the Notion connection link first")
                    }.padding(top = 14.dp),
                )
            }
        }
    } else {
        val items = remember(s, clearedIds, today) { notion.filtered(today) }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 4.dp, bottom = 10.dp)) {
            Column(Modifier.weight(1f)) {
                Text("Upcoming", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    listOf(
                        when {
                            conn.cookie != null -> "${conn.account.substringBefore(' ').ifBlank { "Your" }}'s Workspace · assigned to you"
                            conn.shared -> "Read-only · ${conn.workspace}"
                            else -> conn.workspace
                        },
                        filterSummary(s.filter),
                    )
                        .filter { it.isNotBlank() }.joinToString(" · "),
                    color = FColors.TextTertiary, fontSize = 12.sp,
                )
            }
            IconButton(onClick = { engine.launch { engine.syncNotion() } }) {
                Icon(Icons.Rounded.Refresh, "Refresh", tint = if (s.syncing) FColors.BlueBright else FColors.TextSecondary)
            }
            IconButton(onClick = { showFilter = true }) {
                Icon(Icons.Rounded.FilterList, "Filter", tint = FColors.TextSecondary)
            }
        }
        s.error?.let {
            Text(it, color = FColors.BlueBright, fontSize = 13.sp, modifier = Modifier.padding(start = 4.dp, bottom = 10.dp))
        }
        // Shoot actions: one tap to be reminded of every upcoming shoot, and to clear finished ones.
        val now = engine.platform.now()
        val done = items.filter { notion.isDone(it, now) }
        FlowRow(
            Modifier.fillMaxWidth().padding(bottom = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            ActionPill(
                if (s.filter.shootReminder < 0) Icons.Rounded.NotificationAdd else Icons.Rounded.NotificationsActive,
                if (s.filter.shootReminder < 0) "Remind me of shoots" else "Shoots: ${SHOOT_REMINDER_OPTIONS[s.filter.shootReminder]}",
                active = s.filter.shootReminder >= 0,
            ) { showShootReminder = true }
            if (done.isNotEmpty()) {
                ActionPill(Icons.Rounded.DoneAll, "Clear completed (${done.size})", active = false) {
                    notion.clear(done.map { it.id })
                    done.forEach { engine.cancelReminder(it.id) }
                    onToast("Cleared ${done.size} completed shoot${if (done.size == 1) "" else "s"}")
                }
            }
        }
        if (items.isEmpty()) {
            GlassCard(Modifier.fillMaxWidth()) {
                Text(
                    if (s.syncing) "Checking Notion…" else "Nothing matches your filter.",
                    color = FColors.TextSecondary, fontSize = 14.sp,
                )
            }
        } else {
            GlassCard(Modifier.fillMaxWidth(), padding = 6.dp) {
                val shown = if (showAll) items else items.take(6)
                shown.forEachIndexed { i, item ->
                    if (i > 0) Hairline(startPadding = 76.dp)
                    NotionRow(
                        item, s.filter,
                        done = notion.isDone(item, now),
                        onClear = { notion.clear(listOf(item.id)); engine.cancelReminder(item.id) },
                    ) { if (item.url.isNotBlank()) engine.platform.openUrl(item.url) }
                }
                if (items.size > 6) {
                    Text(
                        if (showAll) "Show less" else "Show all ${items.size}",
                        color = FColors.BlueBright, fontSize = 14.sp, textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().clickable { showAll = !showAll }.padding(14.dp),
                    )
                }
            }
        }
    }

    if (showLogin) {
        engine.platform.NotionLoginScreen { cookie ->
            showLogin = false
            if (cookie != null) {
                busy = true
                engine.launch {
                    val err = notion.signInWithSession(cookie)
                    busy = false
                    if (err != null) onToast(err) else { onToast("Notion connected"); engine.syncNotion() }
                }
            }
        }
    }
    if (showFilter) {
        FilterDialog(today, onDismiss = { showFilter = false }, onToast = onToast)
    }
    if (showShootReminder) {
        ShootReminderDialog(current = s.filter.shootReminder, onDismiss = { showShootReminder = false }) { option ->
            showShootReminder = false
            engine.setShootReminders(option)
            onToast(if (option < 0) "Shoot reminders off" else "You'll be reminded ${SHOOT_REMINDER_OPTIONS[option].lowercase()} every shoot")
        }
    }
}

private val SHOOT_REMINDER_OPTIONS = listOf("1 hour before", "Morning of", "Day before")

@Composable
private fun ActionPill(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, active: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(if (active) FColors.Blue.copy(alpha = 0.25f) else Color.White.copy(alpha = 0.07f))
            .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = if (active) FColors.BlueBright else Color.White, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1)
    }
}

/** Pick when to be reminded of every upcoming video shoot. */
@Composable
private fun ShootReminderDialog(current: Int, onDismiss: () -> Unit, onPick: (Int) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = FColors.Dialog,
        shape = RoundedCornerShape(28.dp),
        icon = { Icon(Icons.Rounded.NotificationAdd, null, tint = FColors.BlueBright) },
        title = { Text("Remind me of shoots", color = Color.White) },
        text = {
            Column {
                Text(
                    "Applies to every upcoming video shoot, including new ones added in Notion.",
                    color = FColors.TextSecondary, fontSize = 14.sp, lineHeight = 20.sp,
                )
                Spacer(Modifier.height(14.dp))
                SHOOT_REMINDER_OPTIONS.forEachIndexed { i, label ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable { onPick(i) }.padding(horizontal = 8.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(label, color = Color.White, fontSize = 16.sp, modifier = Modifier.weight(1f))
                        if (current == i) Icon(Icons.Rounded.Check, "Selected", tint = FColors.BlueBright, modifier = Modifier.size(20.dp))
                    }
                }
                if (current >= 0) {
                    Text(
                        "Turn off shoot reminders", color = FColors.TextTertiary, fontSize = 14.sp,
                        modifier = Modifier.fillMaxWidth().clickable { onPick(-1) }.padding(horizontal = 8.dp, vertical = 14.dp),
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close", color = FColors.TextSecondary) } },
    )
}

/** One shoot / project, showing only the details the filter asks for. */
@Composable
fun NotionRow(item: NotionItem, f: NotionFilter, done: Boolean = false, onClear: () -> Unit = {}, onClick: () -> Unit) {
    val reminders by LocalEngine.current.reminders.collectAsState()
    var showRemind by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 14.dp),
        verticalAlignment = Alignment.Top,
    ) {
        if (f.showShootDate) {
            Column(
                Modifier.width(48.dp).clip(RoundedCornerShape(14.dp)).background(FColors.Blue.copy(alpha = 0.18f)).padding(vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                val d = item.date
                Text(d?.let { SHORT_MONTHS[it.monthNumber - 1].uppercase() } ?: "—", color = FColors.BlueBright, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                Text(d?.dayOfMonth?.toString() ?: "", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(16.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(item.title, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Medium, maxLines = 2)
            // Only what matters to you: when you're recording, and where your content stands.
            val isRecording = item.kind != "content"
            val details = listOfNotNull(
                when {
                    item.kind == "recording" -> "Recording day"
                    isRecording -> null
                    else -> item.database.substringAfter("(", item.database).removeSuffix(")").takeIf { it.isNotBlank() && it != "Content Calendar" }
                },
                if (f.showArrival && item.arrival != null) "Time ${item.arrival}" else null,
                if (!isRecording) item.status else item.status?.takeIf { item.kind == "recording" },
                if (item.hasVideo && !isRecording) "Uploaded" else null,
            )
            if (details.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Text(details.joinToString("  ·  "), color = FColors.TextSecondary, fontSize = 13.sp, maxLines = 2)
            }
            // "Remind me" sits under the details so titles get the full width.
            val reminder = reminders.firstOrNull { it.id == item.id }
            Spacer(Modifier.height(10.dp))
            if (done) {
                // Finished shoot: mark it done and let you clear it from the list.
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.CheckCircle, null, tint = FColors.BlueBright, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Shoot done", color = FColors.BlueBright, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.width(12.dp))
                    Row(
                        Modifier
                            .clip(RoundedCornerShape(50))
                            .background(Color.White.copy(alpha = 0.07f))
                            .clickable(onClick = onClear)
                            .padding(horizontal = 12.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Rounded.Close, null, tint = Color.White, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Clear", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    }
                }
            } else Row(
                Modifier
                    .clip(RoundedCornerShape(50))
                    .background(if (reminder != null) FColors.Blue.copy(alpha = 0.25f) else Color.White.copy(alpha = 0.07f))
                    .clickable { showRemind = true }
                    .padding(horizontal = 12.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    if (reminder != null) Icons.Rounded.NotificationsActive else Icons.Rounded.NotificationAdd, null,
                    tint = if (reminder != null) FColors.BlueBright else Color.White, modifier = Modifier.size(15.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    reminder?.let { reminderLabel(it.at) } ?: "Remind me",
                    color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium,
                )
            }
        }
    }
    if (showRemind) RemindDialog(item) { showRemind = false }
}

private fun reminderLabel(at: Long): String {
    val t = toLocal(at)
    return "${t.date.weekdayName().take(3)} ${SHORT_MONTHS[t.monthNumber - 1]} ${t.dayOfMonth}, ${formatTime(t.time)}"
}

/** Choose when to be reminded about a shoot / project. */
@Composable
private fun RemindDialog(item: NotionItem, onDismiss: () -> Unit) {
    val engine = LocalEngine.current
    val now = engine.platform.now()
    val zone = TimeZone.currentSystemDefault()
    val today = toLocal(now).date
    fun at(date: LocalDate, h: Int, m: Int = 0) = LocalDateTime(date, LocalTime(h, m)).toInstant(zone).toEpochMilliseconds()
    val options = buildList {
        add("In 1 hour" to now + 60 * 60_000L)
        if (toLocal(now).hour < 18) add("This evening, 6:00 PM" to at(today, 18))
        add("Tomorrow, 9:00 AM" to at(today.plus(DatePeriod(days = 1)), 9))
        item.date?.let { d ->
            at(d.plus(DatePeriod(days = -1)), 18).takeIf { it > now }?.let { add("Day before, 6:00 PM" to it) }
            at(d, 8).takeIf { it > now }?.let { add("Morning of, 8:00 AM" to it) }
        }
    }.distinctBy { it.second }.sortedBy { it.second }
    val existing = engine.reminders.value.firstOrNull { it.id == item.id }
    val detail = listOfNotNull(
        item.date?.let { "${SHORT_MONTHS[it.monthNumber - 1]} ${it.dayOfMonth}" },
        item.arrival?.let { "arrive $it" },
        item.status,
    ).joinToString(" · ")

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = FColors.Dialog,
        shape = RoundedCornerShape(28.dp),
        icon = { Icon(Icons.Rounded.NotificationAdd, null, tint = FColors.BlueBright) },
        title = { Text("Remind me", color = Color.White) },
        text = {
            Column {
                Text(item.title, color = FColors.TextSecondary, fontSize = 15.sp)
                Spacer(Modifier.height(16.dp))
                options.forEach { (label, time) ->
                    Text(
                        label, color = Color.White, fontSize = 16.sp,
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable {
                            engine.remind(Reminder(item.id, "Reminder: ${item.title}", detail.ifBlank { "From your Notion feed" }, time))
                            onDismiss()
                        }.padding(horizontal = 8.dp, vertical = 14.dp),
                    )
                }
                if (existing != null) {
                    Text(
                        "Cancel reminder (${reminderLabel(existing.at)})", color = FColors.TextTertiary, fontSize = 14.sp,
                        modifier = Modifier.fillMaxWidth().clickable { engine.cancelReminder(item.id); onDismiss() }.padding(horizontal = 8.dp, vertical = 14.dp),
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close", color = FColors.TextSecondary) } },
    )
}

private fun filterSummary(f: NotionFilter): String {
    val range = when (f.range) {
        0 -> "This week"
        1 -> "This month"
        2 -> f.month?.let { m -> m.split("-").let { "${MONTHS[it[1].toInt() - 1]} ${it[0]}" } } ?: "A month"
        else -> "All upcoming"
    }
    val types = if (f.types.size == JOB_TYPES.size) "" else f.types.joinToString(", ")
    return listOf(range, types).filter { it.isNotBlank() }.joinToString(" · ")
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FilterDialog(today: LocalDate, onDismiss: () -> Unit, onToast: (String) -> Unit) {
    val engine = LocalEngine.current
    val notion = engine.notion
    val s by notion.state.collectAsState()
    var f by remember { mutableStateOf(s.filter) }
    var monthDate by remember {
        mutableStateOf(f.month?.split("-")?.let { LocalDate(it[0].toInt(), it[1].toInt(), 1) } ?: LocalDate(today.year, today.monthNumber, 1))
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = FColors.Dialog,
        shape = RoundedCornerShape(28.dp),
        title = { Text("What to show", color = Color.White) },
        text = {
            Column(Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState())) {
                FilterHeading("Timeframe")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("This week", "This month", "Pick a month", "All upcoming").forEachIndexed { i, label ->
                        Chip(label, f.range == i) { f = f.copy(range = i) }
                    }
                }
                if (f.range == 2) {
                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { monthDate = monthDate.plus(DatePeriod(months = -1)) }) {
                            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, "Previous month", tint = Color.White)
                        }
                        Text("${MONTHS[monthDate.monthNumber - 1]} ${monthDate.year}", color = Color.White, fontSize = 16.sp, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
                        IconButton(onClick = { monthDate = monthDate.plus(DatePeriod(months = 1)) }) {
                            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, "Next month", tint = Color.White)
                        }
                    }
                }

                FilterHeading("Show")
                ToggleRow("Date of recording", f.showShootDate) { f = f.copy(showShootDate = it) }
                ToggleRow("Time of recording", f.showArrival) { f = f.copy(showArrival = it) }

                FilterHeading("Notifications")
                ToggleRow("When my video or content is in review", f.notifyReview) { f = f.copy(notifyReview = it, notify = true) }
                ToggleRow("When a video upload completes", f.notifyUpload) { f = f.copy(notifyUpload = it, notify = true) }
                Text(
                    "Play test chime", color = FColors.BlueBright, fontSize = 14.sp,
                    modifier = Modifier.clickable { engine.platform.notionAlert("Test chime", "This is how Notion updates will sound.") }.padding(vertical = 10.dp),
                )
                Text(
                    "Showing only items assigned to you in ${s.connection?.account?.substringBefore(' ')?.let { "$it's" } ?: "your"} Workspace.",
                    color = FColors.TextTertiary, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp),
                )

                Spacer(Modifier.height(20.dp))
                Text(
                    "Sign out of Notion",
                    color = FColors.TextTertiary, fontSize = 14.sp,
                    modifier = Modifier.clickable { notion.disconnect(); engine.platform.clearNotionSession(); onDismiss(); onToast("Signed out of Notion") }.padding(vertical = 8.dp),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val month = "${monthDate.year}-${monthDate.monthNumber.toString().padStart(2, '0')}"
                notion.setFilter(f.copy(month = if (f.range == 2) month else f.month))
                onDismiss()
            }) { Text("Save", color = FColors.BlueBright, fontWeight = FontWeight.SemiBold) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = FColors.TextSecondary) } },
    )
}

@Composable
private fun FilterHeading(text: String) {
    Text(
        text.uppercase(), color = FColors.TextTertiary, fontSize = 12.sp, letterSpacing = 1.4.sp, fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(top = 22.dp, bottom = 10.dp),
    )
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = Color.White, fontSize = 15.sp, modifier = Modifier.weight(1f).padding(end = 12.dp))
        FSwitch(checked, onChange)
    }
}

@Composable
fun Chip(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) FColors.Blue else Color.White.copy(alpha = 0.06f))
            .border(1.dp, if (selected) Color.Transparent else Color.White.copy(alpha = 0.14f), RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (selected) {
            Icon(Icons.Rounded.Check, null, tint = Color.White, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
        }
        Text(label, color = if (selected) Color.White else FColors.TextSecondary, fontSize = 14.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
    }
}

@Composable
fun NotionDot(selected: Boolean) {
    Box(Modifier.size(4.dp).clip(CircleShape).background(if (selected) Color.White else Color(0xFFFBBF24)))
}
