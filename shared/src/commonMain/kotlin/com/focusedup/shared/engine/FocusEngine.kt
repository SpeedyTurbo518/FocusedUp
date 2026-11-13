package com.focusedup.shared.engine

import com.focusedup.shared.data.AppJson
import com.focusedup.shared.data.AppSettings
import com.focusedup.shared.data.AppearanceStore
import com.focusedup.shared.data.CalendarStore
import com.focusedup.shared.data.FriendsStore
import com.focusedup.shared.data.NotionStore
import com.focusedup.shared.data.OnTaskStore
import com.focusedup.shared.data.ProfileStore
import com.focusedup.shared.data.ShareLinks
import com.focusedup.shared.data.SharedProfile
import com.focusedup.shared.data.TaskStore
import com.focusedup.shared.model.CUSTOM_PRESET
import com.focusedup.shared.model.FocusState
import com.focusedup.shared.model.PRAYER_MINUTES
import com.focusedup.shared.model.PRAYER_MS
import com.focusedup.shared.model.PRAYER_SNOOZE_MS
import com.focusedup.shared.model.PrayerState
import com.focusedup.shared.model.Reminder
import com.focusedup.shared.model.PRESETS
import com.focusedup.shared.model.Phase
import com.focusedup.shared.model.TimerState
import com.focusedup.shared.platform.FocusPlatform
import com.focusedup.shared.util.formatMinutes
import com.focusedup.shared.util.toLocal
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalTime
import kotlinx.datetime.minus
import kotlinx.datetime.toInstant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString

/**
 * Single source of truth for Focus Mode, the session timer, tasks and calendar.
 * Shared by Android, Windows, Mac and iPhone. All device work goes through [platform].
 */
class FocusEngine(val platform: FocusPlatform) {
    companion object {
        const val ACTION_WARMUP_DONE = "com.focusedup.app.WARMUP_DONE"
        const val ACTION_TIMER_DONE = "com.focusedup.app.TIMER_DONE"
        const val ACTION_STOP_FOCUS = "com.focusedup.app.STOP_FOCUS"
        const val ACTION_SKIP_WARMUP = "com.focusedup.app.SKIP_WARMUP"
        const val ACTION_END_TIMER = "com.focusedup.app.END_TIMER"
        private const val KEEP_FOCUS_EVERY_MS = 20_000L
        private const val SHOOT_TAG = "Shoot"
    }

    val settings = AppSettings(platform.store)
    val tasks = TaskStore(platform.store, platform::now)
    val calendar = CalendarStore(platform.store, platform::now)
    val appearance = AppearanceStore(platform.store)
    val profile = ProfileStore(platform.store, platform)
    val notion = NotionStore(platform.store, platform)
    val friends = FriendsStore(platform.store)

    /** "On Task" tab: retail shift timer, check-ins, breaks, floor checklist and scoreboard. */
    val onTask = OnTaskStore(
        platform.store, platform::now,
        remind = { remind(it) },
        cancelReminder = { cancelReminder(it) },
        logShift = { title ->
            val t = toLocal(platform.now())
            calendar.add(t.date, title, LocalTime(t.hour, t.minute))
        },
    )

    /** A profile someone shared with us, waiting for the user to save or dismiss it. */
    private val _incoming = MutableStateFlow<SharedProfile?>(null)
    val incomingProfile: StateFlow<SharedProfile?> = _incoming.asStateFlow()

    private val _state = MutableStateFlow(load())
    val state: StateFlow<FocusState> = _state.asStateFlow()

    private val _prayer = MutableStateFlow(loadPrayer())
    val prayer: StateFlow<PrayerState> = _prayer.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var lastKeepFocus = 0L

    init {
        scope.launch {
            while (isActive) {
                tick()
                delay(1000)
            }
        }
        // Check Notion for new shoots/projects shortly after launch, then every 30 minutes.
        scope.launch {
            delay(4000)
            while (isActive) {
                syncNotion()
                delay(30 * 60_000L)
            }
        }
    }

    // ---------------- Notion ----------------

    fun launch(block: suspend () -> Unit) {
        scope.launch { block() }
    }

    suspend fun syncNotion() {
        if (notion.state.value.connection == null) return
        val changes = notion.sync(tasks.today())
        // Soft chime when something is completed, goes to review, or is reassigned.
        if (changes.isNotEmpty() && notion.state.value.filter.notify) {
            val first = changes.first()
            if (changes.size == 1) platform.notionAlert(first.headline, first.detail)
            else platform.notionAlert("${changes.size} Notion updates", changes.take(3).joinToString(" · ") { it.headline })
        }
        remindUpcomingShoots()
    }

    /**
     * "Remind me of upcoming shoots": with [option] 0 = one hour before, 1 = morning of, 2 = day before,
     * -1 = off. Applies to every upcoming shoot now and to new ones as they appear in Notion.
     */
    fun setShootReminders(option: Int) {
        val old = notion.state.value.filter.shootReminder
        notion.setFilter(notion.state.value.filter.copy(shootReminder = option))
        // Replace the automatic shoot reminders (ones you set by hand on other items stay).
        if (old != option) {
            val shootIds = notion.state.value.items.filter { it.isShoot }.map { it.id }.toSet()
            _reminders.value.filter { it.id in shootIds && it.text.startsWith(SHOOT_TAG) }.forEach { cancelReminder(it.id) }
        }
        remindUpcomingShoots()
    }

    private fun remindUpcomingShoots() {
        val option = notion.state.value.filter.shootReminder
        if (option < 0) return
        val now = platform.now()
        val existing = _reminders.value.map { it.id }.toSet()
        notion.filtered(tasks.today())
            .filter { it.isShoot && it.id !in existing && !notion.isDone(it, now) }
            .forEach { item ->
                val at = notion.shootReminderTime(item, option, now) ?: return@forEach
                val whenText = listOfNotNull(item.date?.let { "${it.monthNumber}/${it.dayOfMonth}" }, item.arrival).joinToString(" at ")
                remind(Reminder(item.id, "Video shoot: ${item.title}", "$SHOOT_TAG $whenText".trim(), at))
            }
    }

    // ---------------- Calendar events with push notifications ----------------

    /** Adds an event and schedules its push notification ([notifyMin] minutes before; all-day events at 9 AM). */
    fun addEvent(date: kotlinx.datetime.LocalDate, title: String, time: LocalTime?, notifyMin: Int) {
        settings.eventNotify = notifyMin
        val e = calendar.add(date, title, time, notifyMin = notifyMin)
        if (notifyMin < 0) return
        val zone = kotlinx.datetime.TimeZone.currentSystemDefault()
        val start = kotlinx.datetime.LocalDateTime(date, time ?: LocalTime(9, 0)).toInstant(zone).toEpochMilliseconds()
        val at = if (time == null) start else start - notifyMin * 60_000L
        if (at <= platform.now()) return
        val text = when {
            time == null -> "Today"
            notifyMin == 0 -> "Starting now"
            notifyMin >= 60 -> "Starts in ${notifyMin / 60} hour${if (notifyMin >= 120) "s" else ""}"
            else -> "Starts in $notifyMin minutes"
        }
        remind(Reminder("event-${e.id}", title, text, at))
    }

    fun removeEvent(id: Long) {
        cancelReminder("event-$id")
        calendar.remove(id)
    }

    // ---------------- Remind me ----------------

    private val _reminders = MutableStateFlow(
        platform.store.get("reminders")?.takeIf { it.isNotEmpty() }
            ?.let { runCatching { AppJson.decodeFromString<List<Reminder>>(it) }.getOrNull() } ?: emptyList()
    )
    val reminders: StateFlow<List<Reminder>> = _reminders.asStateFlow()

    private fun saveReminders(l: List<Reminder>) {
        _reminders.value = l
        platform.store.put("reminders", AppJson.encodeToString(l))
    }

    fun remind(r: Reminder) {
        platform.cancelReminder(r.id)
        saveReminders(_reminders.value.filterNot { it.id == r.id } + r)
        platform.scheduleReminder(r)
    }

    fun cancelReminder(id: String) {
        platform.cancelReminder(id)
        saveReminders(_reminders.value.filterNot { it.id == id })
    }

    /** Android's alarm already showed it; just drop it from the list. */
    fun reminderFired(id: String) = saveReminders(_reminders.value.filterNot { it.id == id })

    private fun fireDueReminders(now: Long) {
        if (platform.remindersInBackground) {
            // Clean up ones the alarm already handled while the app was closed.
            if (_reminders.value.any { it.at < now - 60_000 }) saveReminders(_reminders.value.filter { it.at >= now - 60_000 })
            return
        }
        val due = _reminders.value.filter { it.at <= now }
        if (due.isEmpty()) return
        saveReminders(_reminders.value - due.toSet())
        due.forEach { platform.notionAlert(it.title, it.text) }
    }

    // ---------------- Profile sharing ----------------

    fun myShareLink(): String {
        val p = profile.value.value
        val minutes = calendar.events.value.sumOf { it.focusMinutes }
        return ShareLinks.build(SharedProfile(p.name.ifBlank { "A Focused Up friend" }, p.tagline, p.color, _prayer.value.streak, minutes))
    }

    /** Called with a link the app was opened with (or one pasted on desktop). */
    fun receiveLink(link: String): Boolean {
        // One-time Notion connection link from the workspace owner: focusedup://notion#t=...
        if (link.startsWith("focusedup://notion")) {
            // Never let a link silently replace an existing sign-in (it could point at someone else's Notion).
            if (notion.state.value.connection != null) return false
            val token = link.substringAfter("t=", "").substringBefore('&')
            if (token.isBlank()) return false
            launch {
                val err = notion.connectWithToken(token, shared = true)
                if (err == null) syncNotion() else platform.notionAlert("Notion didn't connect", err)
            }
            return true
        }
        val p = ShareLinks.parse(link) ?: return false
        _incoming.value = p
        return true
    }

    fun answerIncoming(save: Boolean) {
        _incoming.value?.let { if (save) friends.add(it) }
        _incoming.value = null
    }

    private fun load(): FocusState = platform.store.get("focus_state")
        ?.let { runCatching { AppJson.decodeFromString<FocusState>(it) }.getOrNull() }
        ?: FocusState()

    private fun commit(newState: FocusState) {
        _state.value = newState
        platform.store.put("focus_state", AppJson.encodeToString(newState))
        platform.onStateChanged(newState)
    }

    /** Catches up on deadlines (also after the app was closed or asleep). Safe to call any time. */
    fun tick() {
        val now = platform.now()
        if (_state.value.phase == Phase.WARMUP && now >= _state.value.warmupEndsAt) skipWarmup()
        val t = _state.value.timer
        if (t.isSet && t.running && now >= t.endsAt) onTimerDone()
        val p = _prayer.value
        if (p.running && now >= p.endsAt) completePrayer()
        fireDueReminders(now)
        onTask.tick()
        if (_state.value.phase == Phase.ACTIVE && now - lastKeepFocus >= KEEP_FOCUS_EVERY_MS) {
            lastKeepFocus = now
            platform.keepFocus()
        }
        tasks.rollOver()
    }

    // ---------------- Focus Mode ----------------

    fun startFocus(skipCountdown: Boolean = false): String {
        if (_state.value.phase != Phase.OFF) return "Focus Mode is already on"
        val warmupMs = settings.warmupMinutes * 60_000L
        if (skipCountdown || warmupMs <= 0L) {
            activate()
            return "Focus Mode on. Stay locked in."
        }
        val endsAt = platform.now() + warmupMs
        commit(_state.value.copy(phase = Phase.WARMUP, warmupEndsAt = endsAt, warmupTotalMs = warmupMs))
        platform.schedule(ACTION_WARMUP_DONE, endsAt)
        return "Focus Mode starts in ${formatMinutes(settings.warmupMinutes)}"
    }

    fun skipWarmup() {
        if (_state.value.phase == Phase.WARMUP) activate()
    }

    private fun activate() {
        platform.cancel(ACTION_WARMUP_DONE)
        platform.enterFocus()
        lastKeepFocus = platform.now()
        commit(_state.value.copy(phase = Phase.ACTIVE, activeSince = platform.now(), warmupEndsAt = 0L))
    }

    fun stopFocus() {
        platform.cancel(ACTION_WARMUP_DONE)
        if (_state.value.phase == Phase.ACTIVE) platform.exitFocus()
        commit(_state.value.copy(phase = Phase.OFF, warmupEndsAt = 0L, activeSince = 0L))
    }

    // ---------------- Timer ----------------

    fun startTimer(presetIndex: Int): String? {
        val preset = PRESETS.getOrNull(presetIndex) ?: return null
        return begin(presetIndex, preset.minutes, 0)
    }

    /** Starts a session with a length the user typed in (1 minute to 10 hours). */
    fun startCustomTimer(minutes: Int): String? {
        val m = minutes.coerceIn(1, 600)
        settings.customMinutes = m
        return begin(CUSTOM_PRESET, m, m)
    }

    private fun begin(presetIndex: Int, minutes: Int, customMinutes: Int): String? {
        val now = platform.now()
        val endsAt = now + minutes * 60_000L
        commit(_state.value.copy(timer = TimerState(presetIndex, endsAt, 0L, true, now, customMinutes)))
        platform.schedule(ACTION_TIMER_DONE, endsAt)
        return if (settings.timerControlsFocus && _state.value.phase == Phase.OFF) startFocus() else null
    }

    fun pauseTimer() {
        val t = _state.value.timer
        if (!t.isSet || !t.running) return
        platform.cancel(ACTION_TIMER_DONE)
        val remaining = (t.endsAt - platform.now()).coerceAtLeast(0L)
        commit(_state.value.copy(timer = t.copy(running = false, pausedRemaining = remaining)))
    }

    fun resumeTimer() {
        val t = _state.value.timer
        if (!t.isSet || t.running) return
        val endsAt = platform.now() + t.pausedRemaining
        commit(_state.value.copy(timer = t.copy(running = true, endsAt = endsAt, pausedRemaining = 0L)))
        platform.schedule(ACTION_TIMER_DONE, endsAt)
    }

    fun cancelTimer() {
        platform.cancel(ACTION_TIMER_DONE)
        commit(_state.value.copy(timer = TimerState()))
        if (settings.timerControlsFocus && _state.value.phase != Phase.OFF) stopFocus()
    }

    fun onTimerDone() {
        val t = _state.value.timer
        val preset = t.preset ?: return
        platform.cancel(ACTION_TIMER_DONE)
        calendar.logSession(preset, t.startedAt)
        commit(_state.value.copy(timer = TimerState()))
        if (settings.timerControlsFocus && _state.value.phase != Phase.OFF) stopFocus()
        platform.sessionComplete(
            "${preset.name} complete",
            "${formatMinutes(preset.minutes)} of focus logged to your calendar. Nice work.",
        )
    }

    // ---------------- Prayer ----------------

    private fun loadPrayer(): PrayerState {
        val p = platform.store.get("prayer_state")
            ?.let { runCatching { AppJson.decodeFromString<PrayerState>(it) }.getOrNull() }
            ?: PrayerState()
        // The music doesn't survive a restart, so a session left running comes back paused.
        return if (p.running) p.copy(running = false, remaining = (p.endsAt - platform.now()).coerceIn(0L, PRAYER_MS)) else p
    }

    private fun commitPrayer(p: PrayerState) {
        _prayer.value = p
        platform.store.put("prayer_state", AppJson.encodeToString(p))
    }

    val prayedToday: Boolean get() = _prayer.value.doneOn == tasks.today()

    /** True when the prayer screen should open by itself (after the splash, or when a snooze runs out). */
    fun prayerDue(): Boolean {
        if (!settings.prayerEnabled || prayedToday) return false
        // You cancelled it today: remember that and don't bring it back until tomorrow.
        if (_prayer.value.declinedOn == tasks.today()) return false
        if (_state.value.timer.isSet) return false
        return platform.now() >= _prayer.value.snoozeUntil
    }

    fun startPrayer() {
        val p = _prayer.value
        if (p.running) return
        val remaining = if (p.remaining <= 0L) PRAYER_MS else p.remaining
        commitPrayer(p.copy(running = true, endsAt = platform.now() + remaining, remaining = remaining))
        val path = settings.musicPath ?: return
        if (musicLoaded) platform.resumeMusic() else platform.playMusic(path, settings.musicVolume)
        musicLoaded = true
    }

    private var musicLoaded = false

    private fun stopMusic() {
        musicLoaded = false
        platform.stopMusic()
    }

    /** Setup tab: choose new prayer music (replaces and deletes the old file). */
    fun setMusic(path: String?, name: String?) {
        val old = settings.musicPath
        stopMusic()
        settings.musicPath = path
        settings.musicName = name
        settings.musicSetupDone = true
        if (old != null && old != path) platform.deleteFile(old)
        // Picked mid-prayer (e.g. from the first-time prompt): start the new song right away.
        if (_prayer.value.running && path != null) {
            platform.playMusic(path, settings.musicVolume)
            musicLoaded = true
        }
    }

    /** Called when the splash fades: start the prayer timer (and its music) by itself. */
    fun autoStartPrayer() {
        val p = _prayer.value
        if (prayedToday || p.running || p.remaining < PRAYER_MS || p.declinedOn == tasks.today()) return
        startPrayer()
    }

    fun setMusicVolume(v: Float) {
        settings.musicVolume = v
        platform.setMusicVolume(v)
    }

    /** Setup tab: play the chosen music to check it (true) or stop the preview (false). */
    fun previewMusic(on: Boolean) {
        if (_prayer.value.running) return
        val path = settings.musicPath
        stopMusic()
        if (on && path != null) platform.playMusic(path, settings.musicVolume)
    }

    fun pausePrayer() {
        val p = _prayer.value
        if (!p.running) return
        commitPrayer(p.copy(running = false, remaining = (p.endsAt - platform.now()).coerceAtLeast(0L)))
        platform.pauseMusic()
    }

    /** Cancel: stop the timer and reset it to the full 15 minutes. */
    fun resetPrayer() {
        commitPrayer(_prayer.value.copy(running = false, remaining = PRAYER_MS, endsAt = 0L, declinedOn = tasks.today()))
        stopMusic()
    }

    /** "Continue anyway": leave prayer for now. It asks again in 30 minutes. */
    fun snoozePrayer() {
        commitPrayer(_prayer.value.copy(running = false, remaining = PRAYER_MS, endsAt = 0L, snoozeUntil = platform.now() + PRAYER_SNOOZE_MS))
        stopMusic()
    }

    fun completePrayer() {
        val p = _prayer.value
        val today = tasks.today()
        if (p.doneOn == today && !p.running) return
        val streak = if (p.doneOn == today.minus(DatePeriod(days = 1))) p.streak + 1 else if (p.doneOn == today) p.streak else 1
        commitPrayer(PrayerState(doneOn = today, streak = streak))
        stopMusic()
        val t = toLocal(platform.now())
        calendar.add(today, "Prayer · $PRAYER_MINUTES min", LocalTime(t.hour, t.minute))
        platform.sessionComplete("Prayer complete", "You started the day with prayer. Go do great work.")
    }

    /** App is quitting (desktop): restore sound now. Session timing is kept for next launch. */
    fun shutdown() {
        if (_state.value.phase == Phase.ACTIVE) platform.exitFocus()
    }
}
