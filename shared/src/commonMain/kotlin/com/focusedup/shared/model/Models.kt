package com.focusedup.shared.model

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.serialization.Serializable

@Serializable
enum class Phase { OFF, WARMUP, ACTIVE }

/** A focus session length. [name] and [tagline] are what the Timer tab shows. */
data class Preset(val code: String, val name: String, val tagline: String, val minutes: Int)

val PRESETS = listOf(
    Preset("01", "Quick Sprint", "A sharp 40-minute burst", 40),
    Preset("02", "Deep Dive", "Heads-down building time", 90),
    Preset("03", "Full Armor", "Suit up for the long haul", 105),
)

/** presetIndex used for a session with a length you typed in yourself. */
const val CUSTOM_PRESET = 99

fun customPreset(minutes: Int) = Preset("C", "Custom", "Your own length", minutes)

@Serializable
data class TimerState(
    val presetIndex: Int = -1,
    val endsAt: Long = 0L,
    val pausedRemaining: Long = 0L,
    val running: Boolean = false,
    val startedAt: Long = 0L,
    /** Length of a custom session (presetIndex == CUSTOM_PRESET). */
    val customMinutes: Int = 0,
) {
    val isSet: Boolean get() = preset != null
    val preset: Preset?
        get() = if (presetIndex == CUSTOM_PRESET && customMinutes > 0) customPreset(customMinutes)
        else PRESETS.getOrNull(presetIndex)
}

@Serializable
data class FocusState(
    val phase: Phase = Phase.OFF,
    val warmupEndsAt: Long = 0L,
    val warmupTotalMs: Long = 0L,
    val activeSince: Long = 0L,
    val timer: TimerState = TimerState(),
) {
    val needsService: Boolean get() = phase != Phase.OFF || timer.isSet
}

@Serializable
data class TaskItem(
    val id: Long,
    val title: String,
    /** The day this task is on the list for. Unfinished tasks roll forward to today. */
    val date: LocalDate,
    val done: Boolean = false,
    val doneOn: LocalDate? = null,
    /** Hidden from today's list by "Clear done", but still shown in the calendar history. */
    val cleared: Boolean = false,
)

@Serializable
data class CalEvent(
    val id: Long,
    val date: LocalDate,
    val title: String,
    val time: LocalTime? = null,
    /** Push notification: -1 none, otherwise minutes before the start (0 = at start). */
    val notifyMin: Int = -1,
    /** > 0 for completed Focused Up sessions (logged automatically). */
    val focusMinutes: Int = 0,
) {
    val isFocus: Boolean get() = focusMinutes > 0
}

// ---------------- Prayer ----------------

const val PRAYER_MINUTES = 15
const val PRAYER_MS = PRAYER_MINUTES * 60_000L
/** How long "Snooze" puts the prayer screen away for. */
const val PRAYER_SNOOZE_MS = 30 * 60_000L

@Serializable
data class PrayerState(
    val running: Boolean = false,
    val endsAt: Long = 0L,
    val remaining: Long = PRAYER_MS,
    val doneOn: LocalDate? = null,
    val snoozeUntil: Long = 0L,
    /** Day the user cancelled the prayer; it won't pop up or auto-start again that day. */
    val declinedOn: LocalDate? = null,
    /** Days in a row with a finished prayer, ending on [doneOn]. */
    val streak: Int = 0,
)

// ---------------- Appearance ----------------

@Serializable
data class Appearance(
    val accent: Int = 0,
    val base: Int = 0,
    /** Hue shift in degrees, -180..180. */
    val hue: Float = 0f,
    /** 0.6 (soft) .. 1.4 (crisp). */
    val contrast: Float = 1f,
    /** 0 (grey) .. 1.6 (vivid). */
    val saturation: Float = 1f,
    val iconColor: Int = 0,
    val iconStyle: Int = 0,
)

// ---------------- Profile ----------------

@Serializable
data class Profile(
    val name: String = "",
    val tagline: String = "",
    val color: Int = 0,
    val cover: String? = null,
    val photos: List<String> = emptyList(),
    val photoIndex: Int = 0,
) {
    val photo: String? get() = photos.getOrNull(photoIndex) ?: photos.firstOrNull()
}

// ---------------- Reminders ----------------

/** "Remind me" on a Notion item. [id] is the Notion page id. */
@Serializable
data class Reminder(val id: String, val title: String, val text: String, val at: Long)
