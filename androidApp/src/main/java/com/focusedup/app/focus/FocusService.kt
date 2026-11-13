package com.focusedup.app.focus

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.focusedup.app.MainActivity
import com.focusedup.app.R
import com.focusedup.shared.engine.FocusEngine
import com.focusedup.shared.model.FocusState
import com.focusedup.shared.model.Phase
import com.focusedup.shared.util.formatClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/** Keeps the session alive in the background and shows a live countdown notification. */
class FocusService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        running = true
        val engine = Graph.engine(this)
        Notifications.ensureChannels(this)
        val type = if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0
        ServiceCompat.startForeground(
            this, Notifications.ID_SESSION, Notifications.session(this, engine.state.value), type
        )
        scope.launch {
            engine.state.collect { s ->
                if (!s.needsService) {
                    ServiceCompat.stopForeground(this@FocusService, ServiceCompat.STOP_FOREGROUND_REMOVE)
                    stopSelf()
                } else {
                    getSystemService(NotificationManager::class.java)
                        .notify(Notifications.ID_SESSION, Notifications.session(this@FocusService, s))
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onDestroy() {
        running = false
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        @Volatile var running = false

        fun sync(context: Context, state: FocusState) {
            if (state.needsService && !running) {
                runCatching {
                    ContextCompat.startForegroundService(context, Intent(context, FocusService::class.java))
                }
            }
        }
    }
}

object Notifications {
    const val CH_SESSION = "session"
    const val CH_ALERTS = "alerts"
    const val CH_NOTION = "notion_updates"
    const val ID_SESSION = 1
    const val ID_DONE = 2

    fun ensureChannels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CH_SESSION, "Focus session", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Live countdown while Focus Mode or the timer is running"
                setShowBadge(false)
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_ALERTS, "Session complete", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Chime when a Focused Up timer finishes"
                setSound(
                    RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build(),
                )
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 400, 200, 400, 200, 700)
            }
        )
    }

    private fun ensureNotionChannel(context: Context) {
        val sound = android.net.Uri.parse("android.resource://${context.packageName}/${R.raw.mellow_chime}")
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CH_NOTION, "Notion updates & reminders", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Soft chime when a project is completed, goes to review, is reassigned, or a reminder is due"
                setSound(
                    sound,
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build(),
                )
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 120, 80, 120)
            }
        )
    }

    /** Notion change or reminder, with the mellow chime. */
    fun notion(context: Context, title: String, text: String) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        ensureNotionChannel(context)
        val n = NotificationCompat.Builder(context, CH_NOTION)
            .setSmallIcon(R.drawable.ic_stat_focus)
            .setColor(0xFF3B82F6.toInt())
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(openApp(context))
            .build()
        context.getSystemService(NotificationManager::class.java).notify(1000 + (title.hashCode() and 0xFFFF), n)
    }

    private fun openApp(context: Context): PendingIntent = PendingIntent.getActivity(
        context, 0,
        Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    fun session(context: Context, s: FocusState): Notification {
        val b = NotificationCompat.Builder(context, CH_SESSION)
            .setSmallIcon(R.drawable.ic_stat_focus)
            .setColor(0xFF3B82F6.toInt())
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_STOPWATCH)
            .setContentIntent(openApp(context))

        val t = s.timer
        val preset = t.preset
        when {
            s.phase == Phase.WARMUP -> b
                .setContentTitle("Focus Mode starts soon")
                .setContentText("Wrap up. Silence kicks in when the countdown ends.")
                .countdownTo(s.warmupEndsAt)
                .addAction(0, "Start now", Alarms.pending(context, FocusEngine.ACTION_SKIP_WARMUP))
                .addAction(0, "Cancel", Alarms.pending(context, FocusEngine.ACTION_STOP_FOCUS))

            preset != null && t.running -> b
                .setContentTitle("${preset.name} · Focused Up")
                .setContentText(if (s.phase == Phase.ACTIVE) "Do Not Disturb is on" else "Timer running")
                .countdownTo(t.endsAt)
                .addAction(0, "End session", Alarms.pending(context, FocusEngine.ACTION_END_TIMER))

            preset != null -> b
                .setContentTitle("${preset.name} · paused")
                .setContentText("${formatClock(t.pausedRemaining)} left")
                .setShowWhen(false)
                .addAction(0, "End session", Alarms.pending(context, FocusEngine.ACTION_END_TIMER))

            else -> b
                .setContentTitle("Focus Mode is on")
                .setContentText("Do Not Disturb · media stays on for editing")
                .setWhen(s.activeSince)
                .setShowWhen(true)
                .setUsesChronometer(true)
                .addAction(0, "Turn off", Alarms.pending(context, FocusEngine.ACTION_STOP_FOCUS))
        }
        return b.build()
    }

    fun sessionComplete(context: Context, title: String, text: String) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        ensureChannels(context)
        val n = NotificationCompat.Builder(context, CH_ALERTS)
            .setSmallIcon(R.drawable.ic_stat_focus)
            .setColor(0xFF3B82F6.toInt())
            .setContentTitle(title)
            .setContentText(text)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(openApp(context))
            .build()
        context.getSystemService(NotificationManager::class.java).notify(ID_DONE, n)
    }

    private fun NotificationCompat.Builder.countdownTo(at: Long) = this
        .setWhen(at)
        .setShowWhen(true)
        .setUsesChronometer(true)
        .setChronometerCountDown(true)
}
