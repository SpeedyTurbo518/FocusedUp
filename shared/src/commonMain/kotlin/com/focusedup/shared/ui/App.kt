package com.focusedup.shared.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Storefront
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.focusedup.shared.engine.FocusEngine
import com.focusedup.shared.ui.screens.CalendarScreen
import com.focusedup.shared.ui.screens.FocusScreen
import com.focusedup.shared.ui.screens.IncomingProfileDialog
import com.focusedup.shared.ui.screens.OnTaskScreen
import com.focusedup.shared.ui.screens.PrayerScreen
import com.focusedup.shared.ui.screens.ProfileScreen
import com.focusedup.shared.ui.screens.SetupScreen
import com.focusedup.shared.ui.screens.TimerScreen
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val TAB_ICONS = mapOf(
    Tab.Focus to Icons.Rounded.PowerSettingsNew,
    Tab.Timer to Icons.Rounded.Timer,
    Tab.Calendar to Icons.Rounded.CalendarMonth,
    Tab.OnTask to Icons.Rounded.Storefront,
    Tab.Profile to Icons.Rounded.Person,
    Tab.Setup to Icons.Rounded.Tune,
)

/** Root of the app on every platform: splash → prayer (once a day) → tabs. */
@Composable
fun FocusedUpApp(engine: FocusEngine) {
    CompositionLocalProvider(LocalEngine provides engine) {
        FocusedUpTheme {
            var showSplash by rememberSaveable { mutableStateOf(true) }
            var showPrayer by rememberSaveable { mutableStateOf(engine.prayerDue()) }
            var autoStarted by rememberSaveable { mutableStateOf(false) }
            // Once the splash has faded, the prayer timer and its song start by themselves.
            LaunchedEffect(showSplash) {
                if (!showSplash && showPrayer && !autoStarted) {
                    autoStarted = true
                    delay(450)
                    engine.autoStartPrayer()
                }
            }
            // After a snooze runs out (or a new day starts) the prayer page comes back.
            LaunchedEffect(Unit) {
                while (true) {
                    delay(30_000)
                    if (!showPrayer && engine.prayerDue()) showPrayer = true
                }
            }
            Box(Modifier.fillMaxSize()) {
                MainContent(onOpenPrayer = { showPrayer = true })
                IncomingProfileDialog()
                AnimatedVisibility(
                    visible = showPrayer,
                    enter = fadeIn(tween(350)),
                    exit = fadeOut(tween(350)) + slideOutVertically(tween(400)) { -it / 6 },
                ) {
                    PrayerScreen(onClose = { showPrayer = false })
                }
                AnimatedVisibility(
                    visible = showSplash,
                    enter = EnterTransition.None,
                    exit = fadeOut(tween(400)),
                ) {
                    SplashScreen(onDone = { showSplash = false })
                }
            }
        }
    }
}

@Composable
private fun MainContent(onOpenPrayer: () -> Unit) {
    val engine = LocalEngine.current
    // Opens on the tab you used last, even after the app was closed.
    var tab by remember { mutableStateOf(Tab.entries.firstOrNull { it.name == engine.settings.ui("tab") } ?: Tab.Focus) }
    LaunchedEffect(tab) { engine.settings.setUi("tab", tab.name) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val toast: (String) -> Unit = { msg ->
        scope.launch {
            snackbar.currentSnackbarData?.dismiss()
            snackbar.showSnackbar(msg)
        }
    }

    AppBackground {
        Box(Modifier.fillMaxSize().systemBarsPadding(), contentAlignment = Alignment.TopCenter) {
            // Phone-width column, centered on wide desktop windows.
            Box(Modifier.widthIn(max = 560.dp).fillMaxWidth().fillMaxHeight()) {
                Crossfade(targetState = tab, animationSpec = tween(220), label = "tabs") { current ->
                    when (current) {
                        Tab.Focus -> FocusScreen(onToast = toast, onOpenSetup = { tab = Tab.Setup }, onOpenPrayer = onOpenPrayer)
                        Tab.Timer -> TimerScreen(onToast = toast)
                        Tab.Calendar -> CalendarScreen(onToast = toast)
                        Tab.OnTask -> OnTaskScreen(onToast = toast)
                        Tab.Profile -> ProfileScreen(onOpenPrayer = onOpenPrayer, onToast = toast)
                        Tab.Setup -> SetupScreen(onToast = toast)
                    }
                }
                SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(start = 16.dp, end = 16.dp, bottom = 96.dp)) {
                    Snackbar(it, containerColor = FColors.Dialog.copy(alpha = 0.96f), contentColor = Color.White, shape = RoundedCornerShape(18.dp))
                }
                GlassNavBar(
                    current = tab,
                    icons = TAB_ICONS,
                    onSelect = { tab = it },
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp),
                )
            }
        }
    }
}
