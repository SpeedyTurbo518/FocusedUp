package com.focusedup.app.focus

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DoNotDisturbOn
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.focusedup.shared.engine.FocusEngine
import com.focusedup.shared.model.FocusState
import com.focusedup.shared.platform.Capability
import com.focusedup.shared.platform.FocusPlatform
import com.focusedup.shared.platform.KeyValueStore
import com.focusedup.shared.platform.MediaKind
import com.focusedup.shared.platform.PickedFile
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.graphics.ImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import com.focusedup.shared.ui.FColors
import com.focusedup.shared.ui.GlassCard
import com.focusedup.shared.ui.Hairline
import com.focusedup.shared.ui.PermRow
import com.focusedup.shared.ui.SectionLabel

/** One engine per app process, shared by the screen, the service and alarm wake-ups. */
object Graph {
    @Volatile private var engine: FocusEngine? = null

    fun engine(context: Context): FocusEngine = engine ?: synchronized(this) {
        engine ?: FocusEngine(AndroidPlatform(context.applicationContext)).also { engine = it }
    }
}

private class PrefsStore(context: Context) : KeyValueStore {
    private val sp = context.getSharedPreferences("focused_up_v2", Context.MODE_PRIVATE)
    override fun get(key: String): String? = sp.getString(key, null)
    override fun put(key: String, value: String) {
        sp.edit().putString(key, value).apply()
    }
}

class AndroidPlatform(private val context: Context) : FocusPlatform {
    override val store: KeyValueStore = PrefsStore(context)

    override val dnd = Capability("Do Not Disturb", "On · alarms & media only", "Off", supported = true)
    override val silence = Capability("Ringer & alerts", "Silenced · media stays on for Adobe apps", "Normal", supported = true)

    override fun now(): Long = System.currentTimeMillis()
    override fun enterFocus() = DeviceControl.enterFocus(context)
    override fun exitFocus() = DeviceControl.exitFocus(context)
    override fun keepFocus() = Unit
    override fun schedule(action: String, atMillis: Long) = Alarms.schedule(context, action, atMillis)
    override fun cancel(action: String) = Alarms.cancel(context, action)
    override fun onStateChanged(state: FocusState) = FocusService.sync(context, state)
    override fun sessionComplete(title: String, text: String) = Notifications.sessionComplete(context, title, text)

    // ---------------- Media ----------------

    private val music = MusicPlayer(context)

    @Composable
    override fun rememberPicker(kind: MediaKind, onPicked: (PickedFile) -> Unit): () -> Unit {
        val ctx = LocalContext.current
        val scope = rememberCoroutineScope()
        val callback by rememberUpdatedState(onPicked)
        val launcher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            if (uri != null) scope.launch {
                val file = withContext(Dispatchers.IO) { MediaFiles.importUri(ctx, uri, kind) }
                if (file != null) callback(file)
            }
        }
        return { launcher.launch(if (kind == MediaKind.Image) "image/*" else "audio/*") }
    }

    override fun loadImage(path: String): ImageBitmap? = MediaFiles.loadImage(path)
    override fun deleteFile(path: String) {
        runCatching { File(path).takeIf { it.parentFile?.name == "media" }?.delete() }
    }

    override fun playMusic(path: String, volume: Float) = music.play(path, volume)
    override fun pauseMusic() = music.pause()
    override fun resumeMusic() = music.resume()
    override fun stopMusic() = music.stop()
    override fun setMusicVolume(volume: Float) = music.setVolume(volume)

    override suspend fun http(method: String, url: String, headers: Map<String, String>, body: String?) = JvmNet.http(method, url, headers, body)

    override suspend fun awaitOAuthRedirect(authUrl: String, port: Int) = JvmNet.awaitRedirect(port) { openUrl(authUrl) }

    override fun openUrl(url: String) {
        runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    override fun shareText(text: String): String? {
        val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
        context.startActivity(Intent.createChooser(send, "Share your profile").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        return null
    }

    override val supportsNotionLogin: Boolean get() = true

    @Composable
    override fun NotionLoginScreen(onResult: (String?) -> Unit) = NotionLoginDialog(onResult)

    override fun clearNotionSession() = clearNotionCookies()

    override fun notionAlert(title: String, text: String) = Notifications.notion(context, title, text)
    override val remindersInBackground: Boolean get() = true
    override fun scheduleReminder(r: com.focusedup.shared.model.Reminder) = Reminders.schedule(context, r)
    override fun cancelReminder(id: String) = Reminders.cancel(context, id)

    override fun applyAppIcon(color: Int, style: Int) = AppIcons.apply(context, color, style)
    override val iconNote: String
        get() = "Changes the icon on your home screen and app drawer. Some launchers take a few seconds to update."

    @Composable
    override fun setupWarning(): String? {
        val perms by rememberPerms()
        return if (!perms.dnd || !perms.notifications) "Allow Do Not Disturb access and notifications." else null
    }

    @Composable
    override fun SetupSection() {
        val ctx = LocalContext.current
        val permsState = rememberPerms()
        val perms = permsState.value
        val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
            permsState.value = readPerms(ctx)
        }

        SectionLabel("Permissions")
        GlassCard(Modifier.fillMaxWidth(), padding = 16.dp) {
            PermRow(Icons.Rounded.DoNotDisturbOn, "Do Not Disturb access", "Find Focused Up and switch it on", perms.dnd) {
                ctx.startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
            }
            if (Build.VERSION.SDK_INT >= 33) {
                Hairline()
                PermRow(Icons.Rounded.Notifications, "Notifications", "Live countdown & session-complete chime", perms.notifications) {
                    launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        }

        SectionLabel("About sound")
        GlassCard(Modifier.fillMaxWidth(), padding = 16.dp) {
            Text(
                "Focus Mode turns on Do Not Disturb (alarms & media only) and silences ringtone, notification " +
                    "and system sounds. Media volume stays on, so Adobe apps (Premiere Rush, Lightroom, " +
                    "Photoshop Express) and editing playback stay audible. Android doesn't allow muting " +
                    "individual apps.",
                color = FColors.TextSecondary, fontSize = 14.sp, lineHeight = 20.sp,
            )
        }
    }
}

// ---------------- Permission helpers ----------------

data class PermStatus(val dnd: Boolean, val notifications: Boolean)

fun readPerms(context: Context) = PermStatus(
    dnd = DeviceControl.hasDndAccess(context),
    notifications = Build.VERSION.SDK_INT < 33 ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED,
)

/** Permission status that refreshes whenever you come back to the app (e.g. from Settings). */
@Composable
fun rememberPerms(): MutableState<PermStatus> {
    val context = LocalContext.current
    val state = remember { mutableStateOf(readPerms(context)) }
    val owner = LocalLifecycleOwner.current
    val refresh by rememberUpdatedState { state.value = readPerms(context) }
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) refresh() }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    return state
}
