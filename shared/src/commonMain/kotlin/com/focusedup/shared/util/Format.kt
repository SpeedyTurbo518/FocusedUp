package com.focusedup.shared.util

import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.toLocalDateTime

val MONTHS = listOf(
    "January", "February", "March", "April", "May", "June",
    "July", "August", "September", "October", "November", "December",
)
val WEEKDAYS = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")

private fun pad2(n: Long) = n.toString().padStart(2, '0')

fun formatClock(ms: Long, roundUp: Boolean = true): String {
    val totalSec = (if (roundUp) (ms + 999) / 1000 else ms / 1000).coerceAtLeast(0)
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return if (h > 0) "$h:${pad2(m)}:${pad2(s)}" else "$m:${pad2(s)}"
}

fun formatMinutes(min: Int): String = when {
    min < 60 -> "$min min"
    min % 60 == 0 -> "${min / 60} hr"
    else -> "${min / 60} hr ${min % 60} min"
}

fun formatTime(t: LocalTime): String {
    val h12 = (t.hour + 11) % 12 + 1
    return "$h12:${pad2(t.minute.toLong())} ${if (t.hour < 12) "AM" else "PM"}"
}

fun toLocal(epochMs: Long): LocalDateTime =
    Instant.fromEpochMilliseconds(epochMs).toLocalDateTime(TimeZone.currentSystemDefault())

fun LocalDate.weekdayName(): String = WEEKDAYS[dayOfWeek.isoDayNumber - 1]
fun LocalDate.monthDay(): String = "${MONTHS[monthNumber - 1]} $dayOfMonth"
fun LocalDate.longLabel(): String = "${weekdayName()}, ${monthDay()}"
fun LocalDate.monthYear(): String = "${MONTHS[monthNumber - 1]} $year"

/** "The Hush [SruH2GgFEBQ].opus" → "The Hush". */
fun cleanSongName(name: String): String {
    val noExt = if ('.' in name && name.substringAfterLast('.').length in 2..5) name.substringBeforeLast('.') else name
    return noExt.replace(Regex("""\s*\[[^\]]*\]"""), "").replace('_', ' ').trim().ifEmpty { name }
}
