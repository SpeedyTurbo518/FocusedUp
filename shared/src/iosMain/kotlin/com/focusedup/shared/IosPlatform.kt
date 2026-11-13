package com.focusedup.shared

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.focusedup.shared.engine.FocusEngine
import com.focusedup.shared.model.FocusState
import com.focusedup.shared.platform.Capability
import com.focusedup.shared.platform.FocusPlatform
import com.focusedup.shared.platform.KeyValueStore
import com.focusedup.shared.platform.MediaKind
import com.focusedup.shared.platform.PickedFile
import com.focusedup.shared.ui.FColors
import com.focusedup.shared.ui.GlassCard
import com.focusedup.shared.ui.PillButton
import com.focusedup.shared.ui.SectionLabel
import kotlinx.datetime.Clock
import platform.Foundation.NSUserDefaults
import platform.UserNotifications.UNAuthorizationOptionAlert
import platform.UserNotifications.UNAuthorizationOptionSound
import platform.UserNotifications.UNMutableNotificationContent
import platform.UserNotifications.UNNotificationRequest
import platform.UserNotifications.UNNotificationSound
import platform.UserNotifications.UNTimeIntervalNotificationTrigger
import platform.UserNotifications.UNUserNotificationCenter

private class DefaultsStore : KeyValueStore {
    private val defaults = NSUserDefaults.standardUserDefaults
    override fun get(key: String): String? = defaults.stringForKey(key)
    override fun put(key: String, value: String) = defaults.setObject(value, forKey = key)
}

/**
 * iPhone: Apple doesn't let apps turn on Focus or silence the phone, so Focus Mode here
 * is the timer + tasks + calendar, with a reminder to switch on a Focus yourself.
 * The end-of-session chime is a scheduled local notification, so it fires even if the app is closed.
 */
class IosPlatform : FocusPlatform {
    override val store: KeyValueStore = DefaultsStore()
    override val dnd = Capability("Focus", "", "Turn on a Focus in Control Center", supported = false)
    override val silence = Capability("Silent mode", "", "Use the Ring/Silent switch or Action button", supported = false)

    private val center get() = UNUserNotificationCenter.currentNotificationCenter()

    override fun now(): Long = Clock.System.now().toEpochMilliseconds()
    override fun enterFocus() = Unit
    override fun exitFocus() = Unit
    override fun keepFocus() = Unit
    override fun onStateChanged(state: FocusState) = Unit
    override fun sessionComplete(title: String, text: String) = Unit

    fun requestNotifications() {
        center.requestAuthorizationWithOptions(UNAuthorizationOptionAlert or UNAuthorizationOptionSound) { _, _ -> }
    }

    override fun schedule(action: String, atMillis: Long) {
        if (action != FocusEngine.ACTION_TIMER_DONE) return
        val seconds = (atMillis - now()) / 1000.0
        if (seconds < 1.0) return
        val content = UNMutableNotificationContent()
        content.setTitle("Focused Up session complete")
        content.setBody("Nice work. Your session is logged to the calendar.")
        content.setSound(UNNotificationSound.defaultSound)
        val trigger = UNTimeIntervalNotificationTrigger.triggerWithTimeInterval(seconds, repeats = false)
        val request = UNNotificationRequest.requestWithIdentifier(action, content, trigger)
        center.addNotificationRequest(request, withCompletionHandler = null)
    }

    override fun cancel(action: String) {
        center.removePendingNotificationRequestsWithIdentifiers(listOf(action))
    }

    @Composable
    override fun setupWarning(): String? = null

    @Composable
    override fun NotionLoginScreen(onResult: (String?) -> Unit) = onResult(null)

    // Photo and music pickers on iPhone come in a later update.
    @Composable
    override fun rememberPicker(kind: MediaKind, onPicked: (PickedFile) -> Unit): () -> Unit = {}

    @Composable
    override fun SetupSection() {
        SectionLabel("iPhone")
        GlassCard(Modifier.fillMaxWidth(), padding = 16.dp) {
            Text(
                "Apple doesn't allow apps to turn on Focus or silence your iPhone. When you start a session, " +
                    "swipe down to Control Center and turn on a Focus (like Do Not Disturb or Work).",
                color = FColors.TextSecondary, fontSize = 14.sp, lineHeight = 20.sp,
            )
            Spacer(Modifier.height(12.dp))
            PillButton("Allow session-complete alerts", primary = false, icon = Icons.Rounded.Notifications) {
                requestNotifications()
            }
        }
    }
}
