package com.focusedup.desktop

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import com.focusedup.shared.platform.MediaKind
import com.focusedup.shared.platform.PickedFile
import javazoom.jl.decoder.Bitstream
import javazoom.jl.decoder.Decoder
import javazoom.jl.decoder.SampleBuffer
import java.awt.FileDialog
import java.awt.Frame
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.FloatControl
import javax.sound.sampled.SourceDataLine
import kotlin.math.log10

private val IMAGE_EXTS = listOf("jpg", "jpeg", "png", "webp", "bmp", "gif")
private val AUDIO_EXTS = listOf("mp3", "wav", "aiff", "aif", "au")

object DesktopFiles {
    /** Opens the system file chooser and copies the chosen file into the app-data folder. */
    fun pick(kind: MediaKind): PickedFile? {
        val exts = if (kind == MediaKind.Image) IMAGE_EXTS else AUDIO_EXTS
        val dialog = FileDialog(null as Frame?, if (kind == MediaKind.Image) "Choose a photo" else "Choose prayer music (MP3 or WAV)", FileDialog.LOAD)
        dialog.file = exts.joinToString(";") { "*.$it" } // Windows uses this as the file-type filter
        dialog.setFilenameFilter { _, name -> name.substringAfterLast('.').lowercase() in exts }
        dialog.isVisible = true
        val name = dialog.file ?: return null
        val dir = dialog.directory ?: return null
        val src = File(dir, name)
        val ext = src.extension.lowercase()
        if (!src.isFile || ext !in exts) return null
        return runCatching {
            val media = File(appDataDir(), "media").apply { mkdirs() }
            val prefix = if (kind == MediaKind.Image) "img" else "music"
            val out = File(media, "${prefix}_${System.currentTimeMillis()}.$ext")
            src.copyTo(out, overwrite = true)
            PickedFile(out.absolutePath, name)
        }.getOrNull()
    }

    fun loadImage(path: String): ImageBitmap? = runCatching {
        org.jetbrains.skia.Image.makeFromEncoded(File(path).readBytes()).toComposeImageBitmap()
    }.getOrNull()

    fun delete(path: String) {
        runCatching { File(path).takeIf { it.parentFile?.name == "media" }?.delete() }
    }
}

/**
 * Looping music player for prayer. MP3 is decoded with JLayer, WAV/AIFF with Java Sound.
 * Runs on its own thread; every play() starts a new "generation" so old threads stop by themselves.
 */
class DesktopMusic {
    @Volatile private var gen = 0
    @Volatile private var volume = 0.6f
    @Volatile private var line: SourceDataLine? = null
    @Volatile private var paused = false

    @Synchronized
    fun play(path: String, vol: Float) {
        stop()
        volume = vol
        paused = false
        val myGen = gen
        Thread({ loop(File(path), myGen) }, "prayer-music").apply { isDaemon = true }.start()
    }

    fun pause() {
        paused = true
        runCatching { line?.stop() }
    }

    fun resume() {
        paused = false
        runCatching { line?.start() }
    }

    @Synchronized
    fun stop() {
        gen++
        paused = false
        line?.let { l -> runCatching { l.stop(); l.flush(); l.close() } }
        line = null
    }

    fun setVolume(v: Float) {
        volume = v
        line?.let(::applyVolume)
    }

    private fun loop(file: File, myGen: Int) {
        while (gen == myGen) {
            val played = runCatching {
                if (file.extension.lowercase() == "mp3") playMp3(file, myGen) else playPcm(file, myGen)
            }.getOrDefault(false)
            if (!played) break
        }
    }

    private fun openLine(format: AudioFormat, myGen: Int): SourceDataLine? {
        if (gen != myGen) return null
        val l = AudioSystem.getSourceDataLine(format)
        l.open(format)
        applyVolume(l)
        if (!paused) l.start()
        line = l
        return l
    }

    private fun closeLine(l: SourceDataLine?, myGen: Int) {
        if (l == null) return
        if (gen == myGen) runCatching { l.drain() }
        runCatching { l.stop(); l.close() }
        if (line === l) line = null
    }

    private fun waitWhilePaused(myGen: Int) {
        while (paused && gen == myGen) Thread.sleep(40)
    }

    private fun playMp3(file: File, myGen: Int): Boolean {
        val bitstream = Bitstream(BufferedInputStream(FileInputStream(file)))
        val decoder = Decoder()
        var l: SourceDataLine? = null
        var frames = 0
        try {
            while (gen == myGen) {
                waitWhilePaused(myGen)
                val header = bitstream.readFrame() ?: break
                val out = decoder.decodeFrame(header, bitstream) as SampleBuffer
                if (l == null) {
                    l = openLine(AudioFormat(out.sampleFrequency.toFloat(), 16, out.channelCount, true, false), myGen) ?: break
                }
                val n = out.bufferLength
                val samples = out.buffer
                val bytes = ByteArray(n * 2)
                for (i in 0 until n) {
                    val s = samples[i].toInt()
                    bytes[2 * i] = (s and 0xFF).toByte()
                    bytes[2 * i + 1] = ((s shr 8) and 0xFF).toByte()
                }
                l.write(bytes, 0, bytes.size)
                bitstream.closeFrame()
                frames++
            }
        } finally {
            runCatching { bitstream.close() }
            closeLine(l, myGen)
        }
        return frames > 0
    }

    private fun playPcm(file: File, myGen: Int): Boolean {
        var stream = AudioSystem.getAudioInputStream(file)
        val src = stream.format
        if (src.encoding != AudioFormat.Encoding.PCM_SIGNED && src.encoding != AudioFormat.Encoding.PCM_UNSIGNED) {
            val target = AudioFormat(AudioFormat.Encoding.PCM_SIGNED, src.sampleRate, 16, src.channels, src.channels * 2, src.sampleRate, false)
            stream = AudioSystem.getAudioInputStream(target, stream)
        }
        var l: SourceDataLine? = null
        var total = 0L
        try {
            l = openLine(stream.format, myGen) ?: return false
            val buf = ByteArray(16 * 1024)
            while (gen == myGen) {
                waitWhilePaused(myGen)
                val n = stream.read(buf)
                if (n <= 0) break
                l.write(buf, 0, n)
                total += n
            }
        } finally {
            runCatching { stream.close() }
            closeLine(l, myGen)
        }
        return total > 0
    }

    private fun applyVolume(l: SourceDataLine) {
        runCatching {
            val c = l.getControl(FloatControl.Type.MASTER_GAIN) as FloatControl
            val v = volume.coerceIn(0f, 1f)
            c.value = if (v <= 0.001f) c.minimum else (20f * log10(v)).coerceIn(c.minimum, c.maximum)
        }
    }
}
