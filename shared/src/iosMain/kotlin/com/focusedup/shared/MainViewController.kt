package com.focusedup.shared

import androidx.compose.runtime.remember
import androidx.compose.ui.window.ComposeUIViewController
import com.focusedup.shared.engine.FocusEngine
import com.focusedup.shared.ui.FocusedUpApp
import platform.UIKit.UIViewController

/** Called from Swift (iosApp/FocusedUp/iOSApp.swift) as MainViewControllerKt.MainViewController(). */
fun MainViewController(): UIViewController = ComposeUIViewController {
    val engine = remember { FocusEngine(IosPlatform()) }
    FocusedUpApp(engine)
}
