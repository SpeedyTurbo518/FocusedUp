package com.focusedup.desktop

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Notification
import androidx.compose.ui.window.TrayState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.ImageBitmap
import com.focusedup.shared.data.AppSettings
import com.focusedup.shared.model.FocusState
import com.focusedup.shared.platform.Capability
import com.focusedup.shared.platform.FocusPlatform
import com.focusedup.shared.platform.KeyValueStore
import com.focusedup.shared.platform.MediaKind
import com.focusedup.shared.platform.PickedFile
import com.focusedup.shared.ui.FColors
import com.focusedup.shared.ui.FSwitch
import com.focusedup.shared.ui.GlassCard
import com.focusedup.shared.ui.Hairline
import com.focusedup.shared.ui.PillButton
import com.focusedup.shared.ui.SectionLabel
import com.focusedup.shared.ui.fieldColors
import java.awt.Desktop
import java.awt.Toolkit
import java.io.File
import java.net.URI
import java.util.Properties
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

private val OS = System.getProperty("os.name").lowercase()
val IS_WINDOWS = OS.contains("win")
val IS_MAC = OS.contains("mac")

/** Adobe Creative Cloud (and Focused Up itself) keep their sound during Focus Mode. */
const val DEFAULT_KEEP_APPS =
    "adobe, photoshop, afterfx, illustrator, indesign, audition, animate, lightroom, premiere, " +
        "bridge, dreamweaver, character animator, media encoder, creative cloud, focusedup"

fun appDataDir(): File {
    val home = System.getProperty("user.home")
    val dir = when {
        IS_WINDOWS -> File(System.getenv("APPDATA") ?: "$home\\AppData\\Roaming", "FocusedUp")
        IS_MAC -> File(home, "Library/Application Support/FocusedUp")
        else -> File(home, ".focusedup")
    }
    dir.mkdirs()
    return dir
}

/** Settings file in the user's app-data folder. Fully offline. */
class DesktopStore(dir: File) : KeyValueStore {
    private val file = File(dir, "focusedup.properties")
    private val props = Properties().apply {
        if (file.exists()) runCatching { file.inputStream().use { load(it) } }
    }

    @Synchronized
    override fun get(key: String): String? = props.getProperty(key)

    @Synchronized
    override fun put(key: String, value: String) {
        props.setProperty(key, value)
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.outputStream().use { props.store(it, "Focused Up") }
        if (!tmp.renameTo(file)) {
            file.delete()
            tmp.renameTo(file)
        }
    }
}

/** Calls the bundled FocusAudio.exe helper to mute/unmute other apps' audio sessions. */
class WindowsAudio(private val dir: File) {
    private val exe = File(dir, "FocusAudio.exe")
    private val stateFile = File(dir, "muted-sessions.txt")

    val hasLeftovers: Boolean get() = stateFile.exists()

    private fun ensureExe(): Boolean {
        val bytes = javaClass.getResourceAsStream("/windows/FocusAudio.exe")?.use { it.readBytes() } ?: return false
        if (!exe.exists() || exe.length() != bytes.size.toLong()) runCatching { exe.writeBytes(bytes) }
        return exe.exists()
    }

    fun mute(keep: String): Boolean = run("mute", stateFile.absolutePath, keep.replace(" ", ""))
    fun restore(): Boolean = if (stateFile.exists()) run("restore", stateFile.absolutePath) else true

    private fun run(vararg args: String): Boolean = runCatching {
        if (!ensureExe()) return false
        val p = ProcessBuilder(listOf(exe.absolutePath) + args).redirectErrorStream(true).start()
        p.inputStream.readBytes()
        p.waitFor(20, TimeUnit.SECONDS) && p.exitValue() == 0
    }.getOrDefault(false)
}

class DesktopPlatform : FocusPlatform {
    private val dir = appDataDir()
    override val store: KeyValueStore = DesktopStore(dir)
    private val audio = if (IS_WINDOWS) WindowsAudio(dir) else null
    private val worker = Executors.newSingleThreadExecutor { r -> Thread(r, "focus-audio").apply { isDaemon = true } }

    /** Set by main() once the tray icon exists. */
    var tray: TrayState? = null

    var keepApps: String
        get() = store.get("keep_apps") ?: DEFAULT_KEEP_APPS
        set(v) = store.put("keep_apps", v)

    override val dnd = when {
        IS_WINDOWS -> Capability("Do Not Disturb", "", "Turn on in Windows notifications (see Setup)", supported = false)
        IS_MAC -> Capability("Focus", "", "Turn on a Focus in Control Center", supported = false)
        else -> Capability("Do Not Disturb", "", "Not available on this system", supported = false)
    }
    override val silence = if (IS_WINDOWS) {
        Capability("App sounds", "Muted · Adobe apps stay on", "Normal", supported = true)
    } else {
        Capability("App sounds", "", "macOS doesn't let apps mute other apps", supported = false)
    }

    override fun now(): Long = System.currentTimeMillis()

    override fun enterFocus() {
        val keep = keepApps
        audio?.let { a -> worker.execute { a.mute(keep) } }
    }

    override fun exitFocus() {
        audio?.let { a -> worker.execute { a.restore() } }
    }

    /** Mutes apps opened after Focus Mode started. */
    override fun keepFocus() = enterFocus()

    /** Blocking restore for quitting or startup clean-up. */
    fun restoreNow() {
        val a = audio ?: return
        runCatching { worker.submit { a.restore() }.get(20, TimeUnit.SECONDS) }
    }

    val hasLeftoverMutes: Boolean get() = audio?.hasLeftovers == true

    // The desktop app keeps the timer running while it's open (or hidden in the tray).
    override fun schedule(action: String, atMillis: Long) = Unit
    override fun cancel(action: String) = Unit
    override fun onStateChanged(state: FocusState) = Unit

    override fun sessionComplete(title: String, text: String) {
        runCatching { Toolkit.getDefaultToolkit().beep() }
        tray?.sendNotification(Notification(title, text, Notification.Type.Info))
    }

    @Composable
    override fun setupWarning(): String? = null

    // ---------------- Media ----------------

    private val music = DesktopMusic()

    @Composable
    override fun rememberPicker(kind: MediaKind, onPicked: (PickedFile) -> Unit): () -> Unit {
        val callback by rememberUpdatedState(onPicked)
        return remember(kind) { { DesktopFiles.pick(kind)?.let { callback(it) } } }
    }

    override fun loadImage(path: String): ImageBitmap? = DesktopFiles.loadImage(path)
    override fun deleteFile(path: String) = DesktopFiles.delete(path)
    override fun playMusic(path: String, volume: Float) = music.play(path, volume)
    override fun pauseMusic() = music.pause()
    override fun resumeMusic() = music.resume()
    override fun stopMusic() = music.stop()
    override fun setMusicVolume(volume: Float) = music.setVolume(volume)

    // ---------------- Network, browser, sharing ----------------

    override suspend fun http(method: String, url: String, headers: Map<String, String>, body: String?) = JvmNet.http(method, url, headers, body)
    override suspend fun awaitOAuthRedirect(authUrl: String, port: Int) = JvmNet.awaitRedirect(port) { openUrl(authUrl) }

    override fun openUrl(url: String) {
        runCatching { Desktop.getDesktop().browse(URI(url)) }
    }

    override fun shareText(text: String): String {
        runCatching {
            Toolkit.getDefaultToolkit().systemClipboard.setContents(java.awt.datatransfer.StringSelection(text), null)
        }
        return "Profile link copied. Paste it into a message."
    }

    override fun readClipboard(): String? = runCatching {
        Toolkit.getDefaultToolkit().systemClipboard.getData(java.awt.datatransfer.DataFlavor.stringFlavor) as? String
    }.getOrNull()

    override val pastesLinks: Boolean get() = true

    @Composable
    override fun NotionLoginScreen(onResult: (String?) -> Unit) {
        LaunchedEffect(Unit) { onResult(null) }
    }

    /** Soft two-note chime plus a tray notification. Reminders fire while the app is open or in the tray. */
    override fun notionAlert(title: String, text: String) {
        playChime()
        tray?.sendNotification(Notification(title, text, Notification.Type.None))
    }

    private fun playChime() {
        runCatching {
            val bytes = javaClass.getResourceAsStream("/mellow_chime.wav")?.use { it.readBytes() } ?: return
            val stream = javax.sound.sampled.AudioSystem.getAudioInputStream(java.io.ByteArrayInputStream(bytes))
            val clip = javax.sound.sampled.AudioSystem.getClip()
            clip.open(stream)
            clip.addLineListener { e -> if (e.type == javax.sound.sampled.LineEvent.Type.STOP) clip.close() }
            clip.start()
        }
    }

    // ---------------- Full screen ----------------

    private val settings = AppSettings(store)

    /** Window full-screen state; main() watches this. */
    val fullscreen = mutableStateOf(settings.startFullscreen)

    @Composable
    override fun SetupSection() {
        DisplaySetup()
        if (IS_WINDOWS) WindowsSetup() else MacSetup()
    }

    @Composable
    private fun DisplaySetup() {
        var startFull by remember { mutableStateOf(settings.startFullscreen) }
        SectionLabel("Display")
        GlassCard(Modifier.fillMaxWidth(), padding = 16.dp) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f).padding(end = 12.dp)) {
                    Text("Full screen", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                    Text("Press F11 to switch any time, Esc to leave.", color = FColors.TextSecondary, fontSize = 13.sp)
                }
                FSwitch(fullscreen.value) { fullscreen.value = it }
            }
            Spacer(Modifier.height(10.dp))
            Hairline(0.dp)
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f).padding(end = 12.dp)) {
                    Text("Open in full screen", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                    Text("Start Focused Up full screen every time.", color = FColors.TextSecondary, fontSize = 13.sp)
                }
                FSwitch(startFull) { startFull = it; settings.startFullscreen = it }
            }
        }
    }

    @Composable
    private fun WindowsSetup() {
        var keep by remember { mutableStateOf(keepApps) }

        SectionLabel("Apps that stay audible")
        GlassCard(Modifier.fillMaxWidth(), padding = 16.dp) {
            Text(
                "During Focus Mode, every app is muted except these (separate with commas).",
                color = FColors.TextSecondary, fontSize = 14.sp, lineHeight = 20.sp,
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = keep,
                onValueChange = { keep = it; keepApps = it },
                minLines = 3,
                colors = fieldColors(),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Reset to Adobe defaults",
                color = FColors.BlueBright, fontSize = 13.sp,
                modifier = Modifier.clickable { keep = DEFAULT_KEEP_APPS; keepApps = DEFAULT_KEEP_APPS },
            )
        }

        SectionLabel("Do Not Disturb")
        GlassCard(Modifier.fillMaxWidth(), padding = 16.dp) {
            Text(
                "Windows doesn't let apps turn this on. Use Win + N, or:",
                color = FColors.TextSecondary, fontSize = 14.sp, lineHeight = 20.sp,
            )
            Spacer(Modifier.height(12.dp))
            PillButton("Open notification settings", primary = false, icon = Icons.Rounded.Settings) {
                openUri("ms-settings:notifications")
            }
        }
    }

    @Composable
    private fun MacSetup() {
        SectionLabel("Focus on Mac")
        GlassCard(Modifier.fillMaxWidth(), padding = 16.dp) {
            Text("Timer, tasks and calendar work fully on Mac.", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(6.dp))
            Text(
                "macOS doesn't allow apps to turn on Focus or mute other apps. Turn on a Focus from Control Center " +
                    "when you start a session; your Adobe apps keep playing.",
                color = FColors.TextSecondary, fontSize = 14.sp, lineHeight = 20.sp,
            )
            Spacer(Modifier.height(12.dp))
            PillButton("Open Focus settings", primary = false, icon = Icons.Rounded.Settings) {
                openUri("x-apple.systempreferences:com.apple.Focus-Settings.extension")
            }
        }
    }

    private fun openUri(uri: String) {
        runCatching { Desktop.getDesktop().browse(URI(uri)) }
    }
}
