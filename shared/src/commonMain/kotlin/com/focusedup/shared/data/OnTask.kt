package com.focusedup.shared.data

import com.focusedup.shared.model.Reminder
import com.focusedup.shared.platform.KeyValueStore
import com.focusedup.shared.util.formatTime
import com.focusedup.shared.util.toLocal
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString

val DEFAULT_DUTIES = listOf(
    "Greet every customer within 10 seconds",
    "Recover and face my section",
    "Restock from the back room",
    "Check the fitting rooms",
    "Tidy the register area",
    "Check prices and signs",
    "Mention the current promo",
)

val COUNTERS = listOf("Customers helped", "Sales closed", "Add-ons", "Sign-ups")

/** Gentle nudges that rotate through check-in notifications. */
private val CHECK_IN_PROMPTS = listOf(
    "Phone down, eyes up. Anyone in your zone need help?",
    "Quick scan: is your section faced and full?",
    "Walk your zone and greet the next customer.",
    "Slow moment? Recover one shelf or restock one item.",
    "Offer an add-on with your next sale.",
    "Check the fitting rooms and the floor near you.",
)

/** Your usual shift setup. Remembered between shifts. */
@Serializable
data class ShiftSettings(
    val hours: Float = 8f,
    /** Minutes between "back on task" nudges, 0 = off. */
    val checkInMin: Int = 30,
    /** Minutes between break reminders, 0 = off. */
    val breakEveryMin: Int = 120,
    val breakLenMin: Int = 15,
    /** Lunch length in minutes, reminded halfway through, 0 = off. */
    val lunchMin: Int = 30,
    val salesGoal: Int = 0,
    val duties: List<String> = DEFAULT_DUTIES,
)

/** The shift you're working right now (survives closing the app). */
@Serializable
data class ShiftState(
    val active: Boolean = false,
    val startedAt: Long = 0L,
    val endsAt: Long = 0L,
    val breakUntil: Long = 0L,
    val done: Set<String> = emptySet(),
    val counts: Map<String, Int> = emptyMap(),
    val sales: Int = 0,
)

/**
 * "On Task" for sales floors and other retail shifts: shift timer, check-in nudges,
 * break reminders, a floor checklist and a simple scoreboard.
 */
class OnTaskStore(
    private val store: KeyValueStore,
    private val now: () -> Long,
    private val remind: (Reminder) -> Unit,
    private val cancelReminder: (String) -> Unit,
    private val logShift: (title: String) -> Unit,
) {
    private val _settings = MutableStateFlow(
        store.get("shift_settings")?.let { runCatching { AppJson.decodeFromString<ShiftSettings>(it) }.getOrNull() } ?: ShiftSettings()
    )
    val settings: StateFlow<ShiftSettings> = _settings.asStateFlow()

    private val _state = MutableStateFlow(
        store.get("shift_state")?.let { runCatching { AppJson.decodeFromString<ShiftState>(it) }.getOrNull() } ?: ShiftState()
    )
    val state: StateFlow<ShiftState> = _state.asStateFlow()

    private var scheduled: List<String>
        get() = store.get("shift_reminders")?.split(',')?.filter { it.isNotBlank() }.orEmpty()
        set(v) = store.put("shift_reminders", v.joinToString(","))

    fun updateSettings(change: (ShiftSettings) -> ShiftSettings) {
        val v = change(_settings.value)
        _settings.value = v
        store.put("shift_settings", AppJson.encodeToString(v))
    }

    private fun commit(s: ShiftState) {
        _state.value = s
        store.put("shift_state", AppJson.encodeToString(s))
    }

    fun startShift() {
        val t = now()
        val cfg = _settings.value
        val end = t + (cfg.hours * 60 * 60_000L).toLong()
        commit(ShiftState(active = true, startedAt = t, endsAt = end))
        scheduleAll(t, end, cfg)
    }

    private fun scheduleAll(start: Long, end: Long, cfg: ShiftSettings) {
        cancelAll()
        val ids = mutableListOf<String>()
        fun add(id: String, title: String, text: String, at: Long) {
            if (at <= now() || at >= end) return
            remind(Reminder(id, title, text, at))
            ids += id
        }
        if (cfg.checkInMin > 0) {
            var at = start + cfg.checkInMin * 60_000L
            var n = 0
            while (at < end && n < 60) {
                add("shift-ci-$n", "On task?", CHECK_IN_PROMPTS[n % CHECK_IN_PROMPTS.size], at)
                at += cfg.checkInMin * 60_000L
                n++
            }
        }
        if (cfg.breakEveryMin > 0) {
            var at = start + cfg.breakEveryMin * 60_000L
            var n = 0
            while (at < end && n < 20) {
                add("shift-br-$n", "Break time", "Take your ${cfg.breakLenMin}-minute break. You've earned it.", at)
                at += cfg.breakEveryMin * 60_000L
                n++
            }
        }
        if (cfg.lunchMin > 0) add("shift-lunch", "Lunch", "Halfway there. Time for your ${cfg.lunchMin}-minute lunch.", start + (end - start) / 2)
        add("shift-end", "Shift ends in 15 minutes", "Wrap up your section and hand off anything open.", end - 15 * 60_000L)
        // The end itself is always announced (add() skips times at the very end).
        if (end > now()) {
            remind(Reminder("shift-done", "Shift complete", "Nice work today. Tap to see your numbers.", end))
            ids += "shift-done"
        }
        scheduled = ids
    }

    private fun cancelAll() {
        scheduled.forEach(cancelReminder)
        scheduled = emptyList()
    }

    fun startBreak(minutes: Int = _settings.value.breakLenMin) {
        val s = _state.value
        if (!s.active) return
        val until = now() + minutes * 60_000L
        commit(s.copy(breakUntil = until))
        // Let you know when the break is over.
        remind(Reminder("shift-back", "Break's over", "Back on the floor. You've got this.", until))
        scheduled = scheduled + "shift-back"
    }

    fun endBreak() {
        val s = _state.value
        commit(s.copy(breakUntil = 0L))
        cancelReminder("shift-back")
    }

    fun endShift() {
        val s = _state.value
        if (!s.active) return
        cancelAll()
        val hours = (now() - s.startedAt) / 3_600_000.0
        val parts = listOfNotNull(
            "${(hours * 10).toInt() / 10.0} hr",
            s.counts["Customers helped"]?.takeIf { it > 0 }?.let { "$it helped" },
            s.counts["Sales closed"]?.takeIf { it > 0 }?.let { "$it sales" },
            s.sales.takeIf { it > 0 }?.let { "$$it" },
        )
        logShift("Shift · ${parts.joinToString(" · ")}")
        commit(ShiftState())
    }

    fun toggleDuty(duty: String) {
        val s = _state.value
        commit(s.copy(done = if (duty in s.done) s.done - duty else s.done + duty))
    }

    fun count(name: String, delta: Int) {
        val s = _state.value
        val v = ((s.counts[name] ?: 0) + delta).coerceAtLeast(0)
        commit(s.copy(counts = s.counts + (name to v)))
    }

    fun setSales(dollars: Int) = commit(_state.value.copy(sales = dollars.coerceAtLeast(0)))

    fun addDuty(text: String) {
        val t = text.trim()
        if (t.isEmpty() || t in _settings.value.duties) return
        updateSettings { it.copy(duties = it.duties + t) }
    }

    fun removeDuty(text: String) = updateSettings { it.copy(duties = it.duties - text) }

    /** Time of the next nudge, for display. */
    fun nextCheckInLabel(): String? {
        val s = _state.value
        val cfg = _settings.value
        if (!s.active || cfg.checkInMin <= 0) return null
        val step = cfg.checkInMin * 60_000L
        val t = now()
        val next = s.startedAt + ((t - s.startedAt) / step + 1) * step
        return if (next < s.endsAt) formatTime(toLocal(next).time) else null
    }

    /** Runs every second from the engine: end the shift on time even if the app was closed. */
    fun tick() {
        val s = _state.value
        if (s.active && now() >= s.endsAt + 60_000L) endShift()
        if (s.breakUntil != 0L && now() >= s.breakUntil) commit(s.copy(breakUntil = 0L))
    }
}
