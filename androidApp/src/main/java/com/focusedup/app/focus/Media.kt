package com.focusedup.app.focus

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.focusedup.shared.platform.MediaKind
import com.focusedup.shared.platform.PickedFile
import java.io.File

object MediaFiles {
    /** Copies a picked file into the app's private storage so it stays available. */
    fun importUri(context: Context, uri: Uri, kind: MediaKind): PickedFile? = runCatching {
        val resolver = context.contentResolver
        var name: String? = null
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) name = c.getString(0)
        }
        val ext = name?.substringAfterLast('.', "")?.takeIf { it.isNotEmpty() && it.length <= 5 }
            ?: MimeTypeMap.getSingleton().getExtensionFromMimeType(resolver.getType(uri))
            ?: if (kind == MediaKind.Image) "jpg" else "mp3"
        val dir = File(context.filesDir, "media").apply { mkdirs() }
        val prefix = if (kind == MediaKind.Image) "img" else "music"
        val out = File(dir, "${prefix}_${System.currentTimeMillis()}.$ext")
        resolver.openInputStream(uri)!!.use { input -> out.outputStream().use { input.copyTo(it) } }
        PickedFile(out.absolutePath, name ?: out.name)
    }.getOrNull()

    /** Decodes a photo, scaled down so big camera pictures don't use too much memory. */
    fun loadImage(path: String, maxSide: Int = 1600): ImageBitmap? = runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= maxSide || bounds.outHeight / (sample * 2) >= maxSide) sample *= 2
        BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })?.asImageBitmap()
    }.getOrNull()
}

/** Looping prayer music. Plays as media, so Do Not Disturb doesn't silence it. */
class MusicPlayer(private val context: Context) {
    private var player: MediaPlayer? = null

    fun play(path: String, volume: Float) {
        stop()
        player = runCatching {
            MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                setDataSource(path)
                isLooping = true
                setVolume(volume, volume)
                prepare()
                start()
            }
        }.getOrNull()
    }

    fun pause() {
        runCatching { player?.takeIf { it.isPlaying }?.pause() }
    }

    fun resume() {
        runCatching { player?.start() }
    }

    fun stop() {
        runCatching { player?.stop() }
        runCatching { player?.release() }
        player = null
    }

    fun setVolume(v: Float) {
        runCatching { player?.setVolume(v, v) }
    }
}

/**
 * Home-screen icon choices. Each one is an <activity-alias> in the manifest;
 * exactly one is enabled at a time.
 */
object AppIcons {
    private val COLORS = listOf("Blue", "Red", "Green", "Purple", "Gold", "Silver")
    private val STYLES = listOf("Glow", "Solid", "Outline")

    private fun alias(context: Context, color: Int, style: Int) =
        ComponentName(context.packageName, "com.focusedup.app.Icon${COLORS[color]}${STYLES[style]}")

    fun apply(context: Context, color: Int, style: Int) {
        val c = color.coerceIn(COLORS.indices)
        val s = style.coerceIn(STYLES.indices)
        val pm = context.packageManager
        val target = alias(context, c, s)
        // Enable the new icon first so the app always has a launcher entry.
        pm.setComponentEnabledSetting(target, PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP)
        for (ci in COLORS.indices) for (si in STYLES.indices) {
            val other = alias(context, ci, si)
            if (other != target) {
                pm.setComponentEnabledSetting(other, PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP)
            }
        }
    }
}
