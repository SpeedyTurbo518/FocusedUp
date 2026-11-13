package com.focusedup.shared.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AddAPhoto
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.IosShare
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.focusedup.shared.data.ProfileStore
import com.focusedup.shared.data.SharedProfile
import com.focusedup.shared.model.Profile
import com.focusedup.shared.platform.MediaKind
import com.focusedup.shared.ui.ACCENTS
import com.focusedup.shared.ui.FColors
import com.focusedup.shared.ui.GlassCard
import com.focusedup.shared.ui.Hairline
import com.focusedup.shared.ui.LocalEngine
import com.focusedup.shared.ui.PillButton
import com.focusedup.shared.ui.ScreenHeader
import com.focusedup.shared.ui.SectionLabel
import com.focusedup.shared.ui.fieldColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Loads a picked photo off the main thread. */
@Composable
fun rememberImage(path: String?): ImageBitmap? {
    val platform = LocalEngine.current.platform
    val img by produceState<ImageBitmap?>(null, path) {
        value = if (path == null) null else withContext(Dispatchers.Default) { runCatching { platform.loadImage(path) }.getOrNull() }
    }
    return img
}

@Composable
fun ProfileScreen(onOpenPrayer: () -> Unit, onToast: (String) -> Unit) {
    val engine = LocalEngine.current
    val platform = engine.platform
    val saved by engine.profile.value.collectAsState()
    val events by engine.calendar.events.collectAsState()
    val tasks by engine.tasks.tasks.collectAsState()
    val prayer by engine.prayer.collectAsState()
    val friends by engine.friends.list.collectAsState()

    // While editing, every change goes into a draft; one Save button commits it.
    var draft by remember { mutableStateOf<Profile?>(null) }
    val editing = draft != null
    val p = draft ?: saved
    val ring by animateColorAsState(ACCENTS.getOrElse(p.color) { ACCENTS[0] }.color, label = "profileColor")

    val pickCover = platform.rememberPicker(MediaKind.Image) { f -> draft = draft?.copy(cover = f.path) }
    val pickPhoto = platform.rememberPicker(MediaKind.Image) { f ->
        draft = draft?.let { d ->
            val photos = (d.photos + f.path).takeLast(ProfileStore.MAX_PHOTOS)
            d.copy(photos = photos, photoIndex = photos.lastIndex)
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(bottom = 120.dp),
    ) {
        ScreenHeader("Profile", if (editing) "Editing" else "You")

        // ---- Cover + avatar ----
        Box(Modifier.fillMaxWidth().height(240.dp)) {
            val cover = rememberImage(p.cover)
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .clip(RoundedCornerShape(28.dp))
                    .background(Brush.linearGradient(listOf(ring, lerp(ring, Color.Black, 0.7f), FColors.Black)))
                    .then(if (editing) Modifier.clickable(onClick = pickCover) else Modifier),
            ) {
                if (cover != null) {
                    Image(cover, contentDescription = "Cover photo", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.4f)))))
                }
                if (editing) {
                    if (p.cover == null) {
                        // No cover yet: a clear "add" prompt. It disappears once a cover is chosen.
                        Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Rounded.AddAPhoto, null, tint = Color.White, modifier = Modifier.size(28.dp))
                            Spacer(Modifier.height(6.dp))
                            Text("Add cover photo", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                        }
                    } else {
                        EditBadge(Icons.Rounded.PhotoCamera, Modifier.align(Alignment.TopEnd).padding(14.dp))
                    }
                }
            }
            Avatar(
                path = p.photo,
                name = p.name,
                ring = ring,
                size = 116.dp,
                editing = editing,
                modifier = Modifier.align(Alignment.BottomStart).offset(x = 20.dp),
                onClick = if (editing) pickPhoto else null,
            )
        }

        Spacer(Modifier.height(16.dp))
        if (!editing) {
            // ---- View mode: clean, no edit controls ----
            Text(p.name.ifBlank { "Your name" }, color = if (p.name.isBlank()) FColors.TextTertiary else Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
            if (p.tagline.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(p.tagline, color = FColors.TextSecondary, fontSize = 15.sp)
            }
            Spacer(Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PillButton("Edit profile", primary = false, icon = Icons.Rounded.Edit, modifier = Modifier.weight(1f)) { draft = saved }
                PillButton("Share", icon = Icons.Rounded.IosShare, modifier = Modifier.weight(1f)) {
                    val link = engine.myShareLink()
                    val msg = platform.shareText("Here's my Focused Up profile. Open it in the app (you'll be asked before anything downloads):\n$link")
                    msg?.let(onToast)
                }
            }

            SectionLabel("Your progress")
            val focusMin = events.sumOf { it.focusMinutes }
            GlassCard(Modifier.fillMaxWidth(), padding = 22.dp) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Stat("${focusMin / 60}h", "Focused", ring)
                    Stat(events.count { it.isFocus }.toString(), "Sessions", ring)
                    Stat(tasks.count { it.done }.toString(), "Tasks", ring)
                    Stat(prayer.streak.toString(), "Prayer days", ring, onClick = onOpenPrayer)
                }
            }

            SectionLabel("Friends")
            GlassCard(Modifier.fillMaxWidth(), padding = if (friends.isEmpty()) 20.dp else 8.dp) {
                if (friends.isEmpty()) {
                    Text("Profiles people share with you show up here.", color = FColors.TextSecondary, fontSize = 14.sp)
                } else {
                    friends.forEachIndexed { i, f ->
                        if (i > 0) Hairline(startPadding = 72.dp)
                        FriendRow(f) { engine.friends.remove(f) }
                    }
                }
                if (platform.pastesLinks) {
                    // Desktop: links can't open the app directly, so offer to paste one.
                    Text(
                        "Paste a shared profile link",
                        color = FColors.BlueBright, fontSize = 14.sp,
                        modifier = Modifier.clickable {
                            val clip = platform.readClipboard().orEmpty()
                            if (!engine.receiveLink(clip)) onToast("Copy a Focused Up profile link first")
                        }.padding(start = if (friends.isEmpty()) 0.dp else 12.dp, top = 14.dp, bottom = 6.dp),
                    )
                }
            }
        } else {
            // ---- Edit mode ----
            val d = draft!!
            OutlinedTextField(
                value = d.name, onValueChange = { draft = d.copy(name = it.take(40)) }, label = { Text("Name") },
                singleLine = true, colors = fieldColors(), shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(14.dp))
            OutlinedTextField(
                value = d.tagline, onValueChange = { draft = d.copy(tagline = it.take(80)) }, label = { Text("Tagline or favorite verse") },
                singleLine = true, colors = fieldColors(), shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth(),
            )

            SectionLabel("Profile color")
            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                ACCENTS.forEachIndexed { i, c -> Swatch(c.color, selected = i == d.color) { draft = d.copy(color = i) } }
            }

            SectionLabel("Profile photos")
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                d.photos.forEachIndexed { i, path ->
                    PhotoThumb(
                        path = path,
                        selected = i == d.photoIndex,
                        ring = ring,
                        onSelect = { draft = d.copy(photoIndex = i) },
                        onRemove = {
                            val photos = d.photos.filterIndexed { j, _ -> j != i }
                            val idx = when {
                                i < d.photoIndex -> d.photoIndex - 1
                                i == d.photoIndex -> 0
                                else -> d.photoIndex
                            }
                            draft = d.copy(photos = photos, photoIndex = idx)
                        },
                    )
                }
                if (d.photos.size < ProfileStore.MAX_PHOTOS) {
                    Box(
                        Modifier
                            .size(76.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .border(1.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(20.dp))
                            .clickable(onClick = pickPhoto),
                        contentAlignment = Alignment.Center,
                    ) { Icon(Icons.Rounded.Add, contentDescription = "Add photo", tint = FColors.TextSecondary) }
                }
            }
            if (d.cover != null) {
                Text(
                    "Remove cover photo", color = FColors.TextTertiary, fontSize = 13.sp,
                    modifier = Modifier.clickable { draft = d.copy(cover = null) }.padding(top = 18.dp, bottom = 4.dp),
                )
            }

            Spacer(Modifier.height(32.dp))
            PillButton("Save", icon = Icons.Rounded.Check, modifier = Modifier.fillMaxWidth()) {
                engine.profile.commit(d.copy(name = d.name.trim(), tagline = d.tagline.trim()))
                draft = null
                onToast("Profile saved")
            }
            Text(
                "Cancel", color = FColors.TextSecondary, fontSize = 15.sp, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().clickable { engine.profile.discard(d); draft = null }.padding(16.dp),
            )
        }
    }
}

/** Shown at the app root when the app is opened from someone's profile link. */
@Composable
fun IncomingProfileDialog() {
    val engine = LocalEngine.current
    val incoming by engine.incomingProfile.collectAsState()
    val f = incoming ?: return
    val color = ACCENTS.getOrElse(f.c) { ACCENTS[0] }.color
    AlertDialog(
        onDismissRequest = { engine.answerIncoming(false) },
        containerColor = FColors.Dialog,
        shape = RoundedCornerShape(28.dp),
        icon = { Initials(f.n, color, 64.dp) },
        title = { Text(f.n, color = Color.White, textAlign = TextAlign.Center) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                if (f.t.isNotBlank()) Text(f.t, color = FColors.TextSecondary, fontSize = 15.sp, textAlign = TextAlign.Center)
                Spacer(Modifier.height(12.dp))
                Text("${f.m / 60}h focused · ${f.s} prayer days", color = FColors.TextTertiary, fontSize = 13.sp)
            }
        },
        confirmButton = {
            TextButton(onClick = { engine.answerIncoming(true) }) { Text("Save to friends", color = FColors.BlueBright, fontWeight = FontWeight.SemiBold) }
        },
        dismissButton = { TextButton(onClick = { engine.answerIncoming(false) }) { Text("Not now", color = FColors.TextSecondary) } },
    )
}

@Composable
private fun FriendRow(f: SharedProfile, onRemove: () -> Unit) {
    val color = ACCENTS.getOrElse(f.c) { ACCENTS[0] }.color
    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Initials(f.n, color, 44.dp)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(f.n, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Medium)
            Text(f.t.ifBlank { "${f.m / 60}h focused · ${f.s} prayer days" }, color = FColors.TextSecondary, fontSize = 13.sp, maxLines = 1)
        }
        Icon(Icons.Rounded.Close, "Remove", tint = FColors.TextTertiary, modifier = Modifier.size(20.dp).clickable(onClick = onRemove))
    }
}

@Composable
private fun Initials(name: String, color: Color, size: Dp) {
    val initials = name.split(" ").filter { it.isNotBlank() }.take(2).joinToString("") { it.first().uppercase() }
    Box(
        Modifier.size(size).clip(CircleShape).background(Brush.linearGradient(listOf(color, lerp(color, Color.Black, 0.6f)))),
        contentAlignment = Alignment.Center,
    ) { Text(initials, color = Color.White, fontSize = (size.value * 0.38f).sp, fontWeight = FontWeight.Bold) }
}

@Composable
private fun EditBadge(icon: ImageVector, modifier: Modifier) {
    Box(
        modifier.size(34.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.55f)),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, contentDescription = "Change", tint = Color.White, modifier = Modifier.size(18.dp)) }
}

@Composable
private fun Avatar(path: String?, name: String, ring: Color, size: Dp, editing: Boolean, modifier: Modifier, onClick: (() -> Unit)?) {
    val img = rememberImage(path)
    Box(modifier.size(size)) {
        Box(
            Modifier
                .fillMaxSize()
                .clip(CircleShape)
                .background(FColors.Black)
                .border(4.dp, ring, CircleShape)
                .padding(6.dp)
                .clip(CircleShape)
                .background(Brush.linearGradient(listOf(ring, lerp(ring, Color.Black, 0.6f))))
                .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
            contentAlignment = Alignment.Center,
        ) {
            if (img != null) {
                Image(img, contentDescription = "Profile photo", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            } else if (editing) {
                // Only prompts to add a photo until one is chosen.
                Icon(Icons.Rounded.AddAPhoto, contentDescription = "Add profile photo", tint = Color.White, modifier = Modifier.size(32.dp))
            } else {
                val initials = name.split(" ").filter { it.isNotBlank() }.take(2).joinToString("") { it.first().uppercase() }
                Text(initials.ifEmpty { "?" }, color = Color.White, fontSize = 38.sp, fontWeight = FontWeight.Bold)
            }
        }
        if (editing && img != null) EditBadge(Icons.Rounded.PhotoCamera, Modifier.align(Alignment.BottomEnd))
    }
}

@Composable
private fun PhotoThumb(path: String, selected: Boolean, ring: Color, onSelect: () -> Unit, onRemove: () -> Unit) {
    val img = rememberImage(path)
    Box(Modifier.size(76.dp)) {
        Box(
            Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(20.dp))
                .background(Color.White.copy(alpha = 0.06f))
                .border(if (selected) 3.dp else 1.dp, if (selected) ring else Color.White.copy(alpha = 0.18f), RoundedCornerShape(20.dp))
                .clickable(onClick = onSelect),
        ) {
            if (img != null) Image(img, contentDescription = "Photo", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .offset(x = 6.dp, y = (-6).dp)
                .size(24.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.8f))
                .clickable(onClick = onRemove),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Rounded.Close, contentDescription = "Remove photo", tint = Color.White, modifier = Modifier.size(14.dp)) }
    }
}

@Composable
private fun Stat(value: String, label: String, color: Color, onClick: (() -> Unit)? = null) {
    Column(
        Modifier.then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier).padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(value, color = lerp(color, Color.White, 0.35f), fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(2.dp))
        Text(label, color = FColors.TextTertiary, fontSize = 12.sp)
    }
}

@Composable
fun Swatch(color: Color, selected: Boolean, size: Dp = 36.dp, onClick: () -> Unit) {
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .background(color)
            .border(if (selected) 3.dp else 1.dp, if (selected) Color.White else Color.White.copy(alpha = 0.2f), CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) Icon(Icons.Rounded.Check, contentDescription = "Selected", tint = Color.White, modifier = Modifier.size(size * 0.5f))
    }
}
