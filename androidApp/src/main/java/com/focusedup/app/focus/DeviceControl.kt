package com.focusedup.app.focus

import android.app.NotificationManager
import android.content.Context
import android.media.AudioManager

/**
 * Turns the phone quiet for a session and puts it back exactly how it was.
 *
 * - Do Not Disturb is set to "Alarms only": calls and notifications are blocked,
 *   but media playback (Adobe Premiere Rush, Lightroom, any editor preview) stays audible.
 * - Ring, notification and system volumes are dropped to 0. Media volume is left alone,
 *   because Android does not allow muting individual apps.
 */
object DeviceControl {
    private val STREAMS = intArrayOf(
        AudioManager.STREAM_RING,
        AudioManager.STREAM_NOTIFICATION,
        AudioManager.STREAM_SYSTEM,
    )

    private fun prefs(context: Context) =
        context.getSharedPreferences("focused_up_device", Context.MODE_PRIVATE)

    fun hasDndAccess(context: Context): Boolean =
        context.getSystemService(NotificationManager::class.java).isNotificationPolicyAccessGranted

    fun enterFocus(context: Context) {
        val sp = prefs(context)
        val am = context.getSystemService(AudioManager::class.java)
        val nm = context.getSystemService(NotificationManager::class.java)

        if (!sp.getBoolean("saved", false)) {
            sp.edit()
                .putBoolean("saved", true)
                .putInt("filter", nm.currentInterruptionFilter)
                .putInt("ringer", am.ringerMode)
                .putString("vols", STREAMS.joinToString(",") { am.getStreamVolume(it).toString() })
                .commit()
        }

        // Lower volumes first: once DND is on, some phones refuse ring volume changes.
        for (stream in STREAMS) runCatching { am.setStreamVolume(stream, 0, 0) }

        if (hasDndAccess(context)) {
            runCatching { nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALARMS) }
        }
    }

    fun exitFocus(context: Context) {
        val sp = prefs(context)
        val am = context.getSystemService(AudioManager::class.java)
        val nm = context.getSystemService(NotificationManager::class.java)
        val dnd = hasDndAccess(context)

        if (!sp.getBoolean("saved", false)) {
            if (dnd) runCatching { nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL) }
            return
        }

        val filter = sp.getInt("filter", NotificationManager.INTERRUPTION_FILTER_ALL)
        if (dnd) {
            val restore = if (filter > NotificationManager.INTERRUPTION_FILTER_UNKNOWN) filter
            else NotificationManager.INTERRUPTION_FILTER_ALL
            runCatching { nm.setInterruptionFilter(restore) }
        }

        runCatching { am.ringerMode = sp.getInt("ringer", AudioManager.RINGER_MODE_NORMAL) }
        val volumes = (sp.getString("vols", "") ?: "").split(",").mapNotNull { it.toIntOrNull() }
        STREAMS.forEachIndexed { i, stream ->
            volumes.getOrNull(i)?.let { v -> runCatching { am.setStreamVolume(stream, v, 0) } }
        }
        sp.edit().clear().commit()
    }
}
