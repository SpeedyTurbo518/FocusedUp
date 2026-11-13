package com.focusedup.shared.platform

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.ImageBitmap
import com.focusedup.shared.model.FocusState

/** Tiny persistent key/value store. Each platform saves it on-device (offline). */
interface KeyValueStore {
    fun get(key: String): String?
    fun put(key: String, value: String)
}

/** What a platform can (or can't) do for one Focus Mode feature, with the words to show. */
data class Capability(
    val title: String,
    val onText: String,
    val offText: String,
    val supported: Boolean,
)

enum class MediaKind { Image, Audio }

/** A file the user picked, copied into the app's own storage. */
data class PickedFile(val path: String, val name: String)

data class HttpResult(val code: Int, val body: String)

/**
 * Everything that differs between Android, Windows, Mac and iPhone.
 * The shared engine and UI only talk to the device through this.
 */
interface FocusPlatform {
    val store: KeyValueStore
    val dnd: Capability
    val silence: Capability

    fun now(): Long

    /** Focus Mode switched on: silence the device. */
    fun enterFocus()

    /** Focus Mode switched off: put sound back exactly how it was. */
    fun exitFocus()

    /** Called every ~20 s while Focus Mode is on (desktop re-mutes newly opened apps). */
    fun keepFocus()

    /** Wake up at [atMillis] even if the app is in the background, where supported. */
    fun schedule(action: String, atMillis: Long)
    fun cancel(action: String)

    fun onStateChanged(state: FocusState)
    fun sessionComplete(title: String, text: String)

    /** Short message when permissions are missing, or null when everything is ready. */
    @Composable
    fun setupWarning(): String?

    /** Platform-specific block in the Setup tab (permissions, app lists...). */
    @Composable
    fun SetupSection()

    // ---------------- Media (photos, prayer music) ----------------

    /** Returns a function that opens the system file picker. The chosen file is copied into app storage. */
    @Composable
    fun rememberPicker(kind: MediaKind, onPicked: (PickedFile) -> Unit): () -> Unit

    fun loadImage(path: String): ImageBitmap? = null
    fun deleteFile(path: String) = Unit

    /** Looping background music for prayer. [volume] is 0..1. */
    fun playMusic(path: String, volume: Float) = Unit
    fun pauseMusic() = Unit
    fun resumeMusic() = Unit
    fun stopMusic() = Unit
    fun setMusicVolume(volume: Float) = Unit

    // ---------------- Network, browser, sharing ----------------

    /** HTTPS request (runs off the main thread). Returns code 0 when there is no connection. */
    suspend fun http(method: String, url: String, headers: Map<String, String>, body: String?): HttpResult = HttpResult(0, "")

    /**
     * Opens [authUrl] in the browser and waits for it to redirect to http://localhost:[port]/...
     * Returns the redirect's query parameters, or null if cancelled / timed out.
     */
    suspend fun awaitOAuthRedirect(authUrl: String, port: Int): Map<String, String>? = null

    fun openUrl(url: String) = Unit

    /** Share text (Android share sheet, desktop clipboard). Returns a message to show, if any. */
    fun shareText(text: String): String? = null

    /** Desktop: read a link the user copied (to import a shared profile). */
    fun readClipboard(): String? = null
    val pastesLinks: Boolean get() = false

    // ---------------- Notion sign-in (user's own login) ----------------

    /** True where the app can show Notion's sign-in page inside itself (Android). */
    val supportsNotionLogin: Boolean get() = false

    /** Notion's real sign-in page. Calls [onResult] with the session, or null if closed. */
    @Composable
    fun NotionLoginScreen(onResult: (String?) -> Unit)

    /** Forget the Notion sign-in on this device. */
    fun clearNotionSession() = Unit

    // ---------------- Notion alerts and reminders ----------------

    /** Notification with the soft chime (Notion changes, reminders). */
    fun notionAlert(title: String, text: String) = sessionComplete(title, text)

    /** True when the platform fires reminders itself even if the app is closed (Android alarms). */
    val remindersInBackground: Boolean get() = false
    fun scheduleReminder(r: com.focusedup.shared.model.Reminder) = Unit
    fun cancelReminder(id: String) = Unit

    /** Switch the home-screen / taskbar icon. Desktop redraws its window icon from the shared state. */
    fun applyAppIcon(color: Int, style: Int) = Unit
    val iconNote: String get() = ""
}
