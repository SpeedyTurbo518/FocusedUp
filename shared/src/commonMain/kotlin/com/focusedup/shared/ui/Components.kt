package com.focusedup.shared.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/** Black-to-navy backdrop with soft blue light behind the glass layers. */
@Composable
fun AppBackground(content: @Composable BoxScope.() -> Unit) {
    Box(Modifier.fillMaxSize().background(FColors.Black)) {
        Canvas(Modifier.fillMaxSize()) {
            drawRect(Brush.verticalGradient(listOf(FColors.Night, FColors.Black, FColors.Black)))
            val c1 = Offset(size.width * 0.1f, size.height * 0.06f)
            val r1 = size.width * 0.95f
            drawCircle(Brush.radialGradient(listOf(FColors.Blue.copy(alpha = 0.32f), Color.Transparent), c1, r1), r1, c1)
            val c2 = Offset(size.width * 1.0f, size.height * 0.72f)
            val r2 = size.width * 0.85f
            drawCircle(Brush.radialGradient(listOf(FColors.BlueDeep.copy(alpha = 0.28f), Color.Transparent), c2, r2), r2, c2)
        }
        content()
    }
}

/** Frosted translucent card: light top edge, darker bottom, hairline border. */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    corner: Dp = 26.dp,
    padding: Dp = 22.dp,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(corner)
    Column(
        modifier
            .clip(shape)
            .background(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.09f * FColors.Glass), Color.White.copy(alpha = 0.035f * FColors.Glass))))
            .border(1.dp, Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.22f * FColors.Glass), Color.White.copy(alpha = 0.04f))), shape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(padding),
        content = content,
    )
}

@Composable
fun ScreenHeader(title: String, eyebrow: String) {
    Column(Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 28.dp)) {
        Text(eyebrow.uppercase(), color = FColors.TextTertiary, fontSize = 12.sp, letterSpacing = 1.6.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(4.dp))
        Text(title, color = FColors.Text, fontSize = 34.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp)
    }
}

@Composable
fun SectionLabel(text: String) {
    Text(
        text.uppercase(),
        color = FColors.TextTertiary,
        fontSize = 12.sp,
        letterSpacing = 1.4.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(start = 6.dp, top = 36.dp, bottom = 14.dp),
    )
}

@Composable
fun PillButton(
    text: String,
    modifier: Modifier = Modifier,
    primary: Boolean = true,
    icon: ImageVector? = null,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(50)
    Row(
        modifier
            .clip(shape)
            .background(if (primary) FColors.Blue else Color.White.copy(alpha = 0.08f))
            .then(if (!primary) Modifier.border(1.dp, Color.White.copy(alpha = 0.16f), shape) else Modifier)
            .clickable(onClick = onClick)
            .padding(horizontal = 22.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
            if (text.isNotEmpty()) Spacer(Modifier.width(8.dp))
        }
        if (text.isNotEmpty()) Text(text, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
    }
}

@Composable
fun FSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        colors = SwitchDefaults.colors(
            checkedThumbColor = Color.White,
            checkedTrackColor = FColors.Blue,
            checkedBorderColor = Color.Transparent,
            uncheckedThumbColor = Color.White.copy(alpha = 0.75f),
            uncheckedTrackColor = Color.White.copy(alpha = 0.12f),
            uncheckedBorderColor = Color.Transparent,
        ),
    )
}

@Composable
fun IconTile(icon: ImageVector, active: Boolean) {
    Box(
        Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (active) FColors.Blue.copy(alpha = 0.25f) else Color.White.copy(alpha = 0.06f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = if (active) FColors.BlueBright else FColors.TextSecondary, modifier = Modifier.size(20.dp))
    }
}

@Composable
fun Hairline(startPadding: Dp = 54.dp) {
    Box(Modifier.fillMaxWidth().padding(start = startPadding).height(1.dp).background(FColors.Hairline))
}

/** Row with an icon, title, detail and either a check (granted) or an "Allow" button. */
@Composable
fun PermRow(icon: ImageVector, title: String, detail: String, granted: Boolean, onGrant: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        IconTile(icon, granted)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Medium)
            Text(detail, color = FColors.TextSecondary, fontSize = 13.sp)
        }
        if (granted) {
            Icon(Icons.Rounded.CheckCircle, contentDescription = "Allowed", tint = FColors.BlueBright)
        } else {
            Text(
                "Allow",
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(FColors.Blue)
                    .clickable(onClick = onGrant)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
    }
}

@Composable
fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = Color.White,
    unfocusedTextColor = Color.White,
    focusedBorderColor = FColors.BlueBright,
    unfocusedBorderColor = Color.White.copy(alpha = 0.18f),
    focusedLabelColor = FColors.BlueBright,
    unfocusedLabelColor = FColors.TextSecondary,
    focusedPlaceholderColor = FColors.TextTertiary,
    unfocusedPlaceholderColor = FColors.TextTertiary,
    cursorColor = FColors.BlueBright,
    focusedContainerColor = Color.White.copy(alpha = 0.04f),
    unfocusedContainerColor = Color.White.copy(alpha = 0.03f),
)

enum class Tab(val label: String) { Focus("Focus"), Timer("Timer"), Calendar("Calendar"), Profile("Profile"), Setup("Setup") }

@Composable
fun GlassNavBar(current: Tab, icons: Map<Tab, ImageVector>, onSelect: (Tab) -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(34.dp)
    Row(
        modifier
            .clip(shape)
            .background(FColors.Night.copy(alpha = 0.92f))
            .border(1.dp, Color.White.copy(alpha = 0.12f), shape)
            .padding(6.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Tab.entries.forEach { tab ->
            val selected = tab == current
            val bg by animateColorAsState(if (selected) FColors.Blue else Color.Transparent, label = "tabBg")
            Row(
                Modifier
                    .clip(RoundedCornerShape(28.dp))
                    .background(bg)
                    .clickable { onSelect(tab) }
                    .padding(horizontal = if (selected) 14.dp else 11.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(icons.getValue(tab), contentDescription = tab.label, tint = if (selected) Color.White else FColors.TextSecondary, modifier = Modifier.size(22.dp))
                AnimatedVisibility(selected) {
                    Text(tab.label, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 8.dp))
                }
            }
        }
    }
}

@Composable
fun rememberNow(now: () -> Long, intervalMs: Long = 250L): State<Long> {
    val state = remember { mutableLongStateOf(now()) }
    LaunchedEffect(intervalMs) {
        while (true) {
            state.longValue = now()
            delay(intervalMs)
        }
    }
    return state
}

@Composable
fun Dot(color: Color, size: Dp = 4.dp) {
    Box(Modifier.size(size).clip(CircleShape).background(color))
}
