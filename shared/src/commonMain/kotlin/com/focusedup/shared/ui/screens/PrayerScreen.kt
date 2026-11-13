package com.focusedup.shared.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Snooze
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.focusedup.shared.model.PRAYER_MS
import com.focusedup.shared.platform.MediaKind
import com.focusedup.shared.ui.AppBackground
import com.focusedup.shared.ui.FColors
import com.focusedup.shared.ui.GlassCard
import com.focusedup.shared.ui.IconTile
import com.focusedup.shared.ui.LocalEngine
import com.focusedup.shared.ui.PillButton
import com.focusedup.shared.util.cleanSongName
import kotlin.math.cos
import kotlin.math.sin

/** Short public-domain (KJV) verses; one is picked per day. */
private val VERSES = listOf(
    "Commit thy works unto the LORD, and thy thoughts shall be established." to "Proverbs 16:3",
    "This is the day which the LORD hath made; we will rejoice and be glad in it." to "Psalm 118:24",
    "I can do all things through Christ which strengtheneth me." to "Philippians 4:13",
    "Whatsoever ye do, do it heartily, as to the Lord, and not unto men." to "Colossians 3:23",
    "Be still, and know that I am God." to "Psalm 46:10",
    "Trust in the LORD with all thine heart; and lean not unto thine own understanding." to "Proverbs 3:5",
    "They that wait upon the LORD shall renew their strength." to "Isaiah 40:31",
)

/** The first page after the splash: 15 minutes of prayer before work. */
@Composable
fun PrayerScreen(onClose: () -> Unit) {
    val engine = LocalEngine.current
    val platform = engine.platform
    val p by engine.prayer.collectAsState()
    val doneToday = p.doneOn == engine.tasks.today()
    var confirmSnooze by remember { mutableStateOf(false) }
    var musicAsked by remember { mutableStateOf(engine.settings.musicSetupDone) }
    var musicName by remember { mutableStateOf(engine.settings.musicName) }
    val pickMusic = platform.rememberPicker(MediaKind.Audio) { file ->
        engine.setMusic(file.path, file.name)
        musicName = file.name
        musicAsked = true
    }

    // Frame-by-frame clock while running, so the ring and digits move smoothly.
    var nowMs by remember { mutableLongStateOf(platform.now()) }
    LaunchedEffect(p.running) {
        nowMs = platform.now()
        while (p.running) {
            withFrameMillis { nowMs = platform.now() }
            if (nowMs >= p.endsAt) engine.tick()
        }
    }
    val remaining = when {
        doneToday && !p.running -> 0L
        p.running -> (p.endsAt - nowMs).coerceIn(0L, PRAYER_MS)
        else -> p.remaining
    }
    val progress = 1f - remaining.toFloat() / PRAYER_MS
    val verse = VERSES[(engine.tasks.today().toEpochDays() % VERSES.size).let { if (it < 0) it + VERSES.size else it }]

    AppBackground {
        Box(Modifier.fillMaxSize().systemBarsPadding(), contentAlignment = Alignment.TopCenter) {
            Column(
                Modifier
                    .widthIn(max = 560.dp)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("BEFORE YOU GET TO WORK", color = FColors.TextTertiary, fontSize = 12.sp, letterSpacing = 1.8.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(6.dp))
                Text(
                    if (doneToday) "Amen." else "Start with prayer",
                    color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp,
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    "“${verse.first}”",
                    color = FColors.TextSecondary, fontSize = 15.sp, fontStyle = FontStyle.Italic,
                    textAlign = TextAlign.Center, lineHeight = 21.sp,
                )
                Text(verse.second, color = FColors.BlueBright, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))

                Spacer(Modifier.height(18.dp))
                Box(Modifier.size(300.dp), contentAlignment = Alignment.Center) {
                    PrayerRing(progress, running = p.running, finished = doneToday && !p.running)
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            when {
                                doneToday && !p.running -> "COMPLETE"
                                p.running -> "PRAYING"
                                remaining < PRAYER_MS -> "PAUSED"
                                else -> "15 MINUTES"
                            },
                            color = FColors.TextTertiary, fontSize = 12.sp, letterSpacing = 2.sp, fontWeight = FontWeight.SemiBold,
                        )
                        Spacer(Modifier.height(4.dp))
                        RollingClock(remaining)
                    }
                }

                Spacer(Modifier.height(18.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally), modifier = Modifier.fillMaxWidth()) {
                    when {
                        doneToday && !p.running ->
                            PillButton("Start working", icon = Icons.Rounded.Check, onClick = onClose)
                        p.running -> {
                            PillButton("Pause", icon = Icons.Rounded.Pause) { engine.pausePrayer() }
                            PillButton("Cancel", primary = false, icon = Icons.Rounded.Close) { engine.resetPrayer() }
                        }
                        remaining < PRAYER_MS -> {
                            PillButton("Resume", icon = Icons.Rounded.PlayArrow) { engine.startPrayer() }
                            PillButton("Cancel", primary = false, icon = Icons.Rounded.Close) { engine.resetPrayer() }
                        }
                        else -> PillButton("Begin prayer", icon = Icons.Rounded.PlayArrow) { engine.startPrayer() }
                    }
                }

                Spacer(Modifier.height(22.dp))
                if (!musicAsked) {
                    GlassCard(Modifier.fillMaxWidth(), padding = 16.dp) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconTile(Icons.Rounded.LibraryMusic, active = true)
                            Spacer(Modifier.width(14.dp))
                            Column(Modifier.weight(1f)) {
                                Text("Add calming music", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                                Text("Pick a soft song from your device. It plays while you pray. Change it any time in Setup.", color = FColors.TextSecondary, fontSize = 13.sp)
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            PillButton("Choose music", icon = Icons.Rounded.MusicNote, onClick = pickMusic)
                            PillButton("Not now", primary = false) {
                                engine.settings.musicSetupDone = true
                                musicAsked = true
                            }
                        }
                    }
                } else if (musicName != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.MusicNote, contentDescription = null, tint = FColors.BlueBright, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(cleanSongName(musicName ?: ""), color = FColors.TextSecondary, fontSize = 13.sp, maxLines = 1)
                    }
                }

                Spacer(Modifier.height(18.dp))
                if (doneToday && !p.running) {
                    Text("Streak: ${p.streak} day${if (p.streak == 1) "" else "s"}", color = FColors.TextTertiary, fontSize = 13.sp)
                } else {
                    Row(
                        Modifier
                            .clickable { if (doneToday) onClose() else confirmSnooze = true }
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Rounded.Snooze, contentDescription = null, tint = FColors.TextTertiary, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(if (doneToday) "Back to work" else "Snooze", color = FColors.TextTertiary, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }
        }
    }

    if (confirmSnooze) {
        AlertDialog(
            onDismissRequest = { confirmSnooze = false },
            containerColor = FColors.Dialog,
            shape = RoundedCornerShape(26.dp),
            icon = { Icon(Icons.Rounded.Snooze, contentDescription = null, tint = FColors.BlueBright) },
            title = { Text("Hold on", color = Color.White) },
            text = {
                Text(
                    "You should really pray before working, Are you sure you want to continue?",
                    color = FColors.TextSecondary, fontSize = 15.sp, lineHeight = 21.sp,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmSnooze = false
                    if (!p.running) engine.startPrayer()
                }) { Text("Pray now", color = FColors.BlueBright, fontWeight = FontWeight.SemiBold) }
            },
            dismissButton = {
                TextButton(onClick = {
                    confirmSnooze = false
                    engine.snoozePrayer()
                    onClose()
                }) { Text("Yes, continue", color = FColors.TextSecondary) }
            },
        )
    }
}

/** MM:SS where each digit rolls down into place as it changes. */
@Composable
private fun RollingClock(remainingMs: Long) {
    val totalSec = ((remainingMs + 999) / 1000).toInt()
    val text = "${(totalSec / 60).toString().padStart(2, '0')}:${(totalSec % 60).toString().padStart(2, '0')}"
    Row(verticalAlignment = Alignment.CenterVertically) {
        text.forEachIndexed { i, ch ->
            if (ch == ':') {
                Text(":", color = Color.White.copy(alpha = 0.7f), fontSize = 58.sp, fontWeight = FontWeight.Light, modifier = Modifier.padding(horizontal = 2.dp))
            } else {
                Box(Modifier.width(38.dp), contentAlignment = Alignment.Center) {
                    AnimatedContent(
                        targetState = ch,
                        transitionSpec = {
                            (slideInVertically(tween(380, easing = FastOutSlowInEasing)) { -it / 2 } + fadeIn(tween(380))) togetherWith
                                (slideOutVertically(tween(380, easing = FastOutSlowInEasing)) { it / 2 } + fadeOut(tween(300)))
                        },
                        label = "digit$i",
                    ) { d ->
                        Text(d.toString(), color = Color.White, fontSize = 58.sp, fontWeight = FontWeight.Light)
                    }
                }
            }
        }
    }
}

/** Ring that fills smoothly, with a softly breathing glow and a bright comet at the tip. */
@Composable
private fun PrayerRing(progress: Float, running: Boolean, finished: Boolean) {
    val breathe = rememberInfiniteTransition(label = "breathe")
    val pulse by breathe.animateFloat(
        initialValue = 0.55f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(4000, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "pulse",
    )
    val accent = FColors.BlueBright
    val glowColor = FColors.Blue
    Canvas(Modifier.fillMaxSize()) {
        val r = size.minDimension / 2f
        val glow = if (running || finished) pulse else 0.4f
        drawCircle(Brush.radialGradient(listOf(glowColor.copy(alpha = 0.38f * glow), Color.Transparent), center, r), r)
        val stroke = 7.dp.toPx()
        val ringR = r * 0.80f
        drawCircle(Color.White.copy(alpha = 0.08f), ringR, style = Stroke(stroke))
        val sweep = 360f * progress.coerceIn(0f, 1f)
        if (sweep > 0f) {
            drawArc(
                brush = Brush.sweepGradient(listOf(glowColor, accent, glowColor), center),
                startAngle = -90f,
                sweepAngle = sweep,
                useCenter = false,
                topLeft = Offset(center.x - ringR, center.y - ringR),
                size = Size(ringR * 2, ringR * 2),
                style = Stroke(stroke, cap = StrokeCap.Round),
            )
            val a = ((-90f + sweep) * kotlin.math.PI / 180f).toFloat()
            val tip = Offset(center.x + ringR * cos(a), center.y + ringR * sin(a))
            drawCircle(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.9f), Color.Transparent), tip, stroke * 2.2f), stroke * 2.2f, tip)
        }
    }
}
