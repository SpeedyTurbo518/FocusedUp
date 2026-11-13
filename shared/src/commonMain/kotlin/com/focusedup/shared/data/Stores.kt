package com.focusedup.shared.data

import com.focusedup.shared.model.Appearance
import com.focusedup.shared.model.CalEvent
import com.focusedup.shared.model.Preset
import com.focusedup.shared.model.Profile
import com.focusedup.shared.model.TaskItem
import com.focusedup.shared.platform.FocusPlatform
import com.focusedup.shared.platform.KeyValueStore
import com.focusedup.shared.ui.FColors
import com.focusedup.shared.ui.buildPalette
import com.focusedup.shared.util.formatMinutes
import com.focusedup.shared.util.toLocal
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.minus
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

internal val AppJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}

class AppSettings(private val store: KeyValueStore) {
    var warmupMinutes: Int
        get() = store.get("warmup_min")?.toIntOrNull() ?: 0
        set(v) = store.put("warmup_min", v.toString())

    var timerControlsFocus: Boolean
        get() = store.get("timer_controls_focus")?.toBooleanStrictOrNull() ?: true
        set(v) = store.put("timer_controls_focus", v.toString())

    /** Last length typed into the custom timer. */
    var customMinutes: Int
        get() = store.get("custom_min")?.toIntOrNull() ?: 60
        set(v) = store.put("custom_min", v.toString())

    /** Show the prayer screen after the splash, once a day. */
    var prayerEnabled: Boolean
        get() = store.get("prayer_enabled")?.toBooleanStrictOrNull() ?: true
        set(v) = store.put("prayer_enabled", v.toString())

    var musicPath: String?
        get() = store.get("music_path")?.takeIf { it.isNotEmpty() }
        set(v) = store.put("music_path", v ?: "")

    var musicName: String?
        get() = store.get("music_name")?.takeIf { it.isNotEmpty() }
        set(v) = store.put("music_name", v ?: "")

    var musicVolume: Float
        get() = store.get("music_volume")?.toFloatOrNull() ?: 0.6f
        set(v) = store.put("music_volume", v.toString())

    /** True once the first-time music prompt was answered (picked or skipped). */
    var musicSetupDone: Boolean
        get() = store.get("music_setup_done")?.toBooleanStrictOrNull() ?: false
        set(v) = store.put("music_setup_done", v.toString())

    /** Remembers small UI choices (last tab, typed inputs, calendar day...) across app restarts. */
    fun ui(key: String): String? = store.get("ui_$key")?.takeIf { it.isNotEmpty() }
    fun setUi(key: String, value: String?) = store.put("ui_$key", value ?: "")

    /** Default push notification for new calendar events: -1 none, 0 at start, 15, 60 (minutes before). */
    var eventNotify: Int
        get() = store.get("event_notify")?.toIntOrNull() ?: 15
        set(v) = store.put("event_notify", v.toString())

    /** Desktop: open in full screen. */
    var startFullscreen: Boolean
        get() = store.get("start_fullscreen")?.toBooleanStrictOrNull() ?: false
        set(v) = store.put("start_fullscreen", v.toString())
}

/** App colors and icon. Changing it repaints the whole UI immediately. */
class AppearanceStore(private val store: KeyValueStore) {
    private val _value = MutableStateFlow(
        store.get("appearance")?.let { runCatching { AppJson.decodeFromString<Appearance>(it) }.getOrNull() } ?: Appearance()
    )
    val value: StateFlow<Appearance> = _value.asStateFlow()

    init {
        FColors.palette = buildPalette(_value.value)
    }

    fun update(change: (Appearance) -> Appearance) {
        val v = change(_value.value)
        _value.value = v
        FColors.palette = buildPalette(v)
        store.put("appearance", AppJson.encodeToString(v))
    }
}

/** Name, colors and photos shown on the Profile tab. */
class ProfileStore(private val store: KeyValueStore, private val platform: FocusPlatform) {
    private val _value = MutableStateFlow(
        store.get("profile")?.let { runCatching { AppJson.decodeFromString<Profile>(it) }.getOrNull() } ?: Profile()
    )
    val value: StateFlow<Profile> = _value.asStateFlow()

    fun update(change: (Profile) -> Profile) {
        val v = change(_value.value)
        _value.value = v
        store.put("profile", AppJson.encodeToString(v))
    }

    /** Saves an edited profile in one go and deletes photos that are no longer used. */
    fun commit(edited: Profile) {
        val old = _value.value
        val keep = (edited.photos + listOfNotNull(edited.cover)).toSet()
        update { edited.copy(photoIndex = edited.photoIndex.coerceIn(0, (edited.photos.size - 1).coerceAtLeast(0))) }
        (old.photos + listOfNotNull(old.cover)).filter { it !in keep }.forEach(platform::deleteFile)
    }

    /** Edit cancelled: delete photos picked during the edit that were never saved. */
    fun discard(edited: Profile) {
        val saved = _value.value.let { (it.photos + listOfNotNull(it.cover)).toSet() }
        (edited.photos + listOfNotNull(edited.cover)).filter { it !in saved }.forEach(platform::deleteFile)
    }

    fun setCover(path: String?) {
        val old = _value.value.cover
        update { it.copy(cover = path) }
        if (old != null && old != path) platform.deleteFile(old)
    }

    fun addPhoto(path: String) = update {
        val photos = (it.photos + path).takeLast(MAX_PHOTOS)
        it.copy(photos = photos, photoIndex = photos.lastIndex)
    }

    fun removePhoto(index: Int) {
        val p = _value.value
        val path = p.photos.getOrNull(index) ?: return
        val photos = p.photos.filterIndexed { i, _ -> i != index }
        val current = when {
            index < p.photoIndex -> p.photoIndex - 1
            index == p.photoIndex -> 0
            else -> p.photoIndex
        }
        update { it.copy(photos = photos, photoIndex = current.coerceIn(0, (photos.size - 1).coerceAtLeast(0))) }
        platform.deleteFile(path)
    }

    companion object {
        const val MAX_PHOTOS = 6
    }
}

private class IdMaker(private val now: () -> Long) {
    private var seq = 0
    fun next(): Long = now() * 1000 + (seq++ % 1000)
}

/** Daily to-do list. Unfinished tasks automatically carry over to the next day. */
class TaskStore(private val store: KeyValueStore, private val now: () -> Long) {
    private val ids = IdMaker(now)
    private val _tasks = MutableStateFlow(load())
    val tasks: StateFlow<List<TaskItem>> = _tasks.asStateFlow()
    private var lastRollDate: LocalDate? = null

    fun today(): LocalDate = toLocal(now()).date

    fun rollOver() {
        val today = today()
        if (today == lastRollDate) return
        lastRollDate = today
        val keepAfter = today.minus(DatePeriod(days = 90))
        val list = _tasks.value
        val updated = list
            .filterNot { it.done && it.date < keepAfter }
            .map { if (!it.done && it.date < today) it.copy(date = today) else it }
        if (updated != list) save(updated)
    }

    /** Adds one task per line, so a pasted list becomes several tasks. Returns how many were added. */
    fun add(text: String): Int {
        val titles = text.lines()
            .map { it.trim().trimStart('-', '*', '•', '·', '□', '☐').trim() }
            .filter { it.isNotEmpty() }
        if (titles.isEmpty()) return 0
        val today = today()
        save(_tasks.value + titles.map { TaskItem(ids.next(), it, today) })
        return titles.size
    }

    fun toggle(id: Long) {
        val today = today()
        save(_tasks.value.map {
            if (it.id == id) it.copy(done = !it.done, doneOn = if (!it.done) today else null) else it
        })
    }

    fun remove(id: Long) = save(_tasks.value.filterNot { it.id == id })

    fun clearDone() {
        val today = today()
        save(_tasks.value.map { if (it.done && it.date == today) it.copy(cleared = true) else it })
    }

    private fun load(): List<TaskItem> = store.get("tasks")
        ?.let { runCatching { AppJson.decodeFromString<List<TaskItem>>(it) }.getOrNull() }
        ?: emptyList()

    private fun save(list: List<TaskItem>) {
        _tasks.value = list
        store.put("tasks", AppJson.encodeToString(list))
    }
}

/** Offline calendar: your events plus automatically logged focus sessions. */
class CalendarStore(private val store: KeyValueStore, private val now: () -> Long) {
    private val ids = IdMaker(now)
    private val _events = MutableStateFlow(load())
    val events: StateFlow<List<CalEvent>> = _events.asStateFlow()

    fun add(date: LocalDate, title: String, time: LocalTime?, focusMinutes: Int = 0, notifyMin: Int = -1): CalEvent {
        val e = CalEvent(ids.next(), date, title, time, focusMinutes = focusMinutes, notifyMin = notifyMin)
        save(_events.value + e)
        return e
    }

    fun remove(id: Long) = save(_events.value.filterNot { it.id == id })

    fun logSession(preset: Preset, startedAt: Long) {
        val start = toLocal(if (startedAt > 0) startedAt else now())
        add(
            start.date,
            "Focused Up ${preset.code} · ${preset.name} (${formatMinutes(preset.minutes)})",
            LocalTime(start.hour, start.minute),
            preset.minutes,
        )
    }

    private fun load(): List<CalEvent> = store.get("calendar")
        ?.let { runCatching { AppJson.decodeFromString<List<CalEvent>>(it) }.getOrNull() }
        ?: emptyList()

    private fun save(list: List<CalEvent>) {
        _events.value = list
        store.put("calendar", AppJson.encodeToString(list))
    }
}
