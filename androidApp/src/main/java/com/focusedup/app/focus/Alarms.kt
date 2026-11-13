package com.focusedup.app.focus

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.focusedup.shared.engine.FocusEngine

/** Exact wake-ups for "countdown finished" and "timer finished", even in deep sleep. */
object Alarms {
    fun schedule(context: Context, action: String, atMillis: Long) {
        val am = context.getSystemService(AlarmManager::class.java)
        val pi = pending(context, action)
        val exactAllowed = Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()
        if (exactAllowed) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, pi)
        else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, pi)
    }

    fun cancel(context: Context, action: String) {
        context.getSystemService(AlarmManager::class.java).cancel(pending(context, action))
    }

    fun pending(context: Context, action: String): PendingIntent = PendingIntent.getBroadcast(
        context,
        action.hashCode(),
        Intent(context, AlarmReceiver::class.java).setAction(action),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
}

/** Handles alarm wake-ups and the buttons on the session notification. */
class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val engine = Graph.engine(context)
        when (intent.action) {
            FocusEngine.ACTION_WARMUP_DONE, FocusEngine.ACTION_SKIP_WARMUP -> engine.skipWarmup()
            FocusEngine.ACTION_TIMER_DONE -> engine.tick()
            FocusEngine.ACTION_STOP_FOCUS -> engine.stopFocus()
            FocusEngine.ACTION_END_TIMER -> engine.cancelTimer()
            Reminders.ACTION_REMIND -> {
                Notifications.notion(context, intent.getStringExtra("title").orEmpty(), intent.getStringExtra("text").orEmpty())
                engine.reminderFired(intent.getStringExtra("id").orEmpty())
            }
        }
    }
}

/** "Remind me" alarms for Notion items. They fire even if the app is closed. */
object Reminders {
    const val ACTION_REMIND = "com.focusedup.app.REMIND"

    private fun pending(context: Context, r: String, title: String = "", text: String = ""): PendingIntent = PendingIntent.getBroadcast(
        context,
        r.hashCode(),
        Intent(context, AlarmReceiver::class.java).setAction(ACTION_REMIND)
            .setData(android.net.Uri.parse("focusedup://reminder/$r"))
            .putExtra("id", r).putExtra("title", title).putExtra("text", text),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    fun schedule(context: Context, r: com.focusedup.shared.model.Reminder) {
        val am = context.getSystemService(AlarmManager::class.java)
        val pi = pending(context, r.id, r.title, r.text)
        val exactAllowed = Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()
        if (exactAllowed) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, r.at, pi)
        else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, r.at, pi)
    }

    fun cancel(context: Context, id: String) {
        context.getSystemService(AlarmManager::class.java).cancel(pending(context, id))
    }
}
