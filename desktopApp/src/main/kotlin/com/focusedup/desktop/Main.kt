package com.focusedup.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.window.WindowDraggableArea
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.FullscreenExit
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Notification
import androidx.compose.ui.window.Tray
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.WindowScope
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberTrayState
import androidx.compose.ui.window.rememberWindowState
import com.focusedup.shared.engine.FocusEngine
import com.focusedup.shared.model.Phase
import com.focusedup.shared.ui.FColors
import com.focusedup.shared.ui.FocusLogoPainter
import com.focusedup.shared.ui.FocusedUpApp
import java.awt.GraphicsEnvironment

fun main() {
    val platform = DesktopPlatform()
    val engine = FocusEngine(platform)

    // If the app closed unexpectedly while apps were muted, unmute them now.
    if (engine.state.value.phase != Phase.ACTIVE && platform.hasLeftoverMutes) platform.restoreNow()
    // Safety net: never leave other apps muted when Windows shuts the app down.
    Runtime.getRuntime().addShutdownHook(Thread { platform.restoreNow() })

    application {
        val trayState = rememberTrayState()
        LaunchedEffect(Unit) { platform.tray = trayState }
        // Window, taskbar and tray icon follow Setup → App icon.
        val appearance by engine.appearance.value.collectAsState()
        val logo = remember(appearance.iconColor, appearance.iconStyle) {
            FocusLogoPainter(appearance.iconColor, appearance.iconStyle)
        }
        var visible by remember { mutableStateOf(true) }
        var fullscreen by platform.fullscreen

        val quit = {
            engine.shutdown()
            platform.stopMusic()
            platform.restoreNow()
            exitApplication()
        }
        val close = {
            if (engine.state.value.needsService || engine.prayer.value.running) {
                visible = false
                trayState.sendNotification(
                    Notification("Focused Up is still running", "Your session continues. Open it from the tray icon.")
                )
            } else {
                quit()
            }
        }

        Tray(
            icon = logo,
            state = trayState,
            tooltip = "Focused Up",
            onAction = { visible = true },
            menu = {
                Item("Open Focused Up", onClick = { visible = true })
                Item(if (fullscreen) "Exit full screen" else "Full screen", onClick = { visible = true; fullscreen = !fullscreen })
                Separator()
                Item("Quit", onClick = quit)
            },
        )

        val screenHeight = runCatching {
            GraphicsEnvironment.getLocalGraphicsEnvironment().maximumWindowBounds.height
        }.getOrDefault(900)
        val floatingSize = DpSize(440.dp, minOf(880, screenHeight - 40).dp)
        val windowState = rememberWindowState(size = floatingSize, position = WindowPosition(Alignment.Center))
        var savedPosition by remember { mutableStateOf<WindowPosition>(WindowPosition(Alignment.Center)) }
        var savedSize by remember { mutableStateOf(floatingSize) }

        Window(
            onCloseRequest = close,
            visible = visible,
            state = windowState,
            title = "Focused Up",
            icon = logo,
            undecorated = true,
            transparent = true,
            resizable = false,
            onKeyEvent = { e ->
                when {
                    e.type != KeyEventType.KeyDown -> false
                    e.key == Key.F11 -> { fullscreen = !fullscreen; true }
                    e.key == Key.Escape && fullscreen -> { fullscreen = false; true }
                    else -> false
                }
            },
        ) {
            // Borderless full screen: cover the whole monitor the window is on.
            LaunchedEffect(fullscreen) {
                if (fullscreen) {
                    savedPosition = windowState.position
                    savedSize = windowState.size
                    val b = window.graphicsConfiguration.bounds
                    windowState.position = WindowPosition(b.x.dp, b.y.dp)
                    windowState.size = DpSize(b.width.dp, b.height.dp)
                    window.toFront()
                } else if (windowState.size != savedSize) {
                    windowState.size = savedSize
                    windowState.position = savedPosition
                }
            }

            val shape = if (fullscreen) RectangleShape else RoundedCornerShape(18.dp)
            Box(
                Modifier
                    .fillMaxSize()
                    .clip(shape)
                    .background(FColors.Black)
                    .border(if (fullscreen) 0.dp else 1.dp, Color.White.copy(alpha = if (fullscreen) 0f else 0.10f), shape)
            ) {
                Column(Modifier.fillMaxSize()) {
                    TitleBar(
                        fullscreen = fullscreen,
                        onMinimize = { windowState.isMinimized = true },
                        onToggleFullscreen = { fullscreen = !fullscreen },
                        onClose = close,
                    )
                    Box(Modifier.fillMaxWidth().weight(1f)) {
                        FocusedUpApp(engine)
                    }
                }
            }
        }
    }
}

/** Minimal dark title bar: drag to move, full screen, minimize, close. */
@Composable
private fun WindowScope.TitleBar(fullscreen: Boolean, onMinimize: () -> Unit, onToggleFullscreen: () -> Unit, onClose: () -> Unit) {
    val content: @Composable RowScope.() -> Unit = {
        Text("Focused Up", color = FColors.TextSecondary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.weight(1f))
        TitleButton(Icons.Rounded.Remove, "Minimize", onMinimize)
        Spacer(Modifier.width(6.dp))
        TitleButton(
            if (fullscreen) Icons.Rounded.FullscreenExit else Icons.Rounded.Fullscreen,
            if (fullscreen) "Exit full screen (Esc)" else "Full screen (F11)",
            onToggleFullscreen,
        )
        Spacer(Modifier.width(6.dp))
        TitleButton(Icons.Rounded.Close, "Close", onClose)
    }
    val bar = Modifier.fillMaxWidth().height(40.dp).background(FColors.Night).padding(horizontal = 14.dp)
    if (fullscreen) {
        Row(bar, verticalAlignment = Alignment.CenterVertically, content = content)
    } else {
        WindowDraggableArea {
            Row(bar, verticalAlignment = Alignment.CenterVertically, content = content)
        }
    }
}

@Composable
private fun TitleButton(icon: ImageVector, label: String, onClick: () -> Unit) {
    Box(
        Modifier.size(26.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.08f)).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = label, tint = Color.White.copy(alpha = 0.8f), modifier = Modifier.size(16.dp))
    }
}
