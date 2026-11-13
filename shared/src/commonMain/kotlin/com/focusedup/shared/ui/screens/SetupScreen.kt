package com.focusedup.shared.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.focusedup.shared.model.Appearance
import com.focusedup.shared.platform.MediaKind
import com.focusedup.shared.ui.ACCENTS
import com.focusedup.shared.ui.BASES
import com.focusedup.shared.ui.FColors
import com.focusedup.shared.ui.FSwitch
import com.focusedup.shared.ui.FocusLogo
import com.focusedup.shared.ui.GlassCard
import com.focusedup.shared.ui.Hairline
import com.focusedup.shared.ui.ICON_COLORS
import com.focusedup.shared.ui.ICON_STYLES
import com.focusedup.shared.ui.IconTile
import com.focusedup.shared.ui.LocalEngine
import com.focusedup.shared.ui.PillButton
import com.focusedup.shared.util.cleanSongName
import com.focusedup.shared.ui.ScreenHeader
import com.focusedup.shared.ui.SectionLabel
import com.focusedup.shared.ui.adjust
import com.focusedup.shared.ui.buildPalette

@Composable
fun SetupScreen(onToast: (String) -> Unit) {
    val engine = LocalEngine.current
    var warmup by remember { mutableIntStateOf(engine.settings.warmupMinutes) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(bottom = 120.dp),
    ) {
        ScreenHeader("Setup", "App appearance")

        AppColorsSection()
        AppIconSection(onToast)
        PrayerMusicSection()

        engine.platform.SetupSection()

        SectionLabel("Countdown before silence")
        GlassCard(Modifier.fillMaxWidth(), padding = 20.dp) {
            Segmented(listOf("None", "1 min", "2 min", "5 min"), listOf(0, 1, 2, 5).indexOf(warmup)) { i ->
                val m = listOf(0, 1, 2, 5)[i]
                warmup = m
                engine.settings.warmupMinutes = m
            }
        }

        SectionLabel("About")
        GlassCard(Modifier.fillMaxWidth(), padding = 20.dp) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                FocusLogo(52.dp)
                Spacer(Modifier.width(16.dp))
                Column {
                    Text("Focused Up 2.2", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                    Text("Made by STG Studios", color = FColors.TextSecondary, fontSize = 14.sp)
                    Text("A faith based company", color = FColors.BlueBright, fontSize = 13.sp)
                }
            }
            Spacer(Modifier.height(14.dp))
            Text(
                "Works fully offline. Your tasks, calendar, photos and music stay on this device.",
                color = FColors.TextTertiary, fontSize = 12.sp,
            )
        }
    }
}

// ---------------- App colors ----------------

@Composable
private fun AppColorsSection() {
    val engine = LocalEngine.current
    val a by engine.appearance.value.collectAsState()
    val update: ((Appearance) -> Appearance) -> Unit = { engine.appearance.update(it) }

    SectionLabel("App colors")
    GlassCard(Modifier.fillMaxWidth(), padding = 20.dp) {
        Text("Accent", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            ACCENTS.forEachIndexed { i, c ->
                Swatch(adjust(c.color, a.hue, a.saturation), selected = a.accent == i) { update { it.copy(accent = i) } }
            }
        }

        Spacer(Modifier.height(24.dp))
        Text("Background tone", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BASES.forEachIndexed { i, b ->
                val preview = buildPalette(a.copy(base = i))
                val sel = a.base == i
                Column(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(14.dp))
                        .border(if (sel) 2.dp else 1.dp, if (sel) FColors.BlueBright else Color.White.copy(alpha = 0.14f), RoundedCornerShape(14.dp))
                        .clickable { update { it.copy(base = i) } },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(Modifier.fillMaxWidth().height(40.dp).background(Brush.verticalGradient(listOf(preview.night, preview.black))))
                    Text(
                        b.name, color = if (sel) Color.White else FColors.TextSecondary, fontSize = 12.sp,
                        fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Normal,
                        modifier = Modifier.padding(vertical = 6.dp),
                    )
                }
            }
        }

        Spacer(Modifier.height(20.dp))
        var fineTune by remember { mutableStateOf(a.hue != 0f || a.contrast != 1f || a.saturation != 1f) }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Fine-tune hue, contrast & saturation", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
            FSwitch(fineTune) { fineTune = it }
        }
        if (fineTune) {
            Spacer(Modifier.height(8.dp))
            AdjustSlider("Hue", "${if (a.hue > 0) "+" else ""}${a.hue.toInt()}°", a.hue, -180f..180f, hueTrack = true) { v -> update { it.copy(hue = v) } }
            AdjustSlider("Color contrast", "${(a.contrast * 100).toInt()}%", a.contrast, 0.6f..1.4f) { v -> update { it.copy(contrast = v) } }
            AdjustSlider("Saturation", "${(a.saturation * 100).toInt()}%", a.saturation, 0f..1.6f) { v -> update { it.copy(saturation = v) } }
        }

        Spacer(Modifier.height(12.dp))
        Row(
            Modifier.clip(RoundedCornerShape(50)).clickable { update { it.copy(accent = 0, base = 0, hue = 0f, contrast = 1f, saturation = 1f) } }
                .padding(horizontal = 4.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            androidx.compose.material3.Icon(Icons.Rounded.Refresh, contentDescription = null, tint = FColors.BlueBright, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text("Reset colors", color = FColors.BlueBright, fontSize = 13.sp)
        }
    }
}

@Composable
private fun AdjustSlider(
    label: String,
    valueText: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    hueTrack: Boolean = false,
    onChange: (Float) -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(top = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
            Text(valueText, color = FColors.TextSecondary, fontSize = 13.sp)
        }
        if (hueTrack) {
            Spacer(Modifier.height(6.dp))
            Canvas(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(50))) {
                val base = ACCENTS[0].color
                drawRect(Brush.horizontalGradient((0..12).map { adjust(base, -180f + it * 30f, 1f) }))
            }
        }
        Slider(
            value = value,
            onValueChange = onChange,
            valueRange = range,
            colors = SliderDefaults.colors(
                thumbColor = Color.White,
                activeTrackColor = FColors.Blue,
                inactiveTrackColor = Color.White.copy(alpha = 0.12f),
            ),
        )
    }
}

// ---------------- App icon ----------------

@Composable
private fun AppIconSection(onToast: (String) -> Unit) {
    val engine = LocalEngine.current
    val platform = engine.platform
    val a by engine.appearance.value.collectAsState()
    var applied by remember { mutableStateOf(a.iconColor to a.iconStyle) }
    val pending = applied != (a.iconColor to a.iconStyle)

    SectionLabel("App icon")
    GlassCard(Modifier.fillMaxWidth(), padding = 20.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            FocusLogo(76.dp)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text("${ICON_COLORS[a.iconColor.coerceIn(ICON_COLORS.indices)].name} · ${ICON_STYLES[a.iconStyle.coerceIn(ICON_STYLES.indices)]}", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                Text(
                    platform.iconNote.ifEmpty { "Shown on the taskbar, window and tray right away." },
                    color = FColors.TextSecondary, fontSize = 13.sp,
                )
            }
        }

        Spacer(Modifier.height(24.dp))
        Text("Style", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            ICON_STYLES.forEachIndexed { i, name ->
                Column(
                    Modifier.clip(RoundedCornerShape(16.dp)).clickable { engine.appearance.update { it.copy(iconStyle = i) } }.padding(6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        Modifier.border(
                            2.dp,
                            if (a.iconStyle == i) FColors.BlueBright else Color.Transparent,
                            RoundedCornerShape(20.dp),
                        ).padding(4.dp),
                    ) { FocusLogo(54.dp, colorIndex = a.iconColor, style = i) }
                    Text(name, color = if (a.iconStyle == i) Color.White else FColors.TextSecondary, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
                }
            }
        }

        Spacer(Modifier.height(14.dp))
        Text("Color", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            ICON_COLORS.forEachIndexed { i, c ->
                Swatch(c.color, selected = a.iconColor == i, size = 40.dp) { engine.appearance.update { it.copy(iconColor = i) } }
            }
        }

        if (platform.iconNote.isNotEmpty()) {
            Spacer(Modifier.height(24.dp))
            PillButton(
                if (pending) "Set as home screen icon" else "Home screen icon is up to date",
                primary = pending,
                icon = Icons.Rounded.Check,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (pending) {
                    platform.applyAppIcon(a.iconColor, a.iconStyle)
                    applied = a.iconColor to a.iconStyle
                    onToast("Icon updated. Your home screen may take a moment to refresh.")
                }
            }
        } else {
            // Desktop applies instantly; keep the platform informed anyway.
            DisposableEffect(a.iconColor, a.iconStyle) {
                platform.applyAppIcon(a.iconColor, a.iconStyle)
                onDispose { }
            }
        }
    }
}

// ---------------- Prayer & music ----------------

@Composable
private fun PrayerMusicSection() {
    val engine = LocalEngine.current
    val settings = engine.settings
    var enabled by remember { mutableStateOf(settings.prayerEnabled) }
    var musicName by remember { mutableStateOf(settings.musicName) }
    var volume by remember { mutableFloatStateOf(settings.musicVolume) }
    var previewing by remember { mutableStateOf(false) }
    val pick = engine.platform.rememberPicker(MediaKind.Audio) { file ->
        previewing = false
        engine.setMusic(file.path, file.name)
        musicName = file.name
    }
    DisposableEffect(Unit) { onDispose { if (previewing) engine.previewMusic(false) } }

    SectionLabel("Prayer & music")
    GlassCard(Modifier.fillMaxWidth(), padding = 20.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).padding(end = 12.dp)) {
                Text("Start the day with prayer", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                Text("Once a day, after the splash.", color = FColors.TextSecondary, fontSize = 13.sp)
            }
            FSwitch(enabled) { enabled = it; settings.prayerEnabled = it }
        }
        Spacer(Modifier.height(10.dp))
        Hairline(0.dp)
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconTile(Icons.Rounded.LibraryMusic, active = musicName != null)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text("Background music", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                Text(musicName?.let(::cleanSongName) ?: "None chosen yet.", color = FColors.TextSecondary, fontSize = 13.sp, maxLines = 2)
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PillButton(if (musicName == null) "Choose music" else "Change", icon = Icons.Rounded.MusicNote, onClick = pick)
            if (musicName != null) {
                PillButton(if (previewing) "Stop" else "Preview", primary = false, icon = if (previewing) Icons.Rounded.Stop else Icons.Rounded.PlayArrow) {
                    previewing = !previewing
                    engine.previewMusic(previewing)
                }
                PillButton("", primary = false, icon = Icons.Rounded.Delete) {
                    previewing = false
                    engine.setMusic(null, null)
                    musicName = null
                }
            }
        }
        if (musicName != null) {
            Spacer(Modifier.height(8.dp))
            AdjustSlider("Volume", "${(volume * 100).toInt()}%", volume, 0f..1f) { v ->
                volume = v
                engine.setMusicVolume(v)
            }
        }
    }
}

// ---------------- Small parts ----------------

@Composable
private fun Segmented(options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White.copy(alpha = 0.05f))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        options.forEachIndexed { i, label ->
            val sel = selected == i
            Text(
                label,
                color = if (sel) Color.White else FColors.TextSecondary,
                fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Normal,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (sel) FColors.Blue else Color.Transparent)
                    .clickable { onSelect(i) }
                    .padding(vertical = 10.dp),
            )
        }
    }
}
